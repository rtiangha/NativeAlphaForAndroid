package com.cylonid.nativealpha.helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.cylonid.nativealpha.model.AdblockConfig
import io.github.edsuns.adfilter.AdFilter
import io.github.edsuns.adfilter.DownloadState
import io.github.edsuns.adfilter.Filter

/**
 * Manually downloads all configured filter lists and reports progress. Must be used on the main thread.
 *
 * The library downloads a list with two chained WorkManager jobs (download, then install) and mirrors their state in
 * [Filter.downloadState]; that state is what is reported here. Only one process may download the lists at a time, so
 * this shares the lock used by the automatic sync.
 */
class AdblockListDownloader(context: Context) {

    enum class State { RUNNING, FINISHED, FINISHED_WITH_ERRORS, TIMED_OUT, BUSY_ELSEWHERE, UNAVAILABLE, NOTHING_CONFIGURED }

    enum class ItemState { WAITING, DOWNLOADING, INSTALLING, DONE, FAILED, NOT_STARTED }

    data class ListStatus(
        val label: String,
        val itemState: ItemState,
        val updateTime: Long = 0L,
        val rules: Int = 0
    ) {
        val done: Boolean get() = itemState == ItemState.DONE
        val finished: Boolean
            get() = itemState == ItemState.DONE || itemState == ItemState.FAILED || itemState == ItemState.NOT_STARTED
    }

    data class Progress(
        val state: State,
        val lists: List<ListStatus> = emptyList(),
        val elapsedSeconds: Int = 0
    ) {
        val total: Int get() = lists.size
        val done: Int get() = lists.count { it.done }
        val failed: Int get() = lists.count { it.itemState == ItemState.FAILED || it.itemState == ItemState.NOT_STARTED }
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private var stopObserving: (() -> Unit)? = null
    private var callback: ((Progress) -> Unit)? = null
    private var configs: List<AdblockConfig> = emptyList()
    private var startedAt = 0L
    private var lastMap: Map<String, Filter> = emptyMap()
    private var lastLists: List<ListStatus> = emptyList()

    // Filters for which this run has been seen to queue or run work. A FAILED state only counts for these, because
    // the state left over from an earlier run says nothing about this one.
    private val started = HashSet<String>()

    val isRunning: Boolean get() = stopObserving != null

    private fun elapsedSeconds() = ((System.currentTimeMillis() - startedAt) / 1000).toInt()

    // Re-evaluates every second, so the screen shows that we are alive and "didn't start" can be detected
    private val ticker = object : Runnable {
        override fun run() {
            if (!isRunning) return
            update(lastMap)
            if (isRunning) handler.postDelayed(this, 1000)
        }
    }

    fun start(configs: List<AdblockConfig>, onProgress: (Progress) -> Unit) {
        if (isRunning) return
        if (configs.isEmpty()) {
            onProgress(Progress(State.NOTHING_CONFIGURED))
            return
        }

        val adFilter = try {
            AdFilter.get()
        } catch (e: Exception) {
            null
        }
        if (adFilter == null) {
            onProgress(Progress(State.UNAVAILABLE))
            return
        }

        if (!AdblockSyncLock.tryAcquire(appContext)) {
            onProgress(Progress(State.BUSY_ELSEWHERE))
            return
        }

        this.configs = configs
        this.callback = onProgress
        this.startedAt = System.currentTimeMillis()
        this.started.clear()

        // Make sure every configured list exists, then force a fresh download of each
        val viewModel = adFilter.viewModel
        val known = viewModel.filters.value ?: emptyMap()
        for (config in configs) {
            var filter = known.values.firstOrNull { it.url == config.value }
            if (filter != null && filter.hasDownloaded() && filter.filtersCount == 0) {
                // Recorded as downloaded but with no rules. The installer skips a list whose checksum is unchanged,
                // so it would never be loaded again: start from a clean slate instead.
                viewModel.removeFilter(filter.id)
                filter = null
            }
            val target = filter ?: viewModel.addFilter(config.label, config.value)
            viewModel.download(target.id)
        }

        watch(viewModel.filters)
        update(viewModel.filters.value ?: emptyMap())
        if (isRunning) {
            handler.postDelayed(ticker, 1000)
            handler.postDelayed({ finish(State.TIMED_OUT, report = true) }, TIMEOUT_MS)
        }
    }

    /**
     * Stops reporting to the screen, e.g. when it is closed. The downloads already started continue, so this keeps
     * watching (and keeps the lock) until they finish or time out; pressing the button again meanwhile reports "busy"
     * instead of starting a second round of downloads.
     */
    fun cancel() {
        callback = null
    }

    // Generic, because the view model exposes a LiveData of a concrete map type (e.g. LinkedHashMap)
    private fun <T : Map<String, Filter>> watch(liveData: LiveData<T>) {
        val observer = Observer<T> { map -> update(map) }
        liveData.observeForever(observer)
        stopObserving = { liveData.removeObserver(observer) }
    }

    private fun update(map: Map<String, Filter>) {
        lastMap = map
        val elapsed = elapsedSeconds()
        lastLists = configs.map { config ->
            val filter = map.values.firstOrNull { it.url == config.value }
            ListStatus(
                config.label,
                itemStateOf(filter, elapsed),
                filter?.updateTime ?: 0L,
                filter?.filtersCount ?: 0
            )
        }

        if (lastLists.all { it.finished }) {
            finish(if (lastLists.all { it.done }) State.FINISHED else State.FINISHED_WITH_ERRORS, report = true)
        } else {
            callback?.invoke(Progress(State.RUNNING, lastLists, elapsed))
        }
    }

    private fun itemStateOf(filter: Filter?, elapsed: Int): ItemState {
        if (filter == null) return ItemState.WAITING

        val libraryState = filter.downloadState
        if (libraryState.isRunning) started.add(filter.id)

        return when {
            libraryState == DownloadState.SUCCESS && filter.updateTime >= startedAt -> ItemState.DONE
            libraryState == DownloadState.DOWNLOADING -> ItemState.DOWNLOADING
            libraryState == DownloadState.INSTALLING -> ItemState.INSTALLING
            (libraryState == DownloadState.FAILED || libraryState == DownloadState.CANCELLED) &&
                filter.id in started -> ItemState.FAILED
            // queued, e.g. waiting for a network connection
            filter.id in started -> ItemState.WAITING
            // nothing was queued for this list: the library refused the request (a left-over download record)
            elapsed >= NOT_STARTED_AFTER_SECONDS -> ItemState.NOT_STARTED
            else -> ItemState.WAITING
        }
    }

    private fun finish(state: State, report: Boolean) {
        handler.removeCallbacksAndMessages(null)
        stopObserving?.invoke()
        stopObserving = null
        AdblockSyncLock.release()

        val cb = callback
        callback = null
        if (report) cb?.invoke(Progress(state, lastLists, elapsedSeconds()))
    }

    private companion object {
        const val TIMEOUT_MS = 3 * 60 * 1000L
        const val NOT_STARTED_AFTER_SECONDS = 10
    }
}

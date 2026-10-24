package com.cylonid.nativealpha.helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.cylonid.nativealpha.model.AdblockConfig
import io.github.edsuns.adfilter.AdFilter
import io.github.edsuns.adfilter.Filter

/**
 * Manually downloads all configured filter lists and reports progress. Must be used on the main thread.
 *
 * A list counts as finished once its update time is newer than the moment the download was started. Only one
 * process may download the lists at a time, so this shares the lock used by the automatic sync.
 */
class AdblockListDownloader(context: Context) {

    enum class State { RUNNING, FINISHED, TIMED_OUT, BUSY_ELSEWHERE, UNAVAILABLE, NOTHING_CONFIGURED }

    data class ListStatus(val label: String, val done: Boolean, val updateTime: Long = 0L)

    data class Progress(
        val state: State,
        val lists: List<ListStatus> = emptyList(),
        val elapsedSeconds: Int = 0
    ) {
        val total: Int get() = lists.size
        val done: Int get() = lists.count { it.done }
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private var stopObserving: (() -> Unit)? = null
    private var callback: ((Progress) -> Unit)? = null
    private var configs: List<AdblockConfig> = emptyList()
    private var startedAt = 0L
    private var lastLists: List<ListStatus> = emptyList()

    val isRunning: Boolean get() = stopObserving != null

    private fun elapsedSeconds() = ((System.currentTimeMillis() - startedAt) / 1000).toInt()

    // Re-reports every second so the screen can show that we are alive and waiting
    private val ticker = object : Runnable {
        override fun run() {
            callback?.invoke(Progress(State.RUNNING, lastLists, elapsedSeconds()))
            handler.postDelayed(this, 1000)
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

        // Make sure every configured list exists, then force a fresh download of each
        val viewModel = adFilter.viewModel
        val known = viewModel.filters.value ?: emptyMap()
        for (config in configs) {
            val filter = known.values.firstOrNull { it.url == config.value }
                ?: viewModel.addFilter(config.label, config.value)
            viewModel.download(filter.id)
        }

        watch(viewModel.filters)

        Log.d(TAG, "started at $startedAt for ${configs.size} lists")
        update(viewModel.filters.value ?: emptyMap())
        handler.postDelayed(ticker, 1000)
        handler.postDelayed({ finish(State.TIMED_OUT, report = true) }, TIMEOUT_MS)
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
        lastLists = configs.map { config ->
            val filter = map.values.firstOrNull { it.url == config.value }
            ListStatus(config.label, filter != null && filter.updateTime >= startedAt, filter?.updateTime ?: 0L)
        }
        Log.d(
            TAG, "filters changed (startedAt=$startedAt): " +
                map.values.joinToString { "${it.url} updateTime=${it.updateTime} hasDownloaded=${it.hasDownloaded()}" }
        )
        if (lastLists.all { it.done }) {
            finish(State.FINISHED, report = true)
        } else {
            callback?.invoke(Progress(State.RUNNING, lastLists, elapsedSeconds()))
        }
    }

    private fun finish(state: State, report: Boolean) {
        handler.removeCallbacksAndMessages(null)
        stopObserving?.invoke()
        stopObserving = null
        AdblockSyncLock.release()

        Log.d(TAG, "finished with state $state after ${elapsedSeconds()}s")
        val cb = callback
        callback = null
        if (report) cb?.invoke(Progress(state, lastLists, elapsedSeconds()))
    }

    private companion object {
        const val TAG = "AdblockDL"
        const val TIMEOUT_MS = 3 * 60 * 1000L
    }
}

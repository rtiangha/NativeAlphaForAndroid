package com.cylonid.nativealpha.activities

import android.app.ActivityManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.databinding.AdblockConfigActivityBinding
import com.cylonid.nativealpha.databinding.AddAdblockConfigDialogBinding
import com.cylonid.nativealpha.fragments.adblocklist.AdblockListFragment
import com.cylonid.nativealpha.helper.AdblockListDownloader
import com.cylonid.nativealpha.model.AdblockConfig
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.NotificationUtils
import com.cylonid.nativealpha.util.ProcessUtils


class AdblockConfigActivity : ToolbarBaseActivity<AdblockConfigActivityBinding>() {
    private lateinit var adblockListFragment: AdblockListFragment
    private val listDownloader by lazy { AdblockListDownloader(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.adblockFab.setOnClickListener { showAddAdblockDialog() }

        binding.btnRestoreDefault.setOnClickListener {
            DataManager.getInstance().apply {
                settings.globalWebApp.adBlockSettings = Const.getDefaultAdBlockConfig()
                saveGlobalSettings()
            }
            ProcessUtils.closeAllWebAppsAndProcesses(getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            updateAdblockList()
        }

        binding.btnDownloadFilters.setOnClickListener {
            listDownloader.start(DataManager.getInstance().settings.globalWebApp.adBlockSettings) { progress ->
                showDownloadProgress(progress)
            }
        }

        setToolbarTitle(getString(R.string.adblock_config))

        adblockListFragment =
            supportFragmentManager.findFragmentById(R.id.adblock_fragment_container_view) as AdblockListFragment
    }

    override fun inflateBinding(layoutInflater: LayoutInflater): AdblockConfigActivityBinding {
        return AdblockConfigActivityBinding.inflate(layoutInflater)
    }

    private fun updateAdblockList() {
        adblockListFragment.updateAdblockList()
    }

    private fun showDownloadProgress(p: AdblockListDownloader.Progress) {
        binding.btnDownloadFilters.isEnabled = p.state != AdblockListDownloader.State.RUNNING

        binding.downloadProgress.visibility = if (p.lists.isEmpty()) View.GONE else View.VISIBLE
        // Animated while nothing has finished yet, so a waiting download doesn't look like a frozen, empty bar
        binding.downloadProgress.isIndeterminate = p.state == AdblockListDownloader.State.RUNNING && p.done == 0
        binding.downloadProgress.max = maxOf(p.total, 1)
        binding.downloadProgress.progress = p.done + p.failed

        val summary = when (p.state) {
            AdblockListDownloader.State.RUNNING -> getString(R.string.adblock_dl_running, p.done, p.total, p.elapsedSeconds)
            AdblockListDownloader.State.FINISHED -> getString(R.string.adblock_dl_finished, p.total)
            AdblockListDownloader.State.FINISHED_WITH_ERRORS ->
                getString(R.string.adblock_dl_finished_errors, p.done, p.total, p.failed)
            AdblockListDownloader.State.TIMED_OUT -> getString(R.string.adblock_dl_timeout, p.done, p.total)
            AdblockListDownloader.State.BUSY_ELSEWHERE -> getString(R.string.adblock_dl_busy)
            AdblockListDownloader.State.UNAVAILABLE -> getString(R.string.adblock_dl_unavailable)
            AdblockListDownloader.State.NOTHING_CONFIGURED -> getString(R.string.adblock_dl_none)
        }
        val details = p.lists.joinToString("\n") { describe(it) }

        binding.downloadStatus.visibility = View.VISIBLE
        binding.downloadStatus.text = if (details.isEmpty()) summary else "$summary\n$details"
    }

    private fun describe(item: AdblockListDownloader.ListStatus): String {
        val age = if (item.updateTime <= 0L) {
            getString(R.string.adblock_dl_never)
        } else {
            android.text.format.DateUtils.getRelativeTimeSpanString(
                item.updateTime,
                System.currentTimeMillis(),
                android.text.format.DateUtils.SECOND_IN_MILLIS
            ).toString()
        }
        val symbol = when (item.itemState) {
            AdblockListDownloader.ItemState.DONE -> "✓"
            AdblockListDownloader.ItemState.FAILED, AdblockListDownloader.ItemState.NOT_STARTED -> "✗"
            else -> "…"
        }
        val stateText = getString(
            when (item.itemState) {
                AdblockListDownloader.ItemState.WAITING -> R.string.adblock_dl_item_waiting
                AdblockListDownloader.ItemState.DOWNLOADING -> R.string.adblock_dl_item_downloading
                AdblockListDownloader.ItemState.INSTALLING -> R.string.adblock_dl_item_installing
                AdblockListDownloader.ItemState.DONE -> R.string.adblock_dl_item_updated
                AdblockListDownloader.ItemState.FAILED -> R.string.adblock_dl_item_failed
                AdblockListDownloader.ItemState.NOT_STARTED -> R.string.adblock_dl_item_not_started
            }
        )
        val text = "$symbol ${item.label} · $stateText"
        return when {
            // Installed, but the engine found no rules in it
            item.done && item.rules == 0 -> "$text · ${getString(R.string.adblock_dl_item_no_rules)}"
            item.done -> text
            else -> "$text · ${getString(R.string.adblock_dl_last_updated, age)}"
        }
    }

    override fun onDestroy() {
        listDownloader.cancel()
        super.onDestroy()
    }

    private fun showAddAdblockDialog() {
        val localBinding = AddAdblockConfigDialogBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(localBinding.root)
            .setTitle(getString(R.string.add_a_new_adblock_provider))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->

                val url = localBinding.addAdblockUrl.text.toString().trim()

                val formattedUrl =
                    if (url.startsWith("https://") || url.startsWith("http://")) url else "https://$url"

                val urlAlreadyExists = DataManager.getInstance().settings.globalWebApp.adBlockSettings.any { it.value == formattedUrl}
                if(urlAlreadyExists) {
                    NotificationUtils.showToast(this, getString(R.string.entry_already_exists))
                    return@setPositiveButton
                }

                DataManager.getInstance().apply {
                    val label =
                        if (localBinding.addAdblockLabel.text.isNotEmpty()) localBinding.addAdblockLabel.text.toString() else url
                    settings.globalWebApp.adBlockSettings += AdblockConfig(label, formattedUrl)
                    saveGlobalSettings()
                }
                updateAdblockList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()

        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        okButton.isEnabled = false
        localBinding.addAdblockUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                okButton.isEnabled = !s.isNullOrBlank()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

    }
}



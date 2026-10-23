package com.cylonid.nativealpha.helper


import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import java.util.concurrent.atomic.AtomicInteger
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.model.DataManager

class AdblockLifecycleHelper(private val activity: Activity) {

    // The crash flag lives in a SharedPreferences file of its own per process. SharedPreferences are not
    // multi-process safe and every sandbox runs in its own process, so one shared flag would be overwritten by,
    // or falsely seen by, other processes.
    private val prefs by lazy {
        val process = Application.getProcessName().replace(':', '_')
        activity.applicationContext.getSharedPreferences("adblock_state_$process", Context.MODE_PRIVATE)
    }

    private var hasCrashed: Boolean
        get() = prefs.getBoolean(KEY_CRASHED, false)
        // commit(): the "in progress" marker must be on disk before the risky operation starts
        @SuppressLint("ApplySharedPref")
        set(value) {
            prefs.edit().putBoolean(KEY_CRASHED, value).commit()
        }

    fun trySyncOperation(callback: AdblockLifecycleCallback) {
        beforeAdblockOperation(callback)
        afterAdblockOperation()
    }

    // True while this helper (i.e. this activity) has an adblock operation in progress.
    private var active = false

    fun beforeAdblockOperation(callback: AdblockLifecycleCallback) {
        // A set flag only means "the previous run crashed" if no operation of this still running process is behind
        // it. Otherwise a second web app opened in the same process while the first one is still syncing would be
        // reported as a crash.
        if (hasCrashed && activeOperations.get() == 0) {
            showWarningDialog()
            hasCrashed = false
            return
        }
        if (!active) {
            active = true
            activeOperations.incrementAndGet()
        }
        hasCrashed = true
        callback.execute()
    }

    /** Safe to call repeatedly; the flag is only cleared when the last operation in this process has ended. */
    fun afterAdblockOperation() {
        if (!active) return
        active = false
        if (activeOperations.decrementAndGet() <= 0) {
            activeOperations.set(0)
            hasCrashed = false
        }
    }

    /**
     * Runs the filter list sync unless another process is already syncing (web apps in different sandboxes would
     * otherwise download the same lists at the same time). Returns false if the sync was skipped.
     */
    fun syncListsIfNoOtherProcessIs(callback: AdblockLifecycleCallback): Boolean {
        if (!AdblockSyncLock.tryAcquire(activity)) return false
        beforeAdblockOperation(callback)
        return true
    }

    /** Called once all lists are up to date, so other processes may sync again. */
    fun releaseSyncLock() {
        AdblockSyncLock.release()
    }

    private fun showWarningDialog() {
        AlertDialog.Builder(activity)
            .setMessage(activity.getString(R.string.adblock_warning_text))
            .setCancelable(false)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(activity.getString(R.string.warning))
            .setPositiveButton(activity.getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                DataManager.getInstance().apply {
                    settings.globalWebApp.adBlockSettings.clear()
                    saveGlobalSettings()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }
            .create().show()
    }

    fun interface AdblockLifecycleCallback {
        fun execute()
    }

    private companion object {
        const val KEY_CRASHED = "adblockCrash"

        // Adblock operations currently in progress in this process
        val activeOperations = AtomicInteger(0)
    }

}
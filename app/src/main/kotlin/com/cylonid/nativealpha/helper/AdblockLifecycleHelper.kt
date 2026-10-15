package com.cylonid.nativealpha.helper


import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
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

    fun beforeAdblockOperation(callback: AdblockLifecycleCallback) {
        if (hasCrashed) {
            showWarningDialog()
            hasCrashed = false
            return
        }
        hasCrashed = true
        callback.execute()
    }

    fun afterAdblockOperation() {
        hasCrashed = false
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
    }

}
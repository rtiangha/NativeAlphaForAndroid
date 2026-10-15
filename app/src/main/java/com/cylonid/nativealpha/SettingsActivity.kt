package com.cylonid.nativealpha

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.cylonid.nativealpha.activities.AdblockConfigActivity
import com.cylonid.nativealpha.activities.ToolbarBaseActivity
import com.cylonid.nativealpha.databinding.GlobalSettingsBinding
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.GlobalSettings
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.NotificationUtils
import com.cylonid.nativealpha.util.Utility
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : ToolbarBaseActivity<GlobalSettingsBinding>() {

    private val exportSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) onExportFileChosen(result.data?.data)
        }

    private val importSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) onImportFileChosen(result.data?.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setToolbarTitle(getString(R.string.global_settings))

        val settings = DataManager.getInstance().settings
        val modified_settings = settings.copy()
        binding.settings = modified_settings
        binding.btnAdblockConfig.setOnClickListener { v: View? ->
            val intent = Intent(
                this@SettingsActivity,
                AdblockConfigActivity::class.java
            )
            intent.setAction(Intent.ACTION_VIEW)
            startActivity(intent)
        }

        binding.btnGlobalWebApp.setOnClickListener { v: View? ->
            val intent = Intent(
                this@SettingsActivity,
                WebAppSettingsActivity::class.java
            )
            intent.putExtra(
                Const.INTENT_WEBAPPID,
                settings.globalWebApp.ID
            )
            intent.setAction(Intent.ACTION_VIEW)
            startActivity(intent)
        }


        binding.btnExportSettings.setOnClickListener { v: View? ->
            val intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
            val sdf =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val currentDateTime = sdf.format(Date())
            intent.putExtra(Intent.EXTRA_TITLE, "NativeAlpha_$currentDateTime")
            try {
                exportSettingsLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                NotificationUtils.showInfoSnackbar(
                    this@SettingsActivity,
                    getString(R.string.no_filemanager),
                    Snackbar.LENGTH_LONG
                )
                e.printStackTrace()
            }
        }

        binding.btnImportSettings.setOnClickListener { v: View? ->
            val intent = Intent().setType("*/*").setAction(Intent.ACTION_GET_CONTENT)
            try {
                importSettingsLauncher.launch(
                    Intent.createChooser(intent, "Select a file")
                )
            } catch (e: ActivityNotFoundException) {
                NotificationUtils.showInfoSnackbar(
                    this@SettingsActivity,
                    getString(R.string.no_filemanager),
                    Snackbar.LENGTH_LONG
                )
                e.printStackTrace()
            }
        }

        binding.btnSave.setOnClickListener {
            DataManager.getInstance().settings = modified_settings
            finish()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    override fun inflateBinding(layoutInflater: LayoutInflater): GlobalSettingsBinding {
        return GlobalSettingsBinding.inflate(layoutInflater)
    }

    private fun onExportFileChosen(uri: Uri?) {
        DataManager.getInstance()
            .saveGlobalSettings() //Needed to write legacy settings to new XML

        if (!DataManager.getInstance().saveSharedPreferencesToFile(uri)) {
            NotificationUtils.showInfoSnackbar(
                this,
                getString(R.string.export_failed),
                Snackbar.LENGTH_LONG
            )
        } else {
            NotificationUtils.showInfoSnackbar(
                this,
                getString(R.string.export_success),
                Snackbar.LENGTH_SHORT
            )
        }
    }

    private fun onImportFileChosen(uri: Uri?) {
        if (!DataManager.getInstance().loadSharedPreferencesFromFile(uri)) {
            NotificationUtils.showInfoSnackbar(
                this,
                getString(R.string.import_failed),
                Snackbar.LENGTH_LONG
            )
        } else {
            val i = Intent(this@SettingsActivity, MainActivity::class.java)

            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)

            DataManager.getInstance().loadAppData()
            i.putExtra(Const.INTENT_BACKUP_RESTORED, true)
            finish()
            startActivity(i)
        }
    }
}

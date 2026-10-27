package com.cylonid.nativealpha.helper

import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricPrompt.PromptInfo
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.util.NotificationUtils
import com.cylonid.nativealpha.util.Utility

internal class BiometricPromptHelper(private val activity: FragmentActivity) {
    private val allowedAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * @param funFail called when authentication failed or was cancelled by the user, and also when no biometric or
     * screen lock is available at all (the caller must not be left waiting for a result that never comes)
     * @param funInterrupted called when the system cancelled the prompt, e.g. because the app went to the background;
     * the user did not decide anything
     */
    @JvmOverloads
    fun showPrompt(
        funSuccess: BiometricPromptCallback,
        funFail: BiometricPromptCallback,
        promptTitle: String,
        funInterrupted: BiometricPromptCallback = BiometricPromptCallback {}
    ) {
        val supported = isBiometricsSupported(activity)
        if (!supported) {
            funFail.execute()
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_CANCELED) {
                        funInterrupted.execute()
                    } else {
                        funFail.execute()
                    }
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    funSuccess.execute()
                }
            })
        val promptInfo = PromptInfo.Builder()
            .setTitle(promptTitle)
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun isBiometricsSupported(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        var isSupported = false

        when (biometricManager.canAuthenticate(allowedAuthenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                isSupported = true
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                NotificationUtils.showToast(activity, activity.getString(R.string.no_biometric_keys_enrolled))
            }
            else -> {
                NotificationUtils.showToast(activity, activity.getString(R.string.no_biometric_devices))
            }
        }
        return isSupported
    }

    internal fun interface BiometricPromptCallback {
        fun execute()
    }
}
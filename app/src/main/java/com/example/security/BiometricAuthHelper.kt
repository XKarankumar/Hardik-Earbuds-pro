package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

data class BiometricCapabilityStatus(
    val canAuthenticate: Boolean,
    val statusLabel: String
)

object BiometricAuthHelper {

    private const val ALLOWED_MODES =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun checkAvailability(context: Context): BiometricCapabilityStatus {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(ALLOWED_MODES)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapabilityStatus(
                canAuthenticate = true,
                statusLabel = "Fingerprint / Biometric Hardware Ready"
            )
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapabilityStatus(
                canAuthenticate = false,
                statusLabel = "No fingerprint/biometric enrolled in Android Settings"
            )
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapabilityStatus(
                canAuthenticate = false,
                statusLabel = "Biometric hardware unavailable on this device"
            )
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapabilityStatus(
                canAuthenticate = false,
                statusLabel = "Biometric sensor temporarily unavailable"
            )
            else -> BiometricCapabilityStatus(
                canAuthenticate = false,
                statusLabel = "Biometric authentication unavailable (PIN supported)"
            )
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Owner Verification",
        subtitle: String = "Verify your identity for HARDIK BUDS PRO Security Center",
        negativeButtonText: String = "Use Owner PIN",
        onSucceeded: () -> Unit,
        onErrorOrCancelled: (String) -> Unit
    ) {
        val availability = checkAvailability(activity)
        if (!availability.canAuthenticate) {
            onErrorOrCancelled(availability.statusLabel)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSucceeded()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onErrorOrCancelled(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED_MODES)
            .setNegativeButtonText(negativeButtonText)
            .build()

        prompt.authenticate(promptInfo)
    }
}

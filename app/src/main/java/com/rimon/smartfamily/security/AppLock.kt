package com.rimon.smartfamily.security

import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * App lock (spec section 17): fingerprint / face, or the phone's own PIN, pattern or password.
 * The app locks when it was in the background longer than the chosen number of minutes.
 */
object AppLock {
    @Volatile private var backgroundSince = 0L
    @Volatile var unlocked = false
        private set

    fun onBackground() { backgroundSince = SystemClock.elapsedRealtime() }

    /** Called on start/resume; true when the lock screen must be shown. */
    fun needsUnlock(enabled: Boolean, minutes: Int): Boolean {
        if (!enabled) { unlocked = true; return false }
        if (unlocked && backgroundSince > 0 && SystemClock.elapsedRealtime() - backgroundSince > minutes * 60_000L) unlocked = false
        return !unlocked
    }

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticators() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun prompt(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true; backgroundSince = 0; onResult(true) }
            override fun onAuthenticationError(code: Int, msg: CharSequence) { onResult(false) }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(authenticators())
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onResult(false) }
    }
}

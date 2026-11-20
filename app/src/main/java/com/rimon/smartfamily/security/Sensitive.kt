package com.rimon.smartfamily.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf

/**
 * 2.2: sensitive screens (secrets, chat, money, documents) block screenshots and the recent-apps preview by themselves,
 * even when "secure screens" is off for the whole app.
 */
object Sensitive {
    val open = mutableIntStateOf(0)
    /** UI tests take screenshots of every screen; they switch the automatic blocking off. */
    @Volatile var allowCaptureForTests = false
}

/** Put at the top of a sensitive screen. */
@Composable
fun SensitiveScreen() {
    DisposableEffect(Unit) {
        Sensitive.open.intValue++
        onDispose { Sensitive.open.intValue-- }
    }
}

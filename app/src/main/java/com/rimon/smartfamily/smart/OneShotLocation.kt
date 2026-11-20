package com.rimon.smartfamily.smart

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One location fix, taken only when the user taps (chat "send my location", "use my location" for zmanim).
 * Nothing runs in the background and nothing is tracked. Falls back to the last known fix.
 */
object OneShotLocation {
    @SuppressLint("MissingPermission")
    suspend fun get(context: Context, timeoutMs: Long = 12_000): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 2 * 60_000) return last
        val provider = providers.firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return last
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                runCatching {
                    if (Build.VERSION.SDK_INT >= 30) {
                        val cancel = android.os.CancellationSignal()
                        cont.invokeOnCancellation { cancel.cancel() }
                        lm.getCurrentLocation(provider, cancel, context.mainExecutor) { cont.resume(it) }
                    } else {
                        @Suppress("DEPRECATION")
                        lm.requestSingleUpdate(provider, { loc -> if (cont.isActive) cont.resume(loc) }, android.os.Looper.getMainLooper())
                    }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return fresh ?: last
    }
}

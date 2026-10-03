package com.pebbledetective.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Where the pebble was found, if the child's device will say.
 *
 * Uses the platform LocationManager rather than play-services-location. That
 * keeps Google Play Services - and anything that could reach the network -
 * out of the dependency graph entirely, which is what lets this app declare
 * no network permissions at all. It also works on devices without GMS.
 *
 * Coarse accuracy only, and entirely optional: if permission is refused or
 * no fix arrives, the pebble is simply logged without a place.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** A single fix, or null. Never throws, never blocks the flow. */
    suspend fun currentLocation(timeoutMs: Long = 10_000L): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val provider = manager.bestAvailableProvider() ?: return null

        return try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        // LocationManagerCompat, not LocationManager: the
                        // platform getCurrentLocation is API 30+ and minSdk
                        // here is 26, so the direct call crashes on Android
                        // 8 to 10. The compat version backports it.
                        @Suppress("DEPRECATION")
                        LocationManagerCompat.getCurrentLocation(
                            manager,
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(context),
                        ) { location -> if (cont.isActive) cont.resume(location) }
                    } catch (_: SecurityException) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            null
        }
    }

    private fun LocationManager.bestAvailableProvider(): String? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return candidates.firstOrNull { provider ->
            runCatching { isProviderEnabled(provider) }.getOrDefault(false)
        }
    }
}

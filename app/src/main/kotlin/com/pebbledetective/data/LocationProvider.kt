package com.pebbledetective.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
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
 *
 * A recent cached fix is preferred over a fresh one, and a stale cached fix
 * is preferred over nothing. "Where was this pebble found" needs neither
 * metre accuracy nor a brand new reading, and insisting on a fresh fix is
 * part of why this failed in the field: the GPS was still thinking by the
 * time the child had moved on to the next stone.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** A single fix, or null. Never throws, never blocks anything that matters. */
    // androidx's CancellationSignal is deprecated in favour of the platform
    // one, but LocationManagerCompat still takes it, and the platform
    // getCurrentLocation it would let us call is API 30+ against minSdk 26.
    @Suppress("DEPRECATION")
    suspend fun currentLocation(timeoutMs: Long = FRESH_FIX_TIMEOUT_MS): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        // A fix from the last couple of minutes is as good as a new one for
        // this purpose, and it is available instantly.
        lastKnown(manager)?.let { if (it.isRecent()) return it }

        val provider = enabledProvider(manager) ?: return lastKnown(manager)
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    // LocationManagerCompat, not LocationManager: the platform
                    // getCurrentLocation is API 30+ and minSdk here is 26, so
                    // the direct call crashes on Android 8 to 10.
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

        // An old fix still beats no place at all.
        return fresh ?: lastKnown(manager)
    }

    /** The newest cached fix across every provider that has one. */
    private fun lastKnown(manager: LocationManager): Location? {
        var best: Location? = null
        for (provider in candidateProviders()) {
            val location = try {
                manager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null  // this device has no such provider
            }
            if (location != null && (best == null || location.time > best.time)) {
                best = location
            }
        }
        return best
    }

    private fun enabledProvider(manager: LocationManager): String? =
        candidateProviders().firstOrNull { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }

    private fun candidateProviders(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }

    private fun Location.isRecent(): Boolean {
        val nowMs = SystemClock.elapsedRealtimeNanos() / 1_000_000
        val ageMs = nowMs - elapsedRealtimeNanos / 1_000_000
        return ageMs in 0..RECENT_FIX_MS
    }

    private companion object {
        /** Nothing is waiting on this any more, so it can afford to be patient. */
        const val FRESH_FIX_TIMEOUT_MS = 25_000L

        /**
         * How old a cached fix may be and still be used as-is.
         *
         * Generous on purpose. This records "the park where the pebble was
         * found", not a survey point, and a child working through a pile of
         * stones stays within a few hundred metres the whole time. A tight
         * window here just means waiting on the GPS and usually recording
         * nothing at all, which is exactly how this failed in the field.
         */
        const val RECENT_FIX_MS = 10 * 60 * 1_000L
    }
}

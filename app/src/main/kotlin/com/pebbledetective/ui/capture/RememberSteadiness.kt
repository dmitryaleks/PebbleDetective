package com.pebbledetective.ui.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pebbledetective.domain.StabilityTracker
import kotlin.math.abs
import kotlin.math.sqrt

/** How steady the phone is right now. */
data class Steadiness(val steady: Boolean, val progress: Float)

/**
 * Tracks how still the phone is being held.
 *
 * Prefers the gyroscope: angular-rate magnitude is drift free and needs no
 * gravity separation, so it is the cleanest "is this being waved about"
 * signal. Where there is no gyroscope it falls back to the accelerometer and
 * measures the residual after low-pass filtering out gravity.
 *
 * The listener is registered only while the capture screen is composed, never
 * for the life of the app.
 *
 * @param armedAtMs when the child asked for a shot, or null while not armed.
 *   Thresholds relax the longer this has been going on.
 */
@Composable
fun rememberSteadiness(armedAtMs: Long?): State<Steadiness> {
    val context = LocalContext.current
    val tracker = remember { StabilityTracker() }
    val steadiness = remember { mutableStateOf(Steadiness(false, 0f)) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gyro = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accel = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensor = gyro ?: accel

        if (manager == null || sensor == null) {
            // No usable sensor: never block the child on a gate we cannot judge.
            steadiness.value = Steadiness(steady = true, progress = 1f)
            return@DisposableEffect onDispose { }
        }

        val usingGyro = sensor === gyro
        val gravity = FloatArray(3)
        var seenGravity = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = SystemClock.elapsedRealtime()
                val magnitude = if (usingGyro) {
                    val (x, y, z) = event.values
                    sqrt(x * x + y * y + z * z)
                } else {
                    // Complementary filter, then the residual after gravity.
                    for (i in 0..2) {
                        gravity[i] = if (seenGravity) {
                            GRAVITY_ALPHA * gravity[i] + (1 - GRAVITY_ALPHA) * event.values[i]
                        } else {
                            event.values[i]
                        }
                    }
                    seenGravity = true
                    val residual = sqrt(
                        (0..2).sumOf { i ->
                            val d = (event.values[i] - gravity[i]).toDouble()
                            d * d
                        }
                    ).toFloat()
                    // Scale the m/s^2 residual onto the gyro's rad/s thresholds.
                    abs(residual) * ACCEL_TO_GYRO_SCALE
                }

                tracker.sample(now, magnitude)
                val elapsed = armedAtMs?.let { now - it } ?: 0L
                steadiness.value = Steadiness(
                    steady = tracker.isSteady(elapsed),
                    progress = tracker.progress(elapsed),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }

    return steadiness
}

private operator fun FloatArray.component1() = this[0]
private operator fun FloatArray.component2() = this[1]
private operator fun FloatArray.component3() = this[2]

private const val GRAVITY_ALPHA = 0.8f

/** 0.35 m/s^2 of shake is about as bad as 0.20 rad/s of rotation. */
private const val ACCEL_TO_GYRO_SCALE = 0.20f / 0.35f

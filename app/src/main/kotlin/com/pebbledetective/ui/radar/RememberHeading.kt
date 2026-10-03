package com.pebbledetective.ui.radar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.pebbledetective.domain.Geo

/**
 * Which way the phone is pointing, in degrees clockwise from magnetic north.
 *
 * The rotation vector fuses the accelerometer, gyroscope and magnetometer,
 * which is far steadier than the magnetometer alone - a bare compass reading
 * jitters by tens of degrees and makes the radar twitch.
 *
 * The reading is smoothed along the *shortest* turn. Interpolating raw
 * azimuths would send the whole display spinning the long way round every
 * time the player walked past north.
 */
@Composable
fun rememberHeading(): State<Float> {
    val context = LocalContext.current
    val view = LocalView.current
    val heading = remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

        if (manager == null || sensor == null) {
            // No compass: the radar still works, it just cannot rotate with
            // the phone. North stays up.
            return@DisposableEffect onDispose { }
        }

        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothed: Float? = null

        val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
                SensorManager.getOrientation(remapped, orientation)

                val raw = Geo.normaliseDegrees(Math.toDegrees(orientation[0].toDouble()))
                val previous = smoothed
                val next = if (previous == null) {
                    raw.toFloat()
                } else {
                    // Low-pass along the short arc, so north is not a cliff.
                    val step = Geo.shortestTurn(previous.toDouble(), raw) * SMOOTHING
                    Geo.normaliseDegrees(previous + step).toFloat()
                }
                smoothed = next
                heading.floatValue = next
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }

    return heading
}

private const val SMOOTHING = 0.18

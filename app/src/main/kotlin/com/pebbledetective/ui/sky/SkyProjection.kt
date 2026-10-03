package com.pebbledetective.ui.sky

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Which way the phone is pointing, as a full attitude rather than a compass
 * bearing.
 *
 * The radar only needs a heading. Augmented reality needs all three axes:
 * a planet forty degrees up has to stay put when the phone is tilted, and
 * has to roll with the screen when the phone is turned over.
 *
 * Holds the rotation that takes a direction in the world - east, north, up -
 * into the phone's own frame, where x is to the right of the screen, y is up
 * it, and the rear camera looks along negative z.
 */
class Attitude(private val worldToDevice: FloatArray) {

    /**
     * Where a point in the sky falls on screen, or null when it is behind
     * the phone.
     *
     * @param focalPixels the pinhole focal length of the preview, which is
     *   what ties the overlay to the live image rather than to an arbitrary
     *   scale.
     */
    fun project(
        azimuthDegrees: Double,
        altitudeDegrees: Double,
        size: Size,
        focalPixels: Float,
    ): Offset? {
        val d = toDevice(azimuthDegrees, altitudeDegrees)
        // The camera looks along negative z; anything else is behind you.
        if (d[2] >= -MIN_DEPTH) return null
        val depth = -d[2]
        return Offset(
            x = size.width / 2f + focalPixels * d[0] / depth,
            y = size.height / 2f - focalPixels * d[1] / depth,
        )
    }

    /**
     * Which way to swing the phone to bring a point into view: a unit vector
     * in screen space, pointing from the centre of the screen toward it.
     *
     * Works whether the target is just off the edge or directly behind,
     * because it is read off the device-frame vector rather than from a
     * projection that would have gone to infinity.
     */
    fun screenDirection(azimuthDegrees: Double, altitudeDegrees: Double): Offset {
        val d = toDevice(azimuthDegrees, altitudeDegrees)
        val length = hypot(d[0], -d[1])
        if (length < 1e-4f) return Offset(0f, -1f)
        return Offset(d[0] / length, -d[1] / length)
    }

    /** How far off the centre of the view a point lies, in degrees. */
    fun offAxisDegrees(azimuthDegrees: Double, altitudeDegrees: Double): Double {
        val d = toDevice(azimuthDegrees, altitudeDegrees)
        return Math.toDegrees(acos((-d[2]).coerceIn(-1f, 1f).toDouble()))
    }

    private fun toDevice(azimuthDegrees: Double, altitudeDegrees: Double): FloatArray {
        val altitude = Math.toRadians(altitudeDegrees)
        val azimuth = Math.toRadians(azimuthDegrees)
        // East, north, up: the frame Android reports its rotation against.
        val east = (cos(altitude) * sin(azimuth)).toFloat()
        val north = (cos(altitude) * cos(azimuth)).toFloat()
        val up = sin(altitude).toFloat()

        val m = worldToDevice
        return floatArrayOf(
            m[0] * east + m[1] * north + m[2] * up,
            m[3] * east + m[4] * north + m[5] * up,
            m[6] * east + m[7] * north + m[8] * up,
        )
    }

    private companion object {
        /** Keeps a point exactly on the horizon of vision from exploding. */
        const val MIN_DEPTH = 1e-3f
    }
}

/**
 * Follows the phone's attitude.
 *
 * Smoothed as a quaternion rather than as three angles. Averaging angles
 * tears at every wrap point, and the radar's trick of smoothing the short
 * way round works for one axis but not for three at once; interpolating the
 * quaternion and renormalising is both correct and cheaper.
 */
@Composable
fun rememberAttitude(): State<Attitude> {
    val context = LocalContext.current
    val view = LocalView.current
    val state = remember { mutableStateOf(Attitude(IDENTITY.copyOf())) }

    DisposableEffect(context, view) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        // The app is portrait locked, but the natural orientation of a large
        // screen is landscape, so the sensor frame still has to be remapped
        // onto the frame the screen is actually drawn in.
        val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }

        val smoothed = FloatArray(4)
        var seeded = false
        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val transposed = FloatArray(9)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val w = if (event.values.size >= 4) {
                    event.values[3]
                } else {
                    val squared = 1f - x * x - y * y - z * z
                    if (squared > 0f) sqrt(squared) else 0f
                }

                if (!seeded) {
                    smoothed[0] = x; smoothed[1] = y; smoothed[2] = z; smoothed[3] = w
                    seeded = true
                } else {
                    // A quaternion and its negation are the same rotation, so
                    // flip the incoming one when it points the other way or
                    // the average swings through the long side.
                    val dot = smoothed[0] * x + smoothed[1] * y + smoothed[2] * z + smoothed[3] * w
                    val sign = if (dot < 0f) -1f else 1f
                    smoothed[0] += (sign * x - smoothed[0]) * SMOOTHING
                    smoothed[1] += (sign * y - smoothed[1]) * SMOOTHING
                    smoothed[2] += (sign * z - smoothed[2]) * SMOOTHING
                    smoothed[3] += (sign * w - smoothed[3]) * SMOOTHING
                    val length = sqrt(
                        smoothed[0] * smoothed[0] + smoothed[1] * smoothed[1] +
                            smoothed[2] * smoothed[2] + smoothed[3] * smoothed[3]
                    )
                    if (length > 1e-6f) for (i in 0..3) smoothed[i] /= length
                }

                SensorManager.getRotationMatrixFromVector(rotation, smoothed)
                SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
                // The sensor gives device to world; the overlay needs the
                // other way about, and for a rotation that is the transpose.
                for (row in 0..2) {
                    for (column in 0..2) {
                        transposed[row * 3 + column] = remapped[column * 3 + row]
                    }
                }
                state.value = Attitude(transposed.copyOf())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }

    return state
}

/**
 * How wide a view the rear camera gives, up and down the screen.
 *
 * Needed to put a label on the right part of the live image: the overlay has
 * to be drawn at the camera's own scale or everything sits at the right
 * bearing and the wrong distance from the middle.
 *
 * Read from the lens itself rather than assumed. The preview is pinned to a
 * four-by-three frame, whose long side fills the height of a portrait
 * screen, so the full long-axis field of view is what is on show.
 */
fun rearCameraVerticalFov(context: Context): Float {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        ?: return DEFAULT_VERTICAL_FOV

    val measured = runCatching {
        val id = manager.cameraIdList.firstOrNull { cameraId ->
            manager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return@runCatching null

        val characteristics = manager.getCameraCharacteristics(id)
        val focal = characteristics
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull() ?: return@runCatching null
        val sensor = characteristics
            .get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return@runCatching null
        val orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

        // A sensor mounted sideways, which is nearly all of them, puts its
        // own width up the screen when the phone is held upright.
        val alongScreen = if (orientation == 90 || orientation == 270) sensor.width else sensor.height
        Math.toDegrees(2.0 * atan((alongScreen / (2.0 * focal)))).toFloat()
    }.getOrNull()

    // Anything outside this is a misreading, not a lens.
    return measured?.takeIf { it in 30f..110f } ?: DEFAULT_VERTICAL_FOV
}

/** Turns a field of view into the focal length a projection needs. */
fun focalPixels(viewHeight: Float, verticalFovDegrees: Float): Float =
    (viewHeight / 2f) / tan(Math.toRadians(verticalFovDegrees / 2.0)).toFloat()

/** True when a projected point is close enough to the screen to be worth drawing. */
fun Offset.isOnScreen(size: Size, margin: Float = 0f): Boolean =
    x >= -margin && y >= -margin && x <= size.width + margin && y <= size.height + margin &&
        abs(x) < 1e6f && abs(y) < 1e6f

private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
private const val SMOOTHING = 0.25f

/** A middling phone main camera, for when the lens will not say. */
private const val DEFAULT_VERTICAL_FOV = 62f

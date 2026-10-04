package com.pebbledetective.ui.journey

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pebbledetective.domain.Astronomy
import com.pebbledetective.domain.Planet
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The solar system as it actually stands, seen from above and to one side.
 *
 * The angles are real: every body is placed from [Astronomy], so the
 * opening shot of the journey is a picture of where the planets were on
 * the day that pebble was picked up. The *radii* are not, and cannot be -
 * Mercury is a thirtieth of Neptune's distance, so a true scale is either
 * a blank screen with a dot in the corner or an inner system three pixels
 * across. They are compressed by a power law, which is what every orrery
 * ever built does and for the same reason.
 */
object SolarSystem {

    /** The order they are drawn in, nearest the Sun first. */
    val BODIES: List<Planet> = listOf(
        Planet.MERCURY, Planet.VENUS, Planet.EARTH, Planet.MOON, Planet.MARS,
        Planet.JUPITER, Planet.SATURN, Planet.URANUS, Planet.NEPTUNE,
    )

    /** Where one body sits on screen, and how big to draw it. */
    data class Placement(val at: Offset, val radiusPx: Float, val depth: Float)

    /**
     * How far to shove the Moon off the Earth so that both can be seen.
     *
     * At this compression the Moon's real offset is a fraction of a pixel:
     * it is a four-hundredth of the Earth's distance from the Sun. Every
     * orrery ever built tells the same lie, and the alternative is a Moon
     * that is simply not there.
     */
    const val MOON_NUDGE = 2.6f

    /**
     * Projects a heliocentric position onto the screen.
     *
     * @param tiltDegrees how far the camera is above the plane of the
     *   planets. Flat on would be a line; straight down would be a clock
     *   face; somewhere between reads as a solar system.
     * @param spinDegrees turns the whole thing about the Sun, so the
     *   opening shot can drift rather than sit still.
     */
    fun project(
        position: Astronomy.Vector,
        size: Size,
        scalePx: Float,
        tiltDegrees: Float,
        spinDegrees: Float,
        centre: Offset,
    ): Placement {
        val spin = Math.toRadians(spinDegrees.toDouble())
        val x = position.x * cos(spin) - position.y * sin(spin)
        val y = position.x * sin(spin) + position.y * cos(spin)

        val tilt = Math.toRadians(tiltDegrees.toDouble())
        val screenX = compress(x)
        // Foreshortened: the far side of the orbit rides up the screen.
        val screenY = compress(y) * cos(tilt) - compress(position.z) * sin(tilt)
        val depth = (compress(y) * sin(tilt)).toFloat()

        return Placement(
            at = Offset(
                centre.x + (screenX * scalePx).toFloat(),
                centre.y - (screenY * scalePx).toFloat(),
            ),
            radiusPx = scalePx,
            depth = depth,
        )
    }

    /**
     * Squashes a distance in astronomical units into something drawable.
     *
     * Signed, so it can be applied to a coordinate rather than only to a
     * radius: the compression has to be the same function on both axes or
     * the circles stop being circles.
     */
    private fun compress(au: Double): Double {
        val magnitude = kotlin.math.abs(au)
        if (magnitude < 1e-9) return 0.0
        val squashed = (magnitude / OUTER_AU).pow(COMPRESSION)
        return if (au < 0) -squashed else squashed
    }

    /** How big to draw each body, as a fraction of the screen's short side. */
    fun sizeOf(planet: Planet): Float = when (planet) {
        Planet.SUN -> 0.046f
        Planet.JUPITER -> 0.052f
        Planet.SATURN -> 0.048f
        Planet.URANUS -> 0.036f
        Planet.NEPTUNE -> 0.035f
        Planet.EARTH -> 0.029f
        Planet.VENUS -> 0.028f
        Planet.MARS -> 0.024f
        Planet.MERCURY -> 0.020f
        Planet.MOON -> 0.012f
    }

    /**
     * The orbit rings.
     *
     * Drawn as ellipses rather than computed from the ellipse of each
     * orbit: at this compression the difference between a real orbit and a
     * circle is a pixel or two, and a ring that the planet sits exactly on
     * is worth more than one that is technically eccentric.
     */
    fun DrawScope.drawOrbits(
        epochMillis: Long,
        scalePx: Float,
        tiltDegrees: Float,
        centre: Offset,
        alpha: Float,
    ) {
        if (alpha <= 0.01f) return
        val squash = cos(Math.toRadians(tiltDegrees.toDouble())).toFloat()
        for (planet in BODIES) {
            if (planet == Planet.MOON) continue
            val au = Astronomy.heliocentricDistanceAu(planet, epochMillis)
            val radius = (compress(au) * scalePx).toFloat()
            if (radius < 4f) continue
            drawOval(
                color = Color(0xFF6E86B8).copy(alpha = 0.22f * alpha),
                topLeft = Offset(centre.x - radius, centre.y - radius * squash),
                size = Size(radius * 2f, radius * 2f * squash),
                style = Stroke(width = 1.2f * density),
            )
        }
    }

    /** The Sun's light, filling the middle of the plane. */
    fun DrawScope.drawSunGlow(centre: Offset, radiusPx: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        drawCircle(
            brush = Brush.radialGradient(
                // Faint and wide. A bright middle came out as a flat
                // orange disc sitting on the plane rather than as light
                // coming off the Sun.
                colors = listOf(
                    Color(0xFFFFD79A).copy(alpha = 0.16f * alpha),
                    Color(0xFFFF9A2E).copy(alpha = 0.07f * alpha),
                    Color.Transparent,
                ),
                center = centre,
                radius = radiusPx,
            ),
            radius = radiusPx,
            center = centre,
        )
    }

    /** Neptune's orbit, which is what the whole picture is scaled to. */
    private const val OUTER_AU = 30.2

    /**
     * The power the radii are squashed by.
     *
     * At 0.42 the four inner planets all landed inside the Sun's own disc,
     * which is the thing a compressed orrery is supposed to prevent. A
     * third spreads them out to where they can be told apart and still
     * leaves Neptune on the rim.
     */
    private const val COMPRESSION = 0.34
}

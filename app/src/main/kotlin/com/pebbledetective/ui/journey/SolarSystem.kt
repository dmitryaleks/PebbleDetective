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
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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
     * Where a body goes on screen, how big its disc is, and how far away.
     *
     * This is the whole of the orrery's geometry, including the one lie in
     * the picture, and both the journey's opening shot and the planetarium
     * call it - a second copy would be two orreries that slowly drifted
     * apart.
     *
     * @param discScale multiplies every disc. The journey draws them at a
     *   fixed size; the planetarium grows them as it zooms in, or zooming
     *   would spread the orbits out around planets that stayed the same
     *   handful of pixels across.
     */
    fun DrawScope.placeBody(
        planet: Planet,
        epochMillis: Long,
        scalePx: Float,
        tiltDegrees: Float,
        spinDegrees: Float,
        centre: Offset,
        discScale: Float = 1f,
    ): Placed {
        val projected = project(
            Astronomy.heliocentricEcliptic(planet, epochMillis),
            size, scalePx, tiltDegrees, spinDegrees, centre,
        )
        val radius = size.minDimension * sizeOf(planet) * discScale
        if (planet != Planet.MOON) return Placed(projected.at, radius, projected.depth)

        // Pushed away from the Sun by a couple of Earth-widths, or it sits
        // underneath the Earth and is never seen at all.
        val earth = project(
            Astronomy.heliocentricEcliptic(Planet.EARTH, epochMillis),
            size, scalePx, tiltDegrees, spinDegrees, centre,
        ).at
        val away = Offset(earth.x - centre.x, earth.y - centre.y)
        val length = hypot(away.x, away.y).coerceAtLeast(1f)
        val step = size.minDimension * sizeOf(Planet.EARTH) * discScale * MOON_NUDGE
        return Placed(
            at = Offset(earth.x + away.x / length * step, earth.y + away.y / length * step),
            radius = radius,
            depth = projected.depth,
        )
    }

    /**
     * How far out a body is drawn, as a fraction of the picture's radius.
     *
     * Neptune is 1 by construction. Mercury is a thirtieth of its distance
     * but a fifth of the way out, which is the compression doing its job -
     * and it is what the planetarium needs to decide how far to zoom in
     * when it is asked to focus on one world.
     */
    fun orbitFraction(planet: Planet, epochMillis: Long): Float = when (planet) {
        // The Sun is at the middle, whatever the orbital elements say: it
        // borrows the Earth's for the sake of having some.
        Planet.SUN -> 0f
        else -> compress(Astronomy.semiMajorAxisAu(planet, epochMillis)).toFloat()
    }

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
        val z = position.z

        // Pulled in along the line from the Sun, so only the distance is
        // squashed and the direction survives untouched.
        //
        // The first version squashed each coordinate on its own, which is
        // not the same thing at all and was plainly wrong once the clock
        // started running: a circular orbit came out as a rounded square,
        // so a planet wandered a tenth of its orbit's width on and off
        // the ring four times a year - and worse, the power law has an
        // infinite slope at zero, so every time a coordinate crossed an
        // axis the planet leapt sideways. Compressing the radius has
        // neither problem: circles stay circles, concentric and smooth.
        val radius = sqrt(x * x + y * y + z * z)
        val squash = if (radius < 1e-9) 0.0 else compress(radius) / radius

        val tilt = Math.toRadians(tiltDegrees.toDouble())
        val screenX = x * squash
        // Foreshortened: the far side of the orbit rides up the screen.
        val screenY = y * squash * cos(tilt) - z * squash * sin(tilt)
        val depth = (y * squash * sin(tilt)).toFloat()

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
     * Squashes a distance in astronomical units into a fraction of the
     * picture's radius. Neptune's orbit comes out at 1.
     */
    private fun compress(au: Double): Double {
        if (au < 1e-9) return 0.0
        return (au / OUTER_AU).pow(COMPRESSION)
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
     * Circles rather than the true ellipses: at this compression the
     * difference is a few pixels for every planet but Mercury, and a ring
     * that holds still is worth more than one that is technically
     * eccentric.
     *
     * Each is sized from the orbit's own long half axis and not from
     * where the planet happens to be today. Today's distance was the
     * first attempt, and it meant Mercury's ring swelled and shrank by a
     * seventh every eighty-eight days - a still picture could not show
     * it, and the planetarium, which can run at twenty days a second,
     * made the whole system look like it was breathing.
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
            val au = Astronomy.semiMajorAxisAu(planet, epochMillis)
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

/**
 * A body ready to draw: where it is, how big, and how far off.
 *
 * Shared by the orrery and by the crossing that follows it, because the
 * camera move between them interpolates one into the other.
 */
data class Placed(val at: Offset, val radius: Float, val depth: Float)

/** Part of the way from one placement to another. */
fun lerp(from: Placed, to: Placed, t: Float) = Placed(
    at = Offset(
        from.at.x + (to.at.x - from.at.x) * t,
        from.at.y + (to.at.y - from.at.y) * t,
    ),
    radius = from.radius + (to.radius - from.radius) * t,
    depth = from.depth + (to.depth - from.depth) * t,
)

package com.pebbledetective.ui.journey

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.pebbledetective.domain.Astronomy
import com.pebbledetective.domain.Orrery
import com.pebbledetective.domain.Planet
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of the orrery, which only showed itself once time could run.
 *
 * The compression squashes the distances so that Mercury and Neptune can
 * share a screen. Done to each coordinate separately it is not a
 * compression at all but a warp of the plane, and the planetarium made
 * that obvious: circles came out as rounded squares and planets jumped
 * whenever they crossed an axis. These are the tests that pin it down.
 */
class OrreryGeometryTest {

    private val size = Size(1080f, 2400f)
    private val centre = Offset(0f, 0f)
    private val scale = 500f

    private fun project(x: Double, y: Double, z: Double = 0.0, tilt: Float = 0f) =
        SolarSystem.project(
            position = Astronomy.Vector(x, y, z),
            size = size,
            scalePx = scale,
            tiltDegrees = tilt,
            spinDegrees = 0f,
            centre = centre,
        ).at

    private fun radiusOf(at: Offset) = hypot(at.x, at.y)

    /**
     * A circular orbit has to come out as a circle.
     *
     * Compressing x and y apart put the diagonals twelve percent further
     * out than the axes, which is a planet visibly leaving its own ring
     * and rejoining it four times a year.
     */
    @Test
    fun `a circle stays a circle`() {
        val reference = radiusOf(project(1.0, 0.0))
        for (degrees in 0 until 360 step 5) {
            val radians = Math.toRadians(degrees.toDouble())
            val at = project(cos(radians), sin(radians))
            assertEquals(
                "out of round at $degrees degrees",
                reference.toDouble(),
                radiusOf(at).toDouble(),
                reference * 0.001,
            )
        }
    }

    /**
     * And nothing may leap as it crosses an axis.
     *
     * The power law has an infinite slope at zero, so a coordinate
     * passing through it moved tens of pixels for a hundredth of an
     * astronomical unit of real motion.
     */
    @Test
    fun `nothing jumps as it crosses an axis`() {
        var previous: Offset? = null
        var longest = 0f
        var shortest = Float.MAX_VALUE
        // A degree at a time across the positive y axis, where x changes
        // sign. Every step covers the same arc, so every step should
        // cover about the same distance on screen.
        for (tenths in 850..950) {
            val radians = Math.toRadians(tenths / 10.0)
            val at = project(cos(radians), sin(radians))
            previous?.let {
                val step = hypot(at.x - it.x, at.y - it.y)
                longest = maxOf(longest, step)
                shortest = minOf(shortest, step)
            }
            previous = at
        }
        assertTrue(
            "steps ranged from $shortest to $longest pixels",
            longest < shortest * 1.2f,
        )
    }

    /** Further out is further out, however hard the distances are squashed. */
    @Test
    fun `the order of the orbits is kept`() {
        val epoch = 1_767_225_600_000L
        val radii = SolarSystem.BODIES
            .filter { it != Planet.MOON }
            .map { planet ->
                planet to SolarSystem.orbitFraction(planet, epoch)
            }
        for ((before, after) in radii.zipWithNext()) {
            assertTrue(
                "${after.first} was drawn inside ${before.first}",
                after.second > before.second,
            )
        }
        // And Neptune is what the picture is scaled to.
        assertEquals(1.0f, radii.last().second, 0.02f)
    }

    /**
     * A planet sits on its own ring.
     *
     * The ring comes off the orbit and the planet off its position, so
     * an eccentric one crosses in and out - but only by its
     * eccentricity, not by the shape of the projection.
     */
    @Test
    fun `each planet is drawn on its own ring`() {
        val epoch = 1_767_225_600_000L
        for (planet in listOf(Planet.EARTH, Planet.JUPITER, Planet.NEPTUNE)) {
            val ring = SolarSystem.orbitFraction(planet, epoch) * scale
            val world = Astronomy.heliocentricEcliptic(planet, epoch)
            val drawn = radiusOf(project(world.x, world.y, world.z))
            assertEquals("$planet left its ring", ring.toDouble(), drawn.toDouble(), ring * 0.05)
        }
    }

    /**
     * The rings themselves hold still while time runs.
     *
     * Sized from today's distance instead, Mercury's swelled and shrank
     * by a seventh every eighty-eight days, and at twenty days a second
     * the whole system appeared to breathe.
     */
    @Test
    fun `the rings do not pulse as the planets go round`() {
        val start = 1_767_225_600_000L
        for (planet in listOf(Planet.MERCURY, Planet.MARS, Planet.SATURN)) {
            val first = SolarSystem.orbitFraction(planet, start)
            for (days in 0..400 step 20) {
                val later = SolarSystem.orbitFraction(planet, Orrery.epochAt(start, days.toDouble()))
                assertTrue(
                    "$planet's ring moved by ${abs(later - first)} after $days days",
                    abs(later - first) < first * 0.005f,
                )
            }
        }
    }

    /**
     * And a planet moves smoothly over a whole orbit rather than
     * wandering towards and away from the middle.
     */
    @Test
    fun `Earth holds its distance all the way round`() {
        val start = 1_767_225_600_000L
        val reference = radiusOf(
            Astronomy.heliocentricEcliptic(Planet.EARTH, start).let { project(it.x, it.y, it.z) }
        )
        for (days in 0..365 step 7) {
            val world = Astronomy.heliocentricEcliptic(
                Planet.EARTH,
                Orrery.epochAt(start, days.toDouble()),
            )
            val drawn = radiusOf(project(world.x, world.y, world.z))
            assertEquals(
                "Earth was $drawn from the Sun after $days days, not $reference",
                reference.toDouble(),
                drawn.toDouble(),
                reference * 0.02,
            )
        }
    }

    /**
     * The Moon goes round the Earth, and in a month.
     *
     * Its drawn distance is exaggerated - at this compression the real
     * one is a fraction of a pixel - but its bearing is not, and the
     * first version threw the bearing away too and parked it on the far
     * side of the Earth from the Sun. That looks right in a still and
     * means the Moon never moves, which is exactly how it was found.
     */
    @Test
    fun `the Moon goes round the Earth in a month`() {
        val start = 1_767_225_600_000L
        var swept = 0.0
        var previous: Double? = null
        var shortest = Double.MAX_VALUE
        var longest = 0.0

        // A sidereal month, half a day at a time.
        for (half in 0..55) {
            val offset = SolarSystem.moonOffset(
                epochMillis = Orrery.epochAt(start, half * 0.5),
                earthDiscPx = 30f,
                scalePx = scale,
                tiltDegrees = 0f,
                spinDegrees = 0f,
            ).screenPx
            val reach = hypot(offset.x, offset.y).toDouble()
            shortest = minOf(shortest, reach)
            longest = maxOf(longest, reach)

            val angle = Math.toDegrees(Math.atan2(offset.y.toDouble(), offset.x.toDouble()))
            previous?.let {
                var step = angle - it
                if (step > 180) step -= 360.0
                if (step < -180) step += 360.0
                swept += step
            }
            previous = angle
        }

        assertEquals("a sidereal month is a full turn", 360.0, abs(swept), 25.0)
        // And it keeps its distance: the real orbit is eccentric by about
        // a twentieth, and nothing in the drawing may add to that.
        assertTrue(
            "the Moon's drawn orbit ran from $shortest to $longest",
            longest < shortest * 1.15,
        )
    }

    /** It is drawn clear of the Earth, which is the whole point of the lie. */
    @Test
    fun `the Moon is drawn clear of the Earth`() {
        val offset = SolarSystem.moonOffset(
            epochMillis = 1_767_225_600_000L,
            earthDiscPx = 30f,
            scalePx = scale,
            tiltDegrees = 55f,
            spinDegrees = -28f,
        ).screenPx
        val reach = hypot(offset.x, offset.y)
        assertTrue("the Moon sat on top of the Earth at $reach px", reach > 30f)
    }
}

package com.pebbledetective.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Halley, which is where a stone goes when a comet drops it.
 *
 * The same six numbers and the same Kepler solver as the planets, but
 * an orbit nothing like theirs: ninety-seven percent eccentric, tipped
 * past vertical and travelled backwards. These check it against the
 * things everyone knows about Halley rather than against a table.
 */
class HalleyTest {

    private fun at(year: Int, month: Int, day: Int): Long =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    private fun distance(epochMillis: Long) =
        Astronomy.heliocentricDistanceAu(Planet.HALLEY, epochMillis)

    /**
     * February 1986: the apparition a generation remembers, and the one
     * the elements are fitted to. Perihelion is 0.586 AU, inside the
     * orbit of Venus.
     */
    @Test
    fun `it rounds the Sun in early 1986`() {
        assertEquals(0.586, distance(at(1986, 2, 9)), 0.02)
    }

    /**
     * And out again. Aphelion came at the end of 2023 at just over
     * thirty-five astronomical units - beyond Neptune, which is why the
     * planetarium today shows it as a speck at the rim.
     */
    @Test
    fun `it is out past Neptune now`() {
        val aphelion = distance(at(2023, 12, 9))
        assertEquals(35.1, aphelion, 0.3)
        assertTrue(
            "Halley should still be outside Neptune in 2026",
            distance(at(2026, 10, 1)) > Astronomy.semiMajorAxisAu(Planet.NEPTUNE, at(2026, 10, 1)),
        )
    }

    /**
     * Seventy-five years and a bit, which is the whole of its fame.
     *
     * Checked by looking for the closest approach either side of each
     * date rather than on the day: a few weeks of error is nothing in a
     * seventy-five year orbit, but near perihelion the comet covers
     * most of an astronomical unit in a month, so a test pinned to the
     * day would be testing the calendar rather than the orbit.
     */
    @Test
    fun `it comes back in a human lifetime`() {
        fun closestNear(epochMillis: Long): Double =
            (-80..80).minOf { distance(epochMillis + it * 5L * 86_400_000L) }

        assertEquals(0.586, closestNear(at(1986, 2, 9)), 0.02)
        assertEquals(0.586, closestNear(at(2061, 7, 28)), 0.02)
        // And it is nowhere near the Sun halfway between.
        assertTrue(distance(at(2023, 12, 9)) > 30.0)
    }

    /** Retrograde and steeply tipped: it does not lie in the plane. */
    @Test
    fun `it does not travel with the planets`() {
        val summer = Astronomy.heliocentricEcliptic(Planet.HALLEY, at(1986, 2, 9))
        // At perihelion it is well below the plane the planets share.
        assertTrue("z was ${summer.z}", abs(summer.z) > 0.1)
    }

    /**
     * No colour can produce it. A stone is from Halley because a child
     * watched a comet drop one, and for no other reason.
     */
    @Test
    fun `a colour can never choose it`() {
        assertFalse(Planet.HALLEY in Planet.sources)
        assertFalse(Planet.EARTH in Planet.sources)
        assertEquals(9, Planet.sources.size)
        val random = kotlin.random.Random(7)
        repeat(500) {
            assertTrue(Planet.sources.random(random) != Planet.HALLEY)
        }
    }

    /** Nor is it one of the things the sky mode points at. */
    @Test
    fun `it is not in the sky mode`() {
        assertFalse(Planet.HALLEY in Astronomy.VISIBLE_BODIES)
    }

    /**
     * The tail grows as it comes in. Not to nothing at the far end: a
     * bare dot at the rim of an orrery is a speck of dust on the glass.
     */
    @Test
    fun `the tail grows as it nears the Sun`() {
        assertEquals(1f, com.pebbledetective.ui.sky.cometTailScale(0.6), 0.001f)
        assertEquals(1f, com.pebbledetective.ui.sky.cometTailScale(1.0), 0.001f)
        assertTrue(com.pebbledetective.ui.sky.cometTailScale(5.0) < 1f)
        assertTrue(com.pebbledetective.ui.sky.cometTailScale(5.0) > 0.35f)
        assertEquals(0.35f, com.pebbledetective.ui.sky.cometTailScale(35.0), 0.001f)
    }

    /** It is on the map of the solar system, and has no ring drawn for it. */
    @Test
    fun `the orrery carries it`() {
        assertTrue(Planet.HALLEY in com.pebbledetective.ui.journey.SolarSystem.BODIES)
    }

    /** A sanity check that the whole thing is not standing still. */
    @Test
    fun `it moves fastest when it is closest`() {
        fun step(from: Long) = abs(
            Astronomy.heliocentricEcliptic(Planet.HALLEY, from + 30L * 86_400_000L).let { later ->
                val now = Astronomy.heliocentricEcliptic(Planet.HALLEY, from)
                (later - now).length
            }
        )
        val nearTheSun = step(at(1986, 2, 9))
        val nearAphelion = step(at(2023, 12, 9))
        assertTrue(
            "a month near perihelion moved $nearTheSun, near aphelion $nearAphelion",
            nearTheSun > nearAphelion * 10,
        )
    }

    @Test
    fun `instants are what they say they are`() {
        // Guards the helper above, which every other test leans on.
        assertEquals(
            "1986-02-09T00:00:00Z",
            Instant.ofEpochMilli(at(1986, 2, 9)).toString(),
        )
    }
}

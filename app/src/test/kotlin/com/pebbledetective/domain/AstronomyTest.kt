package com.pebbledetective.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The planet positions, checked against things that are true of the solar
 * system rather than against a table of numbers copied from somewhere.
 *
 * An error in the elements, in Kepler's equation or in either coordinate
 * transform shows up as one of these invariants failing, and each failure
 * points at a different part of the calculation.
 */
class AstronomyTest {

    private fun utc(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

    // ---- the Sun, which we know the most about --------------------------

    /**
     * The seasons. If the ecliptic-to-equatorial tilt were wrong or the
     * Earth's elements were off, this is what would break first.
     */
    @Test
    fun `the sun crosses the equator at the equinoxes and turns at the solstices`() {
        val marchEquinox = sun(utc(2026, 3, 20, 14, 46)).declinationDegrees
        assertTrue("March equinox declination was $marchEquinox", kotlin.math.abs(marchEquinox) < 0.3)

        val septemberEquinox = sun(utc(2026, 9, 23, 0, 5)).declinationDegrees
        assertTrue(
            "September equinox declination was $septemberEquinox",
            kotlin.math.abs(septemberEquinox) < 0.3,
        )

        val juneSolstice = sun(utc(2026, 6, 21, 8, 24)).declinationDegrees
        assertTrue("June solstice declination was $juneSolstice", juneSolstice in 23.3..23.5)

        val decemberSolstice = sun(utc(2026, 12, 21, 20, 50)).declinationDegrees
        assertTrue(
            "December solstice declination was $decemberSolstice",
            decemberSolstice in -23.5..-23.3,
        )
    }

    /**
     * At noon UTC on the Greenwich meridian the Sun is on the meridian, give
     * or take the equation of time. This is the test that catches a sign
     * error in sidereal time or in the azimuth convention - both of which
     * would leave the declinations above perfectly correct.
     */
    @Test
    fun `at noon on the greenwich meridian the sun is due south`() {
        for (month in 1..12) {
            val sighting = Astronomy.sight(Planet.SUN, utc(2026, month, 15, 12, 0), 51.48, 0.0)
            val offBySouth = Astronomy.separationDegrees(180.0, sighting.azimuthDegrees)
            assertTrue(
                "month $month put the midday sun at azimuth ${sighting.azimuthDegrees}",
                kotlin.math.abs(offBySouth) < 5.0,
            )
            assertTrue("the midday sun was below the horizon in month $month", sighting.isUp)
        }
    }

    /** And at midnight it is underfoot, which the same error would hide. */
    @Test
    fun `at midnight on the greenwich meridian the sun is below the horizon`() {
        for (month in 1..12) {
            val sighting = Astronomy.sight(Planet.SUN, utc(2026, month, 15, 0, 0), 51.48, 0.0)
            assertTrue(
                "month $month put the midnight sun at ${sighting.altitudeDegrees} degrees",
                sighting.altitudeDegrees < -10.0,
            )
        }
    }

    /** Overhead at the equator at noon on the equinox, as close as makes no odds. */
    @Test
    fun `the equinox sun stands overhead at the equator`() {
        // Noon local time at longitude 0 on the day of the March equinox.
        val sighting = Astronomy.sight(Planet.SUN, utc(2026, 3, 20, 12, 7), 0.0, 0.0)
        assertTrue(
            "the sun was only ${sighting.altitudeDegrees} degrees up",
            sighting.altitudeDegrees > 89.0,
        )
    }

    /** Midsummer inside the Arctic circle: the sun does not set. */
    @Test
    fun `the midnight sun does not set above the arctic circle`() {
        val latitude = 78.2 // Longyearbyen.
        for (hour in 0..23) {
            val sighting = Astronomy.sight(Planet.SUN, utc(2026, 6, 21, hour, 0), latitude, 15.6)
            assertTrue(
                "the sun set at ${hour}:00 (altitude ${sighting.altitudeDegrees})",
                sighting.isUp,
            )
        }
    }

    // ---- the orbits -----------------------------------------------------

    /**
     * Mercury and Venus are inside the Earth's orbit, so they can never
     * appear far from the Sun. The bounds are a property of the orbits and
     * cannot be satisfied by an implementation that has them in the wrong
     * place.
     */
    @Test
    fun `the inner planets never stray far from the sun`() {
        var mercuryMax = 0.0
        var venusMax = 0.0
        // Every five days for a decade, which covers many of both cycles.
        for (step in 0 until 730) {
            val at = utc(2026, 1, 1) + step * 5L * 86_400_000L
            mercuryMax = maxOf(mercuryMax, Astronomy.elongationDegrees(Planet.MERCURY, at))
            venusMax = maxOf(venusMax, Astronomy.elongationDegrees(Planet.VENUS, at))
        }
        assertTrue("Mercury reached $mercuryMax degrees from the sun", mercuryMax in 17.0..29.0)
        assertTrue("Venus reached $venusMax degrees from the sun", venusMax in 44.0..48.5)
    }

    /**
     * Each orbit stays between its perihelion and aphelion. A wrong
     * semi-major axis, eccentricity or Kepler solution all land here.
     */
    @Test
    fun `every orbit keeps to its own known range`() {
        val expected = mapOf(
            Planet.MERCURY to (0.306 to 0.468),
            Planet.VENUS to (0.717 to 0.729),
            Planet.EARTH to (0.982 to 1.018),
            Planet.MARS to (1.380 to 1.668),
            Planet.JUPITER to (4.949 to 5.459),
            Planet.SATURN to (8.99 to 10.13),
            Planet.URANUS to (18.27 to 20.10),
            Planet.NEPTUNE to (29.78 to 30.34),
        )
        for ((planet, range) in expected) {
            var low = Double.MAX_VALUE
            var high = 0.0
            for (step in 0 until 400) {
                val at = utc(2026, 1, 1) + step * 20L * 86_400_000L
                val distance = Astronomy.heliocentricDistanceAu(planet, at)
                low = minOf(low, distance)
                high = maxOf(high, distance)
            }
            assertTrue("$planet came in to $low au", low >= range.first)
            assertTrue("$planet went out to $high au", high <= range.second)
        }
    }

    /** The Earth is closest to the Sun in early January, not in July. */
    @Test
    fun `perihelion falls in january`() {
        var closestDay = -1
        var closest = Double.MAX_VALUE
        for (day in 0 until 365) {
            val at = utc(2027, 1, 1) + day * 86_400_000L
            val distance = Astronomy.heliocentricDistanceAu(Planet.EARTH, at)
            if (distance < closest) {
                closest = distance
                closestDay = day
            }
        }
        val date = LocalDate.ofInstant(
            Instant.ofEpochMilli(utc(2027, 1, 1) + closestDay * 86_400_000L),
            ZoneOffset.UTC,
        )
        assertEquals("perihelion landed on $date", 1, date.monthValue)
        assertTrue("perihelion landed on $date", date.dayOfMonth <= 7)
    }

    /**
     * Each outer planet comes to opposition on its own well known cycle -
     * Jupiter every 399 days, Saturn every 378, and so on as the Earth laps
     * them. This is what would catch a typo in a single body's mean
     * longitude or its rate, which the bounds above would happily pass.
     */
    @Test
    fun `the outer planets reach opposition on their known cycles`() {
        val synodicDays = mapOf(
            Planet.MARS to 779.9,
            Planet.JUPITER to 398.9,
            Planet.SATURN to 378.1,
            Planet.URANUS to 369.7,
            Planet.NEPTUNE to 367.5,
        )
        val start = utc(2026, 1, 1)
        for ((planet, expected) in synodicDays) {
            val oppositions = mutableListOf<Int>()
            var previous = Astronomy.elongationDegrees(planet, start)
            var rising = true
            for (day in 1 until 7_300) {
                val now = Astronomy.elongationDegrees(planet, start + day * 86_400_000L)
                // The turning point as the elongation stops growing is
                // opposition; the planet is then opposite the sun.
                if (rising && now < previous && previous > 170.0) {
                    oppositions += day
                    rising = false
                } else if (!rising && now > previous) {
                    rising = true
                }
                previous = now
            }
            assertTrue("$planet had only ${oppositions.size} oppositions", oppositions.size >= 4)
            val gaps = oppositions.zipWithNext { a, b -> (b - a).toDouble() }
            val mean = gaps.average()
            assertEquals("$planet opposition cycle", expected, mean, 3.0)
        }
    }

    // ---- the horizon transform ------------------------------------------

    /**
     * At the pole the sky turns about the zenith, so a body's altitude is
     * just its declination and never changes through the day. Nothing else
     * tests the latitude term this cleanly.
     */
    @Test
    fun `at the north pole altitude equals declination all day`() {
        for (hour in 0..23 step 3) {
            val sighting = Astronomy.sight(Planet.MARS, utc(2026, 5, 4, hour, 0), 90.0, 0.0)
            assertEquals(
                "at ${hour}:00",
                sighting.declinationDegrees,
                sighting.altitudeDegrees,
                0.001,
            )
        }
    }

    /** And nothing with southern declination is ever visible from there. */
    @Test
    fun `the north pole never sees the southern sky`() {
        for (day in 0 until 365 step 7) {
            val at = utc(2026, 1, 1) + day * 86_400_000L
            for (sighting in Astronomy.sky(at, 90.0, 0.0)) {
                if (sighting.declinationDegrees < 0) {
                    assertTrue(
                        "${sighting.planet} was up with declination ${sighting.declinationDegrees}",
                        !sighting.isUp,
                    )
                }
            }
        }
    }

    /** An object on the meridian is due south from the north, due north from the south. */
    @Test
    fun `the azimuth convention is north based and clockwise`() {
        // Hour angle zero puts the body on the meridian.
        val (_, northern) = Astronomy.horizon(0.0, 0.0, 51.0)
        assertEquals(180.0, northern, 0.001)

        val (_, southern) = Astronomy.horizon(0.0, 0.0, -34.0)
        assertEquals(0.0, Astronomy.wrapHalf(southern), 0.001)

        // Six hours before transit a body on the equator is in the east.
        val (_, rising) = Astronomy.horizon(-90.0, 0.0, 51.0)
        assertEquals(90.0, rising, 0.001)

        // Six hours after, in the west.
        val (_, setting) = Astronomy.horizon(90.0, 0.0, 51.0)
        assertEquals(270.0, setting, 0.001)
    }

    // ---- the thing the feature actually promises ------------------------

    /**
     * The point of working the positions out rather than shipping a table:
     * it keeps answering, with no data and no network, for as long as the
     * fitted elements are good for.
     */
    @Test
    fun `it still works decades from now`() {
        for (year in 2026..Astronomy.LAST_YEAR step 4) {
            val sky = Astronomy.sky(utc(year, 7, 1, 22, 0), 35.67, 139.80)
            assertEquals(8, sky.size)
            for (sighting in sky) {
                assertTrue(
                    "$year produced a nonsense altitude for ${sighting.planet}",
                    sighting.altitudeDegrees in -90.0..90.0,
                )
                assertTrue(
                    "$year produced a nonsense azimuth for ${sighting.planet}",
                    sighting.azimuthDegrees in 0.0..360.0,
                )
                assertTrue(
                    "$year lost ${sighting.planet} entirely",
                    sighting.distanceAu > 0.0 && sighting.distanceAu < 35.0,
                )
            }
        }
    }

    /** Earth is where you are standing, so it is not in the list. */
    @Test
    fun `earth is not one of the bodies you can look at`() {
        assertTrue(Planet.EARTH !in Astronomy.VISIBLE_BODIES)
        assertEquals(8, Astronomy.VISIBLE_BODIES.size)
    }

    /** Positions move smoothly; no wrap in the solver may jump a planet. */
    @Test
    fun `nothing teleports between one minute and the next`() {
        for (planet in Astronomy.VISIBLE_BODIES) {
            var previous: Astronomy.Sighting? = null
            for (step in 0 until 240) {
                val at = utc(2026, 11, 2) + step * 60_000L
                val now = Astronomy.sight(planet, at, 35.67, 139.80)
                previous?.let {
                    val moved = kotlin.math.abs(
                        Astronomy.separationDegrees(
                            it.rightAscensionDegrees,
                            now.rightAscensionDegrees,
                        )
                    )
                    assertTrue("$planet jumped $moved degrees in a minute", moved < 0.1)
                }
                previous = now
            }
        }
    }

    @Test
    fun `compass points round to the nearest eighth`() {
        assertEquals(0, Astronomy.compassPoint(0.0))
        assertEquals(0, Astronomy.compassPoint(359.0))
        assertEquals(1, Astronomy.compassPoint(45.0))
        assertEquals(2, Astronomy.compassPoint(92.0))
        assertEquals(4, Astronomy.compassPoint(181.0))
        assertEquals(7, Astronomy.compassPoint(315.0))
    }

    private fun sun(epochMillis: Long) = Astronomy.sight(Planet.SUN, epochMillis, 0.0, 0.0)
}

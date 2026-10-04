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

    // ---- the Moon, which obeys none of the above ------------------------

    /**
     * Perigee and apogee. A wrong parallax term, or the Earth radius in
     * the wrong units, lands here first and lands hard.
     *
     * These are the topocentric bounds, because that is what a sighting
     * reports: the observer is up to an Earth radius nearer or further
     * than the centre of the planet is.
     */
    @Test
    fun `the moon keeps to its own distance`() {
        var low = Double.MAX_VALUE
        var high = 0.0
        for (hour in 0 until 24 * 400 step 7) {
            val at = utc(2026, 1, 1) + hour * 3_600_000L
            val km = Astronomy.sight(Planet.MOON, at, 35.67, 139.80).distanceAu * AU_KM
            low = minOf(low, km)
            high = maxOf(high, km)
        }
        assertTrue("the moon came in to %.0f km".format(low), low in 348_000.0..360_000.0)
        assertTrue("the moon went out to %.0f km".format(high), high in 403_000.0..415_000.0)
    }

    /** Its orbit is tilted about five degrees to the ecliptic, and no more. */
    @Test
    fun `the moon stays near the ecliptic`() {
        var worst = 0.0
        for (day in 0 until 2_000) {
            val at = utc(2026, 1, 1) + day * 86_400_000L
            // Declination minus the obliquity bounds the ecliptic latitude.
            val declination = Astronomy.sight(Planet.MOON, at, 0.0, 0.0).declinationDegrees
            worst = maxOf(worst, kotlin.math.abs(declination))
        }
        // 23.44 of obliquity plus 5.15 of orbital tilt, and never more.
        assertTrue("the moon reached declination $worst", worst in 18.0..29.0)
    }

    /**
     * The synodic month: new moon to new moon, 29.53 days. This is the
     * test that pins the periodic terms rather than just the mean rate -
     * the Moon returns to the Sun on a different cycle from the one it
     * returns to the stars on, and only a theory with the evection and
     * the equation of the centre in it gets both.
     */
    @Test
    fun `new moons come round every twenty nine and a half days`() {
        val start = utc(2026, 1, 1)
        val newMoons = mutableListOf<Int>()
        var previous = Astronomy.elongationDegrees(Planet.MOON, start)
        var falling = true
        for (hour in 1 until 24 * 1_100) {
            val now = Astronomy.elongationDegrees(Planet.MOON, start + hour * 3_600_000L)
            if (falling && now > previous && previous < 20.0) {
                newMoons += hour
                falling = false
            } else if (!falling && now < previous) {
                falling = true
            }
            previous = now
        }
        assertTrue("only ${newMoons.size} new moons", newMoons.size >= 30)
        val gaps = newMoons.zipWithNext { a, b -> (b - a) / 24.0 }
        assertEquals("synodic month", 29.53, gaps.average(), 0.08)
    }

    /** And the sidereal month: back to the same stars in 27.32 days. */
    @Test
    fun `the moon returns to the same stars in a sidereal month`() {
        for (step in 0 until 12) {
            val at = utc(2026, 2, 1) + step * 40L * 86_400_000L
            val before = Astronomy.sight(Planet.MOON, at, 0.0, 0.0)
            val after = Astronomy.sight(
                Planet.MOON, at + (27.321582 * 86_400_000L).toLong(), 0.0, 0.0,
            )
            val moved = kotlin.math.abs(
                Astronomy.separationDegrees(
                    before.rightAscensionDegrees, after.rightAscensionDegrees,
                )
            )
            assertTrue("a sidereal month later it was $moved degrees away", moved < 8.0)
        }
    }

    /** It is full when it is opposite the Sun, and that happens monthly. */
    @Test
    fun `the moon reaches opposition and conjunction every month`() {
        var closest = 180.0
        var furthest = 0.0
        for (hour in 0 until 24 * 40) {
            val at = utc(2026, 5, 1) + hour * 3_600_000L
            val elongation = Astronomy.elongationDegrees(Planet.MOON, at)
            closest = minOf(closest, elongation)
            furthest = maxOf(furthest, elongation)
        }
        assertTrue("never got near the sun: $closest", closest < 8.0)
        assertTrue("never got opposite the sun: $furthest", furthest > 172.0)
    }

    /**
     * Standing somewhere else moves the Moon. A degree is two of its own
     * diameters, so this is the difference between a label on it and a
     * label beside it - and it is the only body here for which the
     * correction is worth making.
     */
    @Test
    fun `where you stand moves the moon and not the planets`() {
        val at = utc(2026, 7, 14, 21, 0)
        fun apart(planet: Planet): Double {
            val north = Astronomy.sight(planet, at, 60.0, 0.0)
            val south = Astronomy.sight(planet, at, -60.0, 0.0)
            // Same instant and meridian, so only parallax separates these.
            return kotlin.math.abs(north.declinationDegrees - south.declinationDegrees)
        }
        assertTrue("the moon shifted by only ${apart(Planet.MOON)}", apart(Planet.MOON) > 0.7)
        assertTrue("mars shifted by ${apart(Planet.MARS)}", apart(Planet.MARS) < 0.02)
    }

    /**
     * Solar eclipses, which are the only absolute check there is on a
     * lunar theory.
     *
     * Every other test here would pass with a constant error bolted on to
     * the Moon's longitude: the months would still be the right length and
     * the distance would still be right. An eclipse pins the epoch, the
     * mean rate and the position of the node all at once, because the Moon
     * has to be in front of the Sun on that particular morning and on no
     * other. These four are published, each one total or annular, so at
     * each of them the two discs really do overlap.
     */
    @Test
    fun `the moon covers the sun on the days it is known to`() {
        val eclipses = listOf(
            Triple(2026, 8, 12),   // total, Spain and Iceland
            Triple(2027, 8, 2),    // total, Egypt
            Triple(2026, 2, 17),   // annular, Antarctica
            Triple(2028, 7, 22),   // total, Australia
        )
        for ((year, month, day) in eclipses) {
            val midnight = LocalDateTime.of(year, month, day, 0, 0)
            val from = midnight.minusDays(2).toInstant(ZoneOffset.UTC).toEpochMilli()

            var closest = 999.0
            var closestAt = 0L
            for (minute in 0 until 4 * 24 * 60) {
                val at = from + minute * 60_000L
                val elongation = Astronomy.elongationDegrees(Planet.MOON, at)
                if (elongation < closest) {
                    closest = elongation
                    closestAt = at
                }
            }

            // Under a degree and a half: the two discs are half a degree
            // each, and the theory is good to about a third of one.
            assertTrue(
                "$year-$month-$day: closest approach was %.2f degrees".format(closest),
                closest < 1.5,
            )
            val on = LocalDate.ofInstant(Instant.ofEpochMilli(closestAt), ZoneOffset.UTC)
            assertEquals("the conjunction fell on the wrong day", midnight.toLocalDate(), on)
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
            assertEquals(9, sky.size)
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
        assertTrue(Planet.MOON in Astronomy.VISIBLE_BODIES)
        assertEquals(9, Astronomy.VISIBLE_BODIES.size)
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

    private companion object {
        const val AU_KM = 149_597_870.7
    }
}

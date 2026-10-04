package com.pebbledetective.domain

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where the planets are, worked out from first principles on the phone.
 *
 * This is the "perpetual calendar" behind the sky mode. There is no data
 * file and no network call: every body's orbit is described by six numbers
 * and six rates of change, and a position at any instant falls out of
 * Kepler's equation. The app can therefore tell you where Saturn is in 2041
 * on a phone in flight mode, which is the whole point.
 *
 * The elements are JPL's "Keplerian Elements for Approximate Positions of
 * the Major Planets", the set fitted for 1800 AD to 2050 AD. Accuracy over
 * that span is a few arcminutes for the inner planets and well under a
 * degree for the outer ones - two orders of magnitude better than the phone
 * compass that decides where the labels actually land, so there is nothing
 * to gain from a heavier theory.
 *
 * Deliberately free of Android, so all of it is an ordinary JVM test.
 */
object Astronomy {

    /** The bodies the sky mode can show. Earth is where you are standing. */
    val VISIBLE_BODIES: List<Planet> = listOf(
        Planet.SUN,
        Planet.MOON,
        Planet.MERCURY,
        Planet.VENUS,
        Planet.MARS,
        Planet.JUPITER,
        Planet.SATURN,
        Planet.URANUS,
        Planet.NEPTUNE,
    )

    /** The span the fitted elements are good for. */
    const val FIRST_YEAR = 1800
    const val LAST_YEAR = 2050

    /**
     * Where a body appears from a given place at a given moment.
     *
     * @param altitudeDegrees above the horizon; negative means underfoot.
     * @param azimuthDegrees clockwise from true north.
     * @param distanceAu from the observer, which is what makes Mars a
     *   different brightness in different years.
     */
    data class Sighting(
        val planet: Planet,
        val altitudeDegrees: Double,
        val azimuthDegrees: Double,
        val rightAscensionDegrees: Double,
        val declinationDegrees: Double,
        val distanceAu: Double,
    ) {
        val isUp: Boolean get() = altitudeDegrees > 0.0
    }

    /** A point in space, in astronomical units. */
    data class Vector(val x: Double, val y: Double, val z: Double) {
        val length: Double get() = sqrt(x * x + y * y + z * z)
        operator fun minus(other: Vector) = Vector(x - other.x, y - other.y, z - other.z)
        operator fun unaryMinus() = Vector(-x, -y, -z)
    }

    /** Every visible body, in the order they are listed. */
    fun sky(epochMillis: Long, latitudeDegrees: Double, longitudeDegrees: Double): List<Sighting> =
        VISIBLE_BODIES.map { sight(it, epochMillis, latitudeDegrees, longitudeDegrees) }

    fun sight(
        planet: Planet,
        epochMillis: Long,
        latitudeDegrees: Double,
        longitudeDegrees: Double,
    ): Sighting {
        val julianDay = julianDay(epochMillis)
        val siderealTime = localSiderealTime(julianDay, longitudeDegrees)

        // Measured from where the observer is standing rather than from the
        // centre of the Earth. For the planets this moves nothing - the
        // offset is a ten-thousandth of the distance to Venus - but the
        // Moon is close enough that it is worth up to a degree, which is
        // two of its own diameters and plainly visible to anyone checking.
        val equatorial = eclipticToEquatorial(geocentricEcliptic(planet, julianDay)) -
            observer(siderealTime, latitudeDegrees)
        val distance = equatorial.length

        val rightAscension = normalise(Math.toDegrees(atan2(equatorial.y, equatorial.x)))
        val declination = Math.toDegrees(asin(equatorial.z / distance))

        val hourAngle = siderealTime - rightAscension
        val (altitude, azimuth) = horizon(hourAngle, declination, latitudeDegrees)

        return Sighting(
            planet = planet,
            altitudeDegrees = altitude,
            azimuthDegrees = azimuth,
            rightAscensionDegrees = rightAscension,
            declinationDegrees = declination,
            distanceAu = distance,
        )
    }

    /** How far from the Sun a body appears in the sky, in degrees. */
    fun elongationDegrees(planet: Planet, epochMillis: Long): Double {
        val julianDay = julianDay(epochMillis)
        val body = eclipticToEquatorial(geocentricEcliptic(planet, julianDay))
        val sun = eclipticToEquatorial(geocentricEcliptic(Planet.SUN, julianDay))
        val dot = (body.x * sun.x + body.y * sun.y + body.z * sun.z) / (body.length * sun.length)
        return Math.toDegrees(kotlin.math.acos(dot.coerceIn(-1.0, 1.0)))
    }

    /** Distance from the Sun, which is a fixed property of the orbit. */
    fun heliocentricDistanceAu(planet: Planet, epochMillis: Long): Double =
        heliocentric(elementsFor(planet), julianDay(epochMillis)).length

    // ---- the machinery --------------------------------------------------

    /**
     * Julian day from a Unix timestamp.
     *
     * UTC is used where the theory asks for Terrestrial Time. They differ by
     * about seventy seconds, which moves the Moon appreciably and the
     * planets not at all.
     */
    fun julianDay(epochMillis: Long): Double =
        UNIX_EPOCH_JD + epochMillis / MILLIS_PER_DAY

    /**
     * The body's position relative to the Earth, in the J2000 ecliptic
     * frame.
     *
     * The Sun is the easy case: seen from Earth it lies in exactly the
     * opposite direction to the Earth seen from the Sun.
     */
    private fun geocentricEcliptic(planet: Planet, julianDay: Double): Vector {
        if (planet == Planet.MOON) return moonEcliptic(julianDay)
        val earth = heliocentric(EARTH, julianDay)
        if (planet == Planet.SUN) return -earth
        return heliocentric(elementsFor(planet), julianDay) - earth
    }

    /** Solves the orbit for its position around the Sun at this instant. */
    private fun heliocentric(elements: Elements, julianDay: Double): Vector {
        val centuries = (julianDay - J2000) / DAYS_PER_CENTURY

        val a = elements.a + elements.aRate * centuries
        val e = elements.e + elements.eRate * centuries
        val inclination = elements.inclination + elements.inclinationRate * centuries
        val meanLongitude = elements.meanLongitude + elements.meanLongitudeRate * centuries
        val perihelion = elements.perihelion + elements.perihelionRate * centuries
        val node = elements.node + elements.nodeRate * centuries

        val argument = perihelion - node
        // Wrapped to a half turn either side, which is what keeps Kepler's
        // equation converging in a handful of passes.
        val meanAnomaly = wrapHalf(meanLongitude - perihelion)
        val eccentric = solveKepler(meanAnomaly, e)

        // In the plane of the orbit, with the x axis toward perihelion.
        val planarX = a * (cos(eccentric) - e)
        val planarY = a * sqrt(1.0 - e * e) * sin(eccentric)

        val cosArgument = cos(Math.toRadians(argument))
        val sinArgument = sin(Math.toRadians(argument))
        val cosNode = cos(Math.toRadians(node))
        val sinNode = sin(Math.toRadians(node))
        val cosInclination = cos(Math.toRadians(inclination))
        val sinInclination = sin(Math.toRadians(inclination))

        return Vector(
            x = (cosArgument * cosNode - sinArgument * sinNode * cosInclination) * planarX +
                (-sinArgument * cosNode - cosArgument * sinNode * cosInclination) * planarY,
            y = (cosArgument * sinNode + sinArgument * cosNode * cosInclination) * planarX +
                (-sinArgument * sinNode + cosArgument * cosNode * cosInclination) * planarY,
            z = (sinArgument * sinInclination) * planarX +
                (cosArgument * sinInclination) * planarY,
        )
    }

    /**
     * Kepler's equation, M = E - e sin E, solved by Newton's method.
     *
     * It has no closed form, which is the one genuinely awkward step in the
     * whole calculation. Newton converges in three or four passes at these
     * eccentricities; the iteration cap is there so a pathological input can
     * never hang the render loop rather than because it is ever reached.
     *
     * @return the eccentric anomaly, in radians.
     */
    private fun solveKepler(meanAnomalyDegrees: Double, e: Double): Double {
        val eDegrees = Math.toDegrees(e)
        var eccentric = meanAnomalyDegrees + eDegrees * sin(Math.toRadians(meanAnomalyDegrees))
        repeat(KEPLER_PASSES) {
            val radians = Math.toRadians(eccentric)
            val delta = meanAnomalyDegrees - (eccentric - eDegrees * sin(radians))
            val step = delta / (1.0 - e * cos(radians))
            eccentric += step
            if (abs(step) < KEPLER_TOLERANCE_DEGREES) return Math.toRadians(eccentric)
        }
        return Math.toRadians(eccentric)
    }

    /**
     * Where the Moon is, which is the one body here that is not an ellipse.
     *
     * The Moon is pulled about by the Sun as hard as a Keplerian orbit can
     * stand: fit it with six elements like a planet and it is several
     * degrees out, which for something half a degree wide and hanging in
     * plain sight is not good enough. These are the leading periodic terms
     * from the Astronomical Almanac's low-precision formulae - the
     * equation of the centre, evection, variation and the annual equation
     * in longitude, and the principal terms in latitude and parallax.
     * Good to about a third of a degree, which is well inside what the
     * phone's compass can tell you anyway.
     */
    private fun moonEcliptic(julianDay: Double): Vector {
        val t = (julianDay - J2000) / DAYS_PER_CENTURY

        val longitude = 218.32 + 481267.881 * t +
            6.29 * sinDegrees(135.0 + 477198.87 * t) -
            1.27 * sinDegrees(259.3 - 413335.36 * t) +
            0.66 * sinDegrees(235.7 + 890534.22 * t) +
            0.21 * sinDegrees(269.9 + 954397.74 * t) -
            0.19 * sinDegrees(357.5 + 35999.05 * t) -
            0.11 * sinDegrees(186.6 + 966404.03 * t)

        val latitude = 5.13 * sinDegrees(93.3 + 483202.02 * t) +
            0.28 * sinDegrees(228.2 + 960400.89 * t) -
            0.28 * sinDegrees(318.3 + 6003.15 * t) -
            0.17 * sinDegrees(217.6 - 407332.21 * t)

        // Horizontal parallax, which is how the distance is expressed: the
        // angle the Earth's radius subtends as seen from the Moon.
        val parallax = 0.9508 +
            0.0518 * cosDegrees(135.0 + 477198.87 * t) +
            0.0095 * cosDegrees(259.3 - 413335.38 * t) +
            0.0078 * cosDegrees(235.7 + 890534.22 * t) +
            0.0028 * cosDegrees(269.9 + 954397.70 * t)

        val distance = (1.0 / sin(Math.toRadians(parallax))) * EARTH_RADIUS_AU
        val cosLatitude = cos(Math.toRadians(latitude))
        return Vector(
            x = distance * cosLatitude * cos(Math.toRadians(longitude)),
            y = distance * cosLatitude * sin(Math.toRadians(longitude)),
            z = distance * sin(Math.toRadians(latitude)),
        )
    }

    /**
     * Where the observer is, relative to the centre of the Earth, in the
     * equatorial frame. A sphere is plenty: the flattening moves this by
     * twenty kilometres, which is a thousandth of the Moon's parallax.
     */
    private fun observer(siderealTimeDegrees: Double, latitudeDegrees: Double): Vector {
        val latitude = Math.toRadians(latitudeDegrees)
        val sidereal = Math.toRadians(siderealTimeDegrees)
        return Vector(
            x = EARTH_RADIUS_AU * cos(latitude) * cos(sidereal),
            y = EARTH_RADIUS_AU * cos(latitude) * sin(sidereal),
            z = EARTH_RADIUS_AU * sin(latitude),
        )
    }

    private fun sinDegrees(degrees: Double) = sin(Math.toRadians(degrees))

    private fun cosDegrees(degrees: Double) = cos(Math.toRadians(degrees))

    /** Tilts the ecliptic frame onto the equatorial one. */
    private fun eclipticToEquatorial(v: Vector): Vector {
        val cosObliquity = cos(Math.toRadians(OBLIQUITY_J2000))
        val sinObliquity = sin(Math.toRadians(OBLIQUITY_J2000))
        return Vector(
            x = v.x,
            y = v.y * cosObliquity - v.z * sinObliquity,
            z = v.y * sinObliquity + v.z * cosObliquity,
        )
    }

    /**
     * Greenwich mean sidereal time, plus the observer's longitude.
     *
     * Sidereal time is what turns a fixed direction in space into a
     * direction on the ground: it is the right ascension currently crossing
     * the meridian overhead.
     */
    fun localSiderealTime(julianDay: Double, longitudeDegrees: Double): Double {
        val days = julianDay - J2000
        val centuries = days / DAYS_PER_CENTURY
        val greenwich = 280.46061837 +
            360.98564736629 * days +
            0.000387933 * centuries * centuries -
            centuries * centuries * centuries / 38_710_000.0
        return normalise(greenwich + longitudeDegrees)
    }

    /**
     * Equatorial to horizon.
     *
     * @return altitude above the horizon and azimuth clockwise from north.
     */
    fun horizon(
        hourAngleDegrees: Double,
        declinationDegrees: Double,
        latitudeDegrees: Double,
    ): Pair<Double, Double> {
        val hourAngle = Math.toRadians(hourAngleDegrees)
        val declination = Math.toRadians(declinationDegrees)
        val latitude = Math.toRadians(latitudeDegrees)

        val altitude = asin(
            sin(latitude) * sin(declination) +
                cos(latitude) * cos(declination) * cos(hourAngle)
        )
        // Measured from south and westward by this form, so a half turn
        // brings it round to the compass convention the phone uses.
        val fromSouth = atan2(
            sin(hourAngle),
            cos(hourAngle) * sin(latitude) - kotlin.math.tan(declination) * cos(latitude),
        )
        val azimuth = normalise(Math.toDegrees(fromSouth) + 180.0)
        return Math.toDegrees(altitude) to azimuth
    }

    /** Folds an angle into 0 up to 360. */
    fun normalise(degrees: Double): Double {
        val wrapped = degrees % 360.0
        return if (wrapped < 0) wrapped + 360.0 else wrapped
    }

    /** Folds an angle into -180 up to 180. */
    fun wrapHalf(degrees: Double): Double = normalise(degrees + 180.0) - 180.0

    /** The shortest way round from one bearing to another, signed. */
    fun separationDegrees(fromDegrees: Double, toDegrees: Double): Double =
        wrapHalf(toDegrees - fromDegrees)

    /** A compass point, for reading out which way to turn. */
    fun compassPoint(azimuthDegrees: Double): Int =
        (floor(normalise(azimuthDegrees) / 45.0 + 0.5).toInt()) % 8

    // ---- the elements ---------------------------------------------------

    /**
     * Six numbers and six rates, per body.
     *
     * Angles in degrees, distances in astronomical units, rates per Julian
     * century from J2000.
     */
    private data class Elements(
        val a: Double, val aRate: Double,
        val e: Double, val eRate: Double,
        val inclination: Double, val inclinationRate: Double,
        val meanLongitude: Double, val meanLongitudeRate: Double,
        val perihelion: Double, val perihelionRate: Double,
        val node: Double, val nodeRate: Double,
    )

    private fun elementsFor(planet: Planet): Elements = when (planet) {
        Planet.MERCURY -> MERCURY
        Planet.VENUS -> VENUS
        // The Moon never reaches here for a position - see moonEcliptic -
        // but it shares the Earth's orbit around the Sun to a quarter of a
        // percent, which is what heliocentricDistanceAu is asking about.
        Planet.EARTH, Planet.SUN, Planet.MOON -> EARTH
        Planet.MARS -> MARS
        Planet.JUPITER -> JUPITER
        Planet.SATURN -> SATURN
        Planet.URANUS -> URANUS
        Planet.NEPTUNE -> NEPTUNE
    }

    private val MERCURY = Elements(
        0.38709927, 0.00000037,
        0.20563593, 0.00001906,
        7.00497902, -0.00594749,
        252.25032350, 149472.67411175,
        77.45779628, 0.16047689,
        48.33076593, -0.12534081,
    )

    private val VENUS = Elements(
        0.72333566, 0.00000390,
        0.00677672, -0.00004107,
        3.39467605, -0.00078890,
        181.97909950, 58517.81538729,
        131.60246718, 0.00268329,
        76.67984255, -0.27769418,
    )

    /**
     * The Earth-Moon barycentre, which is what the fitted set describes.
     * The Earth itself wobbles about it by some 4700km, around a thousandth
     * of a degree seen from any planet.
     */
    private val EARTH = Elements(
        1.00000261, 0.00000562,
        0.01671123, -0.00004392,
        -0.00001531, -0.01294668,
        100.46457166, 35999.37244981,
        102.93768193, 0.32327364,
        0.0, 0.0,
    )

    private val MARS = Elements(
        1.52371034, 0.00001847,
        0.09339410, 0.00007882,
        1.84969142, -0.00813131,
        -4.55343205, 19140.30268499,
        -23.94362959, 0.44441088,
        49.55953891, -0.29257343,
    )

    private val JUPITER = Elements(
        5.20288700, -0.00011607,
        0.04838624, -0.00013253,
        1.30439695, -0.00183714,
        34.39644051, 3034.74612775,
        14.72847983, 0.21252668,
        100.47390909, 0.20469106,
    )

    private val SATURN = Elements(
        9.53667594, -0.00125060,
        0.05386179, -0.00050991,
        2.48599187, 0.00193609,
        49.95424423, 1222.49362201,
        92.59887831, -0.41897216,
        113.66242448, -0.28867794,
    )

    private val URANUS = Elements(
        19.18916464, -0.00196176,
        0.04725744, -0.00004397,
        0.77263783, -0.00242939,
        313.23810451, 428.48202785,
        170.95427630, 0.40805281,
        74.01692503, 0.04240589,
    )

    private val NEPTUNE = Elements(
        30.06992276, 0.00026291,
        0.00859048, 0.00005105,
        1.77004347, 0.00035372,
        -55.12002969, 218.45945325,
        44.96476227, -0.32241464,
        131.78422574, -0.00508664,
    )

    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val DAYS_PER_CENTURY = 36525.0
    private const val OBLIQUITY_J2000 = 23.43928

    /** The Earth's radius, in astronomical units. */
    private const val EARTH_RADIUS_AU = 6378.137 / 149_597_870.7
    private const val KEPLER_PASSES = 12
    private const val KEPLER_TOLERANCE_DEGREES = 1e-7
}

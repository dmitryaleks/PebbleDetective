package com.pebbledetective.domain

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A point on the ground. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * The small amount of spherical geometry the radar needs.
 *
 * Pure, so the awkward parts - crossing the meridian, bearings wrapping past
 * north - are unit testable without a device.
 */
object Geo {

    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in metres. */
    fun distanceMetres(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from one point to another, in degrees clockwise from north. */
    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normaliseDegrees(Math.toDegrees(atan2(y, x)))
    }

    /** The point reached by travelling [distanceMetres] on [bearingDegrees]. */
    fun destination(from: GeoPoint, bearingDegrees: Double, distanceMetres: Double): GeoPoint {
        val angular = distanceMetres / EARTH_RADIUS_M
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)

        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return GeoPoint(
            latitude = Math.toDegrees(lat2),
            // Keep longitude in -180..180 so crossing the date line behaves.
            longitude = ((Math.toDegrees(lon2) + 540) % 360) - 180,
        )
    }

    /**
     * Where a bearing sits relative to the way the phone is pointing, in
     * degrees clockwise. 0 is straight ahead, 90 to the right, 180 behind.
     */
    fun relativeBearing(targetBearing: Double, headingDegrees: Double): Double =
        normaliseDegrees(targetBearing - headingDegrees)

    /** Any angle folded into 0 until 360. */
    fun normaliseDegrees(degrees: Double): Double = ((degrees % 360) + 360) % 360

    /**
     * The shortest signed turn from one heading to another, -180 to 180.
     *
     * Used to smooth the compass: interpolating raw azimuths would spin the
     * whole display the long way round every time it crosses north.
     */
    fun shortestTurn(from: Double, to: Double): Double {
        val delta = normaliseDegrees(to - from)
        return if (delta > 180) delta - 360 else delta
    }

    /**
     * Hides a pebble somewhere near [origin].
     *
     * Never closer than [minMetres], so it is always a short walk rather than
     * landing on top of the player, and never further than [maxMetres].
     */
    fun randomTargetNear(
        origin: GeoPoint,
        random: Random,
        minMetres: Double = 8.0,
        maxMetres: Double = 30.0,
    ): GeoPoint {
        val bearing = random.nextDouble(0.0, 360.0)
        // Uniform over the area rather than over the radius, which would
        // otherwise cluster targets near the middle.
        val t = random.nextDouble()
        val distance = sqrt(minMetres * minMetres + t * (maxMetres * maxMetres - minMetres * minMetres))
        return destination(origin, bearing, distance)
    }

    /** Close enough to call it found. */
    fun isFound(distanceMetres: Double, thresholdMetres: Double = 3.0): Boolean =
        distanceMetres <= thresholdMetres

    /** True when two headings are within [toleranceDegrees] of each other. */
    fun isAimedAt(relativeBearing: Double, toleranceDegrees: Double = 12.0): Boolean =
        abs(shortestTurn(0.0, relativeBearing)) <= toleranceDegrees
}

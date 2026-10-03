package com.pebbledetective.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GeoTest {

    private val tokyo = GeoPoint(35.6895, 139.6917)

    @Test
    fun `distance to itself is zero`() {
        assertEquals(0.0, Geo.distanceMetres(tokyo, tokyo), 1e-6)
    }

    @Test
    fun `travelling a distance then measuring it gives the same distance back`() {
        for (bearing in 0 until 360 step 30) {
            for (metres in listOf(1.0, 8.0, 30.0, 500.0)) {
                val there = Geo.destination(tokyo, bearing.toDouble(), metres)
                assertEquals(
                    "bearing=$bearing metres=$metres",
                    metres,
                    Geo.distanceMetres(tokyo, there),
                    0.01,
                )
            }
        }
    }

    @Test
    fun `travelling a bearing then measuring it gives the same bearing back`() {
        for (bearing in 0 until 360 step 15) {
            val there = Geo.destination(tokyo, bearing.toDouble(), 25.0)
            val measured = Geo.bearingDegrees(tokyo, there)
            assertEquals("bearing=$bearing", 0.0, Geo.shortestTurn(bearing.toDouble(), measured), 0.01)
        }
    }

    @Test
    fun `north east south and west go where expected`() {
        assertTrue(Geo.destination(tokyo, 0.0, 100.0).latitude > tokyo.latitude)
        assertTrue(Geo.destination(tokyo, 180.0, 100.0).latitude < tokyo.latitude)
        assertTrue(Geo.destination(tokyo, 90.0, 100.0).longitude > tokyo.longitude)
        assertTrue(Geo.destination(tokyo, 270.0, 100.0).longitude < tokyo.longitude)
    }

    @Test
    fun `angles fold into zero until three sixty`() {
        assertEquals(10.0, Geo.normaliseDegrees(370.0), 1e-9)
        assertEquals(350.0, Geo.normaliseDegrees(-10.0), 1e-9)
        assertEquals(0.0, Geo.normaliseDegrees(720.0), 1e-9)
    }

    /** Crossing north must not spin the display the long way round. */
    @Test
    fun `the shortest turn goes the short way across north`() {
        assertEquals(20.0, Geo.shortestTurn(350.0, 10.0), 1e-9)
        assertEquals(-20.0, Geo.shortestTurn(10.0, 350.0), 1e-9)
        assertEquals(0.0, Geo.shortestTurn(123.0, 123.0), 1e-9)
        assertTrue(Geo.shortestTurn(0.0, 181.0) in -180.0..180.0)
    }

    @Test
    fun `relative bearing is measured from where the phone points`() {
        // Target due north, phone facing north: dead ahead.
        assertEquals(0.0, Geo.relativeBearing(0.0, 0.0), 1e-9)
        // Target due north, phone facing east: the target is to the left.
        assertEquals(270.0, Geo.relativeBearing(0.0, 90.0), 1e-9)
        // Target due east, phone facing north: to the right.
        assertEquals(90.0, Geo.relativeBearing(90.0, 0.0), 1e-9)
    }

    @Test
    fun `a hidden pebble is always within range and never underfoot`() {
        val random = Random(99)
        repeat(500) {
            val target = Geo.randomTargetNear(tokyo, random)
            val distance = Geo.distanceMetres(tokyo, target)
            assertTrue("too close: $distance", distance >= 8.0 - 0.01)
            assertTrue("too far: $distance", distance <= 30.0 + 0.01)
        }
    }

    @Test
    fun `hidden pebbles are spread around the compass, not all one way`() {
        val random = Random(7)
        val quadrants = mutableSetOf<Int>()
        repeat(200) {
            val target = Geo.randomTargetNear(tokyo, random)
            quadrants += (Geo.bearingDegrees(tokyo, target) / 90).toInt()
        }
        assertEquals("every quadrant should get used", 4, quadrants.size)
    }

    @Test
    fun `the same seed hides the pebble in the same place`() {
        val a = Geo.randomTargetNear(tokyo, Random(42))
        val b = Geo.randomTargetNear(tokyo, Random(42))
        assertEquals(a, b)
    }

    @Test
    fun `found only when close enough`() {
        assertTrue(Geo.isFound(0.5))
        assertTrue(Geo.isFound(3.0))
        assertFalse(Geo.isFound(3.5))
    }

    @Test
    fun `aiming is forgiving but not blind`() {
        assertTrue(Geo.isAimedAt(0.0))
        assertTrue(Geo.isAimedAt(10.0))
        assertTrue("just past north counts", Geo.isAimedAt(355.0))
        assertFalse(Geo.isAimedAt(45.0))
        assertFalse("behind you is not aimed", Geo.isAimedAt(180.0))
    }

    /** Longitude arithmetic must survive the date line. */
    @Test
    fun `works across the date line`() {
        val nearLine = GeoPoint(0.0, 179.9999)
        val across = Geo.destination(nearLine, 90.0, 50.0)
        assertTrue("longitude left the valid range: ${across.longitude}",
            across.longitude in -180.0..180.0)
        assertEquals(50.0, Geo.distanceMetres(nearLine, across), 0.1)
    }
}

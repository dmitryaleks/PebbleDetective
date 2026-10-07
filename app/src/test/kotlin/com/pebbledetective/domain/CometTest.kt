package com.pebbledetective.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The invented comets, and the walk round the compass they make. */
class CometTest {

    /** Due south first, then clockwise a point at a time. */
    @Test
    fun `the first is south and the rest follow the compass`() {
        val expected = listOf(180.0, 225.0, 270.0, 315.0, 0.0, 45.0, 90.0, 135.0)
        assertEquals(expected, Comet.entries.map { it.azimuthDegrees })
        assertEquals((1..8).toList(), Comet.entries.map { it.number })
        assertEquals(Comet.COUNT, Comet.entries.size)
    }

    /**
     * The compass label on the control has to name the direction the
     * comet is actually in, or a grown-up aiming at the park sends the
     * hunt somewhere else.
     */
    @Test
    fun `each sits on a named point of the compass`() {
        // 0 is north and the points run clockwise.
        val expected = listOf(4, 5, 6, 7, 0, 1, 2, 3)
        assertEquals(
            expected,
            Comet.entries.map { Astronomy.compassPoint(it.azimuthDegrees) },
        )
    }

    /** Off, one through eight, off again. */
    @Test
    fun `the taps go round and come back to nothing`() {
        var comet = Comet.next(null)
        for (number in 1..8) {
            assertNotNull("tap $number put nothing up", comet)
            assertEquals(number, comet!!.number)
            comet = Comet.next(comet)
        }
        assertNull("the ninth tap should clear the sky", comet)
        assertEquals(Comet.FIRST, Comet.next(comet))
    }

    /**
     * All of them high. The point of a comet here is to be findable over
     * the roofs from wherever you are standing, so none may sit where a
     * building would hide it, and none may be overhead where you cannot
     * comfortably point a phone.
     */
    @Test
    fun `all of them hang high but not overhead`() {
        for (comet in Comet.entries) {
            assertTrue(
                "$comet sits at ${comet.altitudeDegrees} degrees",
                comet.altitudeDegrees in 45.0..75.0,
            )
        }
    }

    /** And no two at the same height, or two in a row look like one moved. */
    @Test
    fun `no two are at the same height`() {
        val heights = Comet.entries.map { it.altitudeDegrees }
        assertEquals(heights.size, heights.toSet().size)
    }

    @Test
    fun `numbers map back to comets`() {
        for (comet in Comet.entries) {
            assertEquals(comet, Comet.ofNumber(comet.number))
        }
        assertNull(Comet.ofNumber(0))
        assertNull(Comet.ofNumber(9))
    }
}

package com.pebbledetective.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The planetarium's clock, and the sky it points at. */
class OrreryTest {

    /** Starting still matters: the screen opens on the real sky, now. */
    @Test
    fun `the slowest speed is a standstill`() {
        assertEquals(0, Orrery.SPEEDS.first())
        assertEquals(listOf(0, 1, 5, 10, 20), Orrery.SPEEDS)
    }

    @Test
    fun `a second at one day a second is one day`() {
        assertEquals(1.0, Orrery.advance(0.0, 1, backwards = false, frameSeconds = 1.0), 1e-9)
        assertEquals(20.0, Orrery.advance(0.0, 20, backwards = false, frameSeconds = 1.0), 1e-9)
        assertEquals(-5.0, Orrery.advance(0.0, 5, backwards = true, frameSeconds = 1.0), 1e-9)
    }

    /**
     * Sixty short frames and one long one have to land in the same place.
     * The sky runs at a rate, not a step, or a stutter would slow time
     * down rather than skip over it.
     */
    @Test
    fun `a dropped frame costs a longer step and not a slower sky`() {
        var smooth = 0.0
        repeat(60) { smooth = Orrery.advance(smooth, 10, backwards = false, frameSeconds = 1.0 / 60) }
        val stuttered = Orrery.advance(0.0, 10, backwards = false, frameSeconds = 1.0)
        assertEquals(stuttered, smooth, 1e-6)
    }

    @Test
    fun `time stops at the end of the range rather than wrapping`() {
        val far = Orrery.advance(Orrery.RANGE_DAYS - 2.0, 20, backwards = false, frameSeconds = 10.0)
        assertEquals(Orrery.RANGE_DAYS, far, 1e-9)
        assertTrue(Orrery.atLimit(far))
        assertTrue(Orrery.atLimit(-Orrery.RANGE_DAYS))
        assertFalse(Orrery.atLimit(0.0))
    }

    @Test
    fun `an offset in days is that many days of milliseconds`() {
        val opened = 1_767_225_600_000L
        assertEquals(opened, Orrery.epochAt(opened, 0.0))
        assertEquals(opened + Orrery.MILLIS_PER_DAY, Orrery.epochAt(opened, 1.0))
        assertEquals(opened - 7 * Orrery.MILLIS_PER_DAY, Orrery.epochAt(opened, -7.0))
        // And a fraction of a day is a fraction of a day, not nothing:
        // at twenty days a second a frame is a third of one.
        assertEquals(opened + Orrery.MILLIS_PER_DAY / 2, Orrery.epochAt(opened, 0.5))
    }

    /** Ten years each way, which is most of a Jupiter orbit. */
    @Test
    fun `the range reaches a decade either side`() {
        assertEquals(10.0, Orrery.RANGE_DAYS / 365.25, 0.02)
        assertEquals(
            Orrery.epochAt(0L, Orrery.RANGE_DAYS),
            Orrery.epochAt(0L, Orrery.RANGE_DAYS + 500),
        )
    }

    /**
     * The sky the planetarium draws is the sky the rest of the app
     * computes. A year on and the Earth is back where it started, give or
     * take the quarter day that leap years exist to absorb.
     */
    @Test
    fun `a year of running time brings the Earth back round`() {
        val start = 1_767_225_600_000L
        val before = Astronomy.heliocentricEcliptic(Planet.EARTH, start)
        val after = Astronomy.heliocentricEcliptic(
            Planet.EARTH,
            Orrery.epochAt(start, 365.25),
        )
        assertEquals(before.x, after.x, 0.01)
        assertEquals(before.y, after.y, 0.01)
    }

    /**
     * And the other way: half a Mars year puts it across the Sun from
     * where it was, which is the thing the fast speeds are for watching.
     */
    @Test
    fun `half a Martian year puts Mars on the far side`() {
        val start = 1_767_225_600_000L
        val before = Astronomy.heliocentricEcliptic(Planet.MARS, start)
        val after = Astronomy.heliocentricEcliptic(Planet.MARS, Orrery.epochAt(start, 343.5))
        // Opposite signs on both axes is the only way to be across the Sun.
        assertTrue("x did not cross: ${before.x} then ${after.x}", before.x * after.x < 0)
        assertTrue("y did not cross: ${before.y} then ${after.y}", before.y * after.y < 0)
    }
}

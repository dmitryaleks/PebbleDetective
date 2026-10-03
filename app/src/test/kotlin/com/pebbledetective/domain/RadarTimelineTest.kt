package com.pebbledetective.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarTimelineTest {

    @Test
    fun `beeping gets faster as the pebble gets closer`() {
        var previous = Long.MAX_VALUE
        for (metres in 30 downTo 0) {
            val interval = RadarTimeline.proximityIntervalMs(metres.toDouble())
            assertTrue(
                "interval went up while closing in at ${metres}m",
                interval <= previous,
            )
            previous = interval
        }
    }

    @Test
    fun `beeping stays within a sane range`() {
        for (metres in 0..60) {
            val interval = RadarTimeline.proximityIntervalMs(metres.toDouble())
            assertTrue("interval $interval out of range at ${metres}m", interval in 150..1_100)
        }
    }

    @Test
    fun `standing on the pebble beeps as fast as it ever will`() {
        assertEquals(150L, RadarTimeline.proximityIntervalMs(0.0))
    }

    @Test
    fun `beyond the outer ring is the slowest beep, never slower`() {
        val atRange = RadarTimeline.proximityIntervalMs(30.0)
        assertEquals(atRange, RadarTimeline.proximityIntervalMs(500.0))
    }

    @Test
    fun `a zero range does not divide by zero`() {
        assertEquals(150L, RadarTimeline.proximityIntervalMs(5.0, rangeMetres = 0.0))
    }

    /** The speed-up should be felt near the target, not spread out flatly. */
    @Test
    fun `most of the acceleration happens in the last third`() {
        val far = RadarTimeline.proximityIntervalMs(30.0)
        val middle = RadarTimeline.proximityIntervalMs(15.0)
        val near = RadarTimeline.proximityIntervalMs(5.0)
        assertTrue("middle should already be much quicker", middle < far / 2)
        assertTrue("near should be close to the floor", near < 250)
    }

    @Test
    fun `the sweep goes round once per period`() {
        assertEquals(0f, RadarTimeline.sweepDegrees(0), 0.01f)
        assertEquals(180f, RadarTimeline.sweepDegrees(RadarTimeline.SWEEP_PERIOD_MS / 2), 0.01f)
        assertEquals(0f, RadarTimeline.sweepDegrees(RadarTimeline.SWEEP_PERIOD_MS), 0.01f)
    }

    @Test
    fun `the pulse phase stays between zero and one`() {
        for (ms in 0..5_000L step 37) {
            assertTrue(RadarTimeline.pulsePhase(ms) in 0f..1f)
        }
    }
}

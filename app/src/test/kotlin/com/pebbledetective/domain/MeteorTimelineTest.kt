package com.pebbledetective.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeteorTimelineTest {

    @Test
    fun `phases run in order and tile the fall`() {
        var previous = MeteorTimeline.phaseAt(0)
        val order = mutableListOf(previous)
        for (t in 0..MeteorTimeline.TOTAL_MS step 10) {
            val phase = MeteorTimeline.phaseAt(t)
            if (phase != previous) {
                order += phase
                previous = phase
            }
        }
        assertEquals(
            listOf(
                MeteorPhase.IGNITION,
                MeteorPhase.FALL,
                MeteorPhase.IMPACT,
                MeteorPhase.SETTLING,
            ),
            order,
        )
    }

    @Test
    fun `it leaves the planet and reaches the ground`() {
        assertEquals(0f, MeteorTimeline.travel(0), 0.001f)
        assertEquals(0f, MeteorTimeline.travel(700), 0.001f)
        assertEquals(1f, MeteorTimeline.travel(3_000), 0.001f)
        assertEquals(1f, MeteorTimeline.travel(MeteorTimeline.TOTAL_MS), 0.001f)
    }

    @Test
    fun `the fall never runs backwards`() {
        var last = -1f
        for (t in 0..MeteorTimeline.TOTAL_MS step 10) {
            val travel = MeteorTimeline.travel(t)
            assertTrue("travel went backwards at ${t}ms", travel >= last)
            last = travel
        }
    }

    /** It should be visibly faster at the end than at the start. */
    @Test
    fun `the meteor accelerates as it comes down`() {
        val early = MeteorTimeline.travel(1_200) - MeteorTimeline.travel(1_000)
        val late = MeteorTimeline.travel(2_900) - MeteorTimeline.travel(2_700)
        assertTrue("early $early was not slower than late $late", late > early * 1.5f)
    }

    /**
     * One flash, not a flicker. A large-area strobe is a genuine seizure
     * risk and this is a children's app, so the test is here to stop a
     * later tweak turning the impact into one.
     */
    @Test
    fun `there is exactly one flash and it is brief`() {
        var crossings = 0
        var lit = false
        var litMs = 0L
        for (t in 0..MeteorTimeline.TOTAL_MS step 10) {
            val bright = MeteorTimeline.flash(t) > 0.05f
            if (bright) litMs += 10
            if (bright != lit) {
                if (bright) crossings++
                lit = bright
            }
        }
        assertEquals(1, crossings)
        assertTrue("the flash lasted ${litMs}ms", litMs in 100..600)
    }

    @Test
    fun `the impact only happens once the meteor is down`() {
        assertEquals(0f, MeteorTimeline.flash(2_999), 0.001f)
        assertEquals(0f, MeteorTimeline.shockwave(2_999), 0.001f)
        assertEquals(0f, MeteorTimeline.emberGlow(2_999), 0.001f)
        assertTrue(MeteorTimeline.flash(3_010) > 0.5f)
        assertTrue(MeteorTimeline.shockwave(4_000) > 0.3f)
    }

    @Test
    fun `the dust spreads out and the embers cool`() {
        assertEquals(1f, MeteorTimeline.shockwave(MeteorTimeline.TOTAL_MS), 0.001f)
        assertEquals(0f, MeteorTimeline.emberGlow(MeteorTimeline.TOTAL_MS), 0.001f)
        assertTrue(MeteorTimeline.emberGlow(3_200) > MeteorTimeline.emberGlow(4_600))
    }

    @Test
    fun `the heat builds through the fall and is gone after impact`() {
        assertTrue(MeteorTimeline.heat(0) < 0.1f)
        assertTrue(MeteorTimeline.heat(2_900) > 0.9f)
        assertEquals(0f, MeteorTimeline.heat(3_100), 0.001f)
    }

    /** Every cue has to fall inside the animation it belongs to. */
    @Test
    fun `the sound cues land within the sequence and in order`() {
        val cues = listOf(
            MeteorTimeline.CUE_IGNITE_MS,
            MeteorTimeline.CUE_ENTRY_MS,
            MeteorTimeline.CUE_IMPACT_MS,
            MeteorTimeline.CUE_SETTLED_MS,
        )
        assertEquals(cues.sorted(), cues)
        assertTrue(cues.all { it in 0..MeteorTimeline.TOTAL_MS })
        // The bang belongs at the moment it hits, not before or after.
        assertEquals(MeteorPhase.IMPACT, MeteorTimeline.phaseAt(MeteorTimeline.CUE_IMPACT_MS))
    }
}

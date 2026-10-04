package com.pebbledetective.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchTimelineTest {

    @Test
    fun `phases tile the whole sequence with no gap`() {
        var previous = ResearchTimeline.phaseAt(0L)
        assertEquals(ResearchPhase.ACQUIRING, previous)
        val order = mutableListOf(previous)
        for (t in 0..ResearchTimeline.TOTAL_MS step 10) {
            val phase = ResearchTimeline.phaseAt(t)
            if (phase != previous) {
                order += phase
                previous = phase
            }
        }
        // Each beat happens once, in order, and nothing is skipped.
        assertEquals(
            listOf(
                ResearchPhase.ACQUIRING,
                ResearchPhase.SIGNAL,
                ResearchPhase.ANALYSING,
                ResearchPhase.MATCH,
            ),
            order,
        )
    }

    @Test
    fun `the satellite comes first and the match comes last`() {
        assertEquals(ResearchPhase.ACQUIRING, ResearchTimeline.phaseAt(0))
        assertEquals(ResearchPhase.MATCH, ResearchTimeline.phaseAt(4_800))
        assertTrue(ResearchTimeline.progressAt(4_800) >= 0.95f)
    }

    @Test
    fun `progress runs zero to one across the whole five seconds`() {
        assertEquals(0f, ResearchTimeline.progressAt(0), 0.001f)
        assertEquals(0.5f, ResearchTimeline.progressAt(2_500), 0.001f)
        assertEquals(1f, ResearchTimeline.progressAt(ResearchTimeline.TOTAL_MS), 0.001f)
        // Never overruns, however late the frame arrives.
        assertEquals(1f, ResearchTimeline.progressAt(99_999), 0.001f)
    }

    @Test
    fun `analysis lines stream in rather than appearing at once`() {
        assertEquals(0, ResearchTimeline.visibleLines(0, 5))
        assertEquals(0, ResearchTimeline.visibleLines(1_000, 5))
        val middle = ResearchTimeline.visibleLines(3_000, 5)
        assertTrue("middle was $middle", middle in 1..4)
        assertEquals(5, ResearchTimeline.visibleLines(4_300, 5))
        assertEquals(5, ResearchTimeline.visibleLines(9_000, 5))
    }

    @Test
    fun `completion is reported only at the end`() {
        assertTrue(!ResearchTimeline.isComplete(4_999))
        assertTrue(ResearchTimeline.isComplete(ResearchTimeline.TOTAL_MS))
    }
}

class JourneyTimelineTest {

    /**
     * The brief asked for ten to fifteen seconds, which covered the flight
     * through space only. The landing was added to one end and the opening
     * shot of the solar system to the other, which takes the whole thing
     * to twenty-five. Long enough that the skip button has stopped being a
     * courtesy, which is why it is on screen throughout.
     */
    @Test
    fun `the journey lasts twenty five seconds`() {
        assertEquals(25_000L, JourneyTimeline.TOTAL_MS)
    }

    @Test
    fun `phases run in order and tile the flight`() {
        var previous = JourneyTimeline.phaseAt(0)
        val order = mutableListOf(previous)
        for (t in 0..JourneyTimeline.TOTAL_MS step 10) {
            val phase = JourneyTimeline.phaseAt(t)
            if (phase != previous) {
                order += phase
                previous = phase
            }
        }
        assertEquals(
            listOf(
                JourneyPhase.SYSTEM,
                JourneyPhase.CLOSING,
                JourneyPhase.DEPARTURE,
                JourneyPhase.LAUNCH,
                JourneyPhase.CRUISE,
                JourneyPhase.ENTRY,
                JourneyPhase.APPROACH,
                JourneyPhase.DESCENT,
                JourneyPhase.TOUCHDOWN,
            ),
            order,
        )
    }

    @Test
    fun `the pebble stays put until it launches then reaches Earth`() {
        assertEquals(0f, JourneyTimeline.travel(0), 0.001f)
        assertEquals(0f, JourneyTimeline.travel(9_200), 0.001f)
        assertTrue(JourneyTimeline.travel(12_500) > 0.3f)
        assertEquals(1f, JourneyTimeline.travel(17_500), 0.001f)
        assertEquals(1f, JourneyTimeline.travel(JourneyTimeline.TOTAL_MS), 0.001f)
    }

    /**
     * The opening shot holds still long enough to be read as a map of
     * where the planets actually were, then leaves.
     */
    @Test
    fun `the camera holds on the system before it closes in`() {
        assertEquals(0f, JourneyTimeline.closing(0), 0.001f)
        assertEquals(0f, JourneyTimeline.closing(4_000), 0.001f)
        val early = JourneyTimeline.closing(4_500)
        assertTrue("it bolted straight off the mark: $early", early < 0.15f)
        assertEquals(1f, JourneyTimeline.closing(7_000), 0.001f)
        assertEquals(1f, JourneyTimeline.closing(JourneyTimeline.TOTAL_MS), 0.001f)
    }

    /** And the pebble only leaves the surface once the camera has arrived. */
    @Test
    fun `the pebble breaks out after the camera has settled`() {
        assertEquals(0f, JourneyTimeline.breakout(7_000), 0.001f)
        assertTrue(JourneyTimeline.breakout(8_200) > 0.4f)
        assertEquals(1f, JourneyTimeline.breakout(9_200), 0.001f)
        // Clear of the planet before it starts crossing.
        assertEquals(0f, JourneyTimeline.travel(9_200), 0.001f)
    }

    /**
     * It catches fire near Earth and not before. A pebble burning in deep
     * space is the kind of thing a child notices and an adult does not.
     */
    @Test
    fun `the pebble only burns near the end of the crossing`() {
        assertEquals(0f, JourneyTimeline.pebbleFire(9_200), 0.001f)
        assertEquals(0f, JourneyTimeline.pebbleFire(12_000), 0.001f)
        assertTrue(JourneyTimeline.pebbleFire(16_500) > 0.5f)
        assertEquals(1f, JourneyTimeline.pebbleFire(17_500), 0.001f)
    }

    @Test
    fun `the descent zooms from the whole country down to the street`() {
        // Nothing happens until the atmosphere has been crossed.
        assertEquals(0f, JourneyTimeline.descentZoom(0), 0.001f)
        assertEquals(0f, JourneyTimeline.descentZoom(17_500), 0.001f)
        val middle = JourneyTimeline.descentZoom(20_500)
        assertTrue("halfway down was $middle", middle in 0.4f..0.6f)
        assertEquals(1f, JourneyTimeline.descentZoom(23_500), 0.001f)
        assertEquals(1f, JourneyTimeline.descentZoom(JourneyTimeline.TOTAL_MS), 0.001f)
    }

    @Test
    fun `the zoom never runs backwards`() {
        var last = -1f
        for (t in 0..JourneyTimeline.TOTAL_MS step 25) {
            val zoom = JourneyTimeline.descentZoom(t)
            assertTrue("zoom went backwards at ${t}ms", zoom >= last)
            last = zoom
        }
    }

    /**
     * The cut from starfield to map has to happen while the entry glow is
     * bright enough to hide it, or the scene visibly swaps.
     */
    @Test
    fun `the map fades in under the brightest part of the entry glow`() {
        assertEquals(0f, JourneyTimeline.mapReveal(15_000), 0.001f)
        assertEquals(0f, JourneyTimeline.mapReveal(16_900), 0.001f)
        assertEquals(1f, JourneyTimeline.mapReveal(17_500), 0.001f)
        val midway = JourneyTimeline.mapReveal(17_200)
        assertTrue("reveal was $midway", midway in 0.2f..0.8f)
        assertTrue(
            "the glow must still be strong while the map appears",
            JourneyTimeline.entryHeat(17_200) > 0.6f,
        )
        // And the map must not start showing through the crossing, which
        // is what happened when the flight was stretched and this was
        // left behind as an absolute time.
        assertEquals(0f, JourneyTimeline.mapReveal(12_000), 0.001f)
    }

    @Test
    fun `altitude counts down to the ground and stops there`() {
        assertEquals(120, JourneyTimeline.altitudeKm(0))
        assertEquals(120, JourneyTimeline.altitudeKm(15_000))
        assertTrue(JourneyTimeline.altitudeKm(20_000) in 1..119)
        assertEquals(0, JourneyTimeline.altitudeKm(23_500))
        assertEquals(0, JourneyTimeline.altitudeKm(99_999))
    }

    @Test
    fun `the impact only spreads once the pebble is down`() {
        assertEquals(0f, JourneyTimeline.landedFraction(23_000), 0.001f)
        assertEquals(0f, JourneyTimeline.landedFraction(23_500), 0.001f)
        assertEquals(1f, JourneyTimeline.landedFraction(JourneyTimeline.TOTAL_MS), 0.001f)
    }

    @Test
    fun `travel never runs backwards`() {
        var last = -1f
        for (t in 0..JourneyTimeline.TOTAL_MS step 25) {
            val travel = JourneyTimeline.travel(t)
            assertTrue("travel went backwards at ${t}ms", travel >= last)
            last = travel
        }
    }
}

class ProjectionTest {

    @Test
    fun `things further away appear smaller and closer to the centre`() {
        val near = Projection.screenRadius(100f, 500f)
        val far = Projection.screenRadius(100f, 2_000f)
        assertTrue("near=$near far=$far", near > far)

        val nearX = Projection.screenX(200f, 500f, 0f)
        val farX = Projection.screenX(200f, 2_000f, 0f)
        assertTrue("perspective should pull toward centre", nearX > farX)
    }

    @Test
    fun `nothing at or behind the near plane is drawn`() {
        assertTrue(!Projection.isVisible(0f))
        assertTrue(!Projection.isVisible(Projection.NEAR_PLANE))
        assertTrue(Projection.isVisible(Projection.NEAR_PLANE + 1f))
    }

    @Test
    fun `the bezier starts and ends where it is told`() {
        assertEquals(10f, Projection.bezier(10f, 50f, 90f, 0f), 0.001f)
        assertEquals(90f, Projection.bezier(10f, 50f, 90f, 1f), 0.001f)
        // The control point bends the path off the straight line.
        assertEquals(50f, Projection.bezier(10f, 50f, 90f, 0.5f), 0.001f)
    }
}

class StabilityTrackerTest {

    private fun feed(tracker: StabilityTracker, rate: Float, samples: Int = 20, startMs: Long = 0) {
        for (i in 0 until samples) tracker.sample(startMs + i * 20L, rate)
    }

    @Test
    fun `a steady hand settles and a shaking one does not`() {
        val steady = StabilityTracker()
        feed(steady, 0.05f)
        assertTrue(steady.isSteady(0))

        val shaky = StabilityTracker()
        feed(shaky, 1.5f)
        assertTrue(!shaky.isSteady(0))
    }

    @Test
    fun `judges on the ninetieth percentile so one jolt still counts`() {
        val tracker = StabilityTracker()
        // Mostly calm, but a fifth of the window is a violent jolt. A mean
        // would hide this; the 90th percentile must not.
        for (i in 0 until 20) tracker.sample(i * 20L, if (i % 5 == 0) 3.0f else 0.01f)
        assertTrue("a jolt was averaged away", !tracker.isSteady(0))
    }

    @Test
    fun `needs data before it will claim anything`() {
        val tracker = StabilityTracker()
        tracker.sample(0, 0.01f)
        assertTrue(!tracker.hasEnoughData())
        assertEquals(0f, tracker.progress(0), 0.001f)
    }

    /**
     * The important one for a child: a gate that can never be satisfied is
     * worse than no gate. Thresholds relax, and eventually it gives up.
     */
    @Test
    fun `gives up waiting and fires anyway`() {
        val tracker = StabilityTracker()
        feed(tracker, 5.0f)
        assertTrue("should not be steady early", !tracker.isSteady(500))
        assertTrue("must force a capture by 7s", tracker.isSteady(7_000))
        assertEquals(1f, tracker.progress(7_000), 0.001f)
    }

    @Test
    fun `thresholds loosen the longer it takes`() {
        val tracker = StabilityTracker()
        feed(tracker, 0.30f)
        assertTrue("0.30 exceeds the initial 0.20 bar", !tracker.isSteady(0))

        val patient = StabilityTracker()
        feed(patient, 0.30f)
        assertTrue("after 3s the bar relaxes past 0.30", patient.isSteady(3_000))
    }

    @Test
    fun `hysteresis stops the reticle strobing`() {
        val tracker = StabilityTracker()
        feed(tracker, 0.05f)
        assertTrue(tracker.isSteady(0))
        // A nudge above the entry bar but below the exit bar keeps it steady.
        feed(tracker, 0.30f, startMs = 1_000)
        assertTrue("left steady too eagerly", tracker.isSteady(0))
    }

    @Test
    fun `progress always has something to show`() {
        val tracker = StabilityTracker()
        feed(tracker, 0.8f)
        val progress = tracker.progress(0)
        assertTrue("progress $progress out of range", progress in 0f..1f)
    }
}

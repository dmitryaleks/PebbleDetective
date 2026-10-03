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

    /** The brief asks for a flight of ten to fifteen seconds. */
    @Test
    fun `the journey lasts between ten and fifteen seconds`() {
        assertTrue(JourneyTimeline.TOTAL_MS in 10_000..15_000)
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
                JourneyPhase.DEPARTURE,
                JourneyPhase.LAUNCH,
                JourneyPhase.CRUISE,
                JourneyPhase.ENTRY,
                JourneyPhase.LANDING,
            ),
            order,
        )
    }

    @Test
    fun `the pebble stays put until it launches then reaches Earth`() {
        assertEquals(0f, JourneyTimeline.travel(0), 0.001f)
        assertEquals(0f, JourneyTimeline.travel(2_000), 0.001f)
        assertTrue(JourneyTimeline.travel(6_000) > 0.3f)
        assertEquals(1f, JourneyTimeline.travel(11_000), 0.001f)
        assertEquals(1f, JourneyTimeline.travel(JourneyTimeline.TOTAL_MS), 0.001f)
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

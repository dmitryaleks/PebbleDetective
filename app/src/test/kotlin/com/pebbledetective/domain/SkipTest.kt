package com.pebbledetective.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the skip buttons.
 *
 * Both skips shipped broken: the guard compared the proposed start time
 * against the current one and bailed when it was earlier. But *earlier* is
 * exactly what skipping produces - the start moves backwards so the elapsed
 * time lands on the total. The condition only ever passed once the sequence
 * had already finished, so the buttons did nothing for their entire useful
 * life.
 */
class SkipTest {

    private val start = 1_000_000L

    @Test
    fun `skipping research mid-sequence actually skips`() {
        // One second into five.
        val now = start + 1_000
        val skipped = ResearchTimeline.skippedStart(start, now)
        assertNotNull("skip did nothing mid-sequence", skipped)
        assertEquals(ResearchTimeline.TOTAL_MS, now - skipped!!)
        assertTrue(ResearchTimeline.isComplete(now - skipped))
    }

    @Test
    fun `skipping research works at every point along the way`() {
        for (elapsed in 0 until ResearchTimeline.TOTAL_MS step 100) {
            val now = start + elapsed
            val skipped = ResearchTimeline.skippedStart(start, now)
            assertNotNull("skip did nothing at ${elapsed}ms", skipped)
            assertTrue(
                "skip left the sequence unfinished at ${elapsed}ms",
                ResearchTimeline.isComplete(now - skipped!!),
            )
        }
    }

    @Test
    fun `the skipped start is earlier than the original, which is the point`() {
        val now = start + 500
        val skipped = ResearchTimeline.skippedStart(start, now)!!
        assertTrue("skipping must move the start backwards", skipped < start)
    }

    @Test
    fun `skipping an already-finished research is a no-op`() {
        val now = start + ResearchTimeline.TOTAL_MS + 1
        assertNull(ResearchTimeline.skippedStart(start, now))
    }

    @Test
    fun `skipping the journey mid-flight actually skips`() {
        val now = start + 3_000
        val skipped = JourneyTimeline.skippedStart(start, now)
        assertNotNull("skip did nothing mid-flight", skipped)
        assertEquals(JourneyTimeline.TOTAL_MS, now - skipped!!)
        assertTrue(JourneyTimeline.isComplete(now - skipped))
    }

    @Test
    fun `skipping the journey works at every point along the way`() {
        for (elapsed in 0 until JourneyTimeline.TOTAL_MS step 250) {
            val now = start + elapsed
            val skipped = JourneyTimeline.skippedStart(start, now)
            assertNotNull("skip did nothing at ${elapsed}ms", skipped)
            assertTrue(
                "skip left the flight unfinished at ${elapsed}ms",
                JourneyTimeline.isComplete(now - skipped!!),
            )
        }
    }

    @Test
    fun `skipping an already-landed journey is a no-op`() {
        val now = start + JourneyTimeline.TOTAL_MS + 1
        assertNull(JourneyTimeline.skippedStart(start, now))
    }

    /** Skipping must never wind the sequence backwards. */
    @Test
    fun `skipping never reduces elapsed time`() {
        for (elapsed in 0 until ResearchTimeline.TOTAL_MS step 100) {
            val now = start + elapsed
            val skipped = ResearchTimeline.skippedStart(start, now) ?: continue
            assertTrue(
                "elapsed went backwards at ${elapsed}ms",
                (now - skipped) >= elapsed,
            )
        }
    }
}

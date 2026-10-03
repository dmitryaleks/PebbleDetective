package com.pebbledetective.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashTimelineTest {

    /**
     * The one property that actually matters for a splash screen: it has to
     * get out of the way. Everything else about it is taste.
     */
    @Test
    fun `the titles are over in under three seconds`() {
        assertTrue(SplashTimeline.TOTAL_MS <= 3_000)
        assertTrue(SplashTimeline.isComplete(SplashTimeline.TOTAL_MS))
        assertTrue(!SplashTimeline.isComplete(SplashTimeline.TOTAL_MS - 1))
    }

    @Test
    fun `everything starts hidden and ends fully shown`() {
        val stages = listOf<(Long) -> Float>(
            SplashTimeline::sky,
            SplashTimeline::trail,
            SplashTimeline::pebble,
            SplashTimeline::reticle,
            SplashTimeline::title,
        )
        for (stage in stages) {
            assertEquals(0f, stage(0L), 0.001f)
            assertEquals(1f, stage(SplashTimeline.TOTAL_MS), 0.001f)
        }
    }

    /** Nothing may arrive before there is a sky for it to arrive on. */
    @Test
    fun `the sky leads and the title follows everything else`() {
        assertTrue(SplashTimeline.sky(400) > SplashTimeline.body(0, 400))
        assertTrue(SplashTimeline.trail(1_000) > SplashTimeline.reticle(1_000))
        assertTrue(SplashTimeline.reticle(1_500) > SplashTimeline.title(1_500))
    }

    @Test
    fun `the bodies arrive one after another and all of them land`() {
        val midway = (0 until SplashTimeline.BODY_COUNT).map { SplashTimeline.body(it, 700) }
        for (i in 1 until midway.size) {
            assertTrue("body $i overtook body ${i - 1}", midway[i] <= midway[i - 1])
        }
        for (i in 0 until SplashTimeline.BODY_COUNT) {
            assertEquals(
                "body $i never finished arriving",
                1f, SplashTimeline.body(i, SplashTimeline.TOTAL_MS), 0.001f,
            )
        }
    }

    @Test
    fun `no stage ever runs backwards`() {
        val stages = listOf<(Long) -> Float>(
            SplashTimeline::sky,
            SplashTimeline::trail,
            SplashTimeline::pebble,
            SplashTimeline::reticle,
            SplashTimeline::title,
        )
        for (stage in stages) {
            var last = -1f
            for (t in 0..SplashTimeline.TOTAL_MS step 10) {
                val value = stage(t)
                assertTrue("went backwards at ${t}ms", value >= last)
                last = value
            }
        }
    }
}

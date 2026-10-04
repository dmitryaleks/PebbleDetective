package com.pebbledetective.ui.planetarium

import com.pebbledetective.data.PebbleEntry
import com.pebbledetective.domain.Planet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The short list of stones the planetarium offers for a focused world. */
class StonesFromTest {

    private fun entry(id: String, planet: Planet?, daysAgo: Long) = PebbleEntry(
        id = id,
        capturedAtEpochMs = 1_767_225_600_000L - daysAgo * 86_400_000L,
        timeZoneId = "Asia/Tokyo",
        planetId = planet?.id,
    )

    private val logbook = listOf(
        entry("a", Planet.MARS, 1),
        entry("b", Planet.JUPITER, 2),
        entry("c", Planet.MARS, 9),
        entry("d", null, 3),
        entry("e", Planet.MARS, 4),
    )

    @Test
    fun `only the stones from that world`() {
        assertEquals(
            listOf("a", "e", "c"),
            stonesFrom(logbook, Planet.MARS).map { it.id },
        )
        assertEquals(listOf("b"), stonesFrom(logbook, Planet.JUPITER).map { it.id })
    }

    /** Newest first, whatever order the logbook happens to be in. */
    @Test
    fun `newest first`() {
        val shuffled = logbook.sortedBy { it.capturedAtEpochMs }
        assertEquals(
            listOf("a", "e", "c"),
            stonesFrom(shuffled, Planet.MARS).map { it.id },
        )
    }

    /** Nothing focused, nothing to show - and no world of its own. */
    @Test
    fun `nothing without a world`() {
        assertTrue(stonesFrom(logbook, null).isEmpty())
        assertTrue(stonesFrom(logbook, Planet.NEPTUNE).isEmpty())
        assertTrue(stonesFrom(emptyList(), Planet.MARS).isEmpty())
    }

    /**
     * A pocketful of grey pebbles all land on the same world, and a strip
     * of twenty thumbnails is a second logbook rather than an answer.
     */
    @Test
    fun `capped at a glance`() {
        val many = (1..30).map { entry("s$it", Planet.MERCURY, it.toLong()) }
        val shown = stonesFrom(many, Planet.MERCURY)
        assertEquals(STONE_LIMIT, shown.size)
        // And it is the newest that survive the cap.
        assertEquals("s1", shown.first().id)
    }

    /** An unresearched stone has no world yet and belongs to none. */
    @Test
    fun `a stone with no world is in nobody's list`() {
        for (planet in Planet.entries) {
            assertTrue(stonesFrom(logbook, planet).none { it.id == "d" })
        }
    }
}

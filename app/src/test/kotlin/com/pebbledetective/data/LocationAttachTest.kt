package com.pebbledetective.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The location fix lands seconds after the planet is decided, on a separate
 * job. These cover the two ways that went wrong.
 */
class LocationAttachTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun repo() = PebbleRepository(File(folder.root, "pebbles"))

    private fun entry(id: String) = PebbleEntry(
        id = id,
        capturedAtEpochMs = 1_000L,
        timeZoneId = "Asia/Tokyo",
    )

    @Test
    fun `attaching a location keeps the planet that was decided first`() = runBlocking {
        val r = repo()
        r.append(entry("a"))

        // The research finishes and writes the planet...
        r.update(r.find("a")!!.copy(status = PebbleStatus.RESEARCHED, planetId = "mars"))
        // ...then the fix arrives and adds coordinates.
        r.updateWhere("a") { it.copy(latitude = 35.68, longitude = 139.69) }

        val saved = r.find("a")!!
        assertEquals("mars", saved.planetId)
        assertEquals(PebbleStatus.RESEARCHED, saved.status)
        assertEquals(35.68, saved.latitude!!, 1e-9)
        assertEquals(139.69, saved.longitude!!, 1e-9)
    }

    /**
     * The clobbering case: a caller holding a copy of the entry from before
     * the planet was written would otherwise write the planet back out.
     * updateWhere transforms whatever is current instead.
     */
    @Test
    fun `a stale copy cannot erase the planet`() = runBlocking {
        val r = repo()
        r.append(entry("a"))
        val stale = r.find("a")!!          // captured before research ran

        r.update(stale.copy(status = PebbleStatus.RESEARCHED, planetId = "saturn"))

        // The location job only knows the id, not the stale snapshot.
        r.updateWhere("a") { it.copy(latitude = 1.0, longitude = 2.0) }

        val saved = r.find("a")!!
        assertEquals("saturn", saved.planetId)
        assertNotNull(saved.latitude)
        assertNull("the stale snapshot must not have been written", stale.planetId)
    }

    /**
     * The order that actually bit: a cached fix returns instantly, so the
     * coordinates can be written *before* the planet is decided. The planet
     * write must not erase them on its way in.
     */
    @Test
    fun `a location that arrives first is not erased by the planet`() = runBlocking {
        val r = repo()
        r.append(entry("a"))

        // Fix lands immediately, before the colour analysis has finished.
        r.updateWhere("a") { it.copy(latitude = 35.68, longitude = 139.69) }
        // Then the planet is written.
        r.updateWhere("a") { it.copy(status = PebbleStatus.RESEARCHED, planetId = "mars") }

        val saved = r.find("a")!!
        assertEquals("mars", saved.planetId)
        assertNotNull("the planet write erased the location", saved.latitude)
        assertEquals(35.68, saved.latitude!!, 1e-9)
    }

    @Test
    fun `the location survives a reload from disk`() = runBlocking {
        val first = repo()
        first.append(entry("a"))
        first.updateWhere("a") { it.copy(latitude = 51.5, longitude = -0.12) }

        val reopened = repo()
        reopened.load()
        val saved = reopened.find("a")!!
        assertEquals(51.5, saved.latitude!!, 1e-9)
        assertEquals(-0.12, saved.longitude!!, 1e-9)
    }

    @Test
    fun `updating an entry that is gone is harmless`() = runBlocking {
        val r = repo()
        r.append(entry("a"))
        r.delete("a")
        r.updateWhere("a") { it.copy(latitude = 1.0) }
        assertNull(r.find("a"))
    }

    @Test
    fun `a late fix for an earlier pebble does not disturb later ones`() = runBlocking {
        val r = repo()
        r.append(entry("first"))
        r.append(entry("second").copy(capturedAtEpochMs = 2_000L))
        r.update(r.find("second")!!.copy(planetId = "venus"))

        // The first pebble's fix finally arrives, long after the child moved on.
        r.updateWhere("first") { it.copy(latitude = 10.0, longitude = 20.0) }

        assertEquals(10.0, r.find("first")!!.latitude!!, 1e-9)
        assertEquals("venus", r.find("second")!!.planetId)
        assertNull(r.find("second")!!.latitude)
        assertEquals(2, r.entries.value.size)
    }
}

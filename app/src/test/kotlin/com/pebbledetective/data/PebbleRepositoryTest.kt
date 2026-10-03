package com.pebbledetective.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PebbleRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun repo(dir: File = folder.root, onDelete: suspend (String) -> Unit = {}) =
        PebbleRepository(File(dir, "pebbles"), onDelete)

    private fun entry(id: String, at: Long = 1_000L) = PebbleEntry(
        id = id,
        capturedAtEpochMs = at,
        timeZoneId = "Asia/Tokyo",
    )

    @Test
    fun `round-trips entries through the file`() = runBlocking {
        val first = repo()
        first.append(entry("a", at = 100))
        first.append(entry("b", at = 200))

        val reopened = repo()
        reopened.load()
        assertEquals(listOf("b", "a"), reopened.entries.value.map { it.id })
    }

    @Test
    fun `newest pebble comes first`() = runBlocking {
        val r = repo()
        r.append(entry("old", at = 1))
        r.append(entry("new", at = 9_999))
        assertEquals("new", r.entries.value.first().id)
    }

    @Test
    fun `an update supersedes the earlier record`() = runBlocking {
        val r = repo()
        r.append(entry("a"))
        r.update(entry("a").copy(status = PebbleStatus.RESEARCHED, planetId = "mars"))

        val reopened = repo()
        reopened.load()
        assertEquals(1, reopened.entries.value.size)
        assertEquals("mars", reopened.entries.value.single().planetId)
        assertEquals(PebbleStatus.RESEARCHED, reopened.entries.value.single().status)
    }

    /**
     * The whole reason the index is JSON Lines rather than one JSON array: a
     * process killed mid-write must cost at most the last record, never the
     * entire logbook.
     */
    @Test
    fun `survives a torn final line`() = runBlocking {
        val r = repo()
        r.append(entry("a", at = 100))
        r.append(entry("b", at = 200))

        val index = File(File(folder.root, "pebbles"), "index.jsonl")
        index.appendText("""{"id":"c","capturedAtEpoch""")  // interrupted write

        val reopened = repo()
        reopened.load()
        assertEquals(listOf("b", "a"), reopened.entries.value.map { it.id })
    }

    @Test
    fun `ignores blank lines`() = runBlocking {
        val r = repo()
        r.append(entry("a"))
        File(File(folder.root, "pebbles"), "index.jsonl").appendText("\n\n   \n")

        val reopened = repo()
        reopened.load()
        assertEquals(1, reopened.entries.value.size)
    }

    @Test
    fun `an empty logbook loads as empty rather than failing`() = runBlocking {
        val r = repo()
        r.load()
        assertTrue(r.entries.value.isEmpty())
    }

    @Test
    fun `delete removes the record and asks for the photos to go too`() = runBlocking {
        val deleted = mutableListOf<String>()
        val r = repo(onDelete = { deleted += it })
        r.append(entry("a"))
        r.append(entry("b"))
        r.delete("a")

        assertEquals(listOf("b"), r.entries.value.map { it.id })
        assertEquals(listOf("a"), deleted)

        val reopened = repo()
        reopened.load()
        assertEquals(listOf("b"), reopened.entries.value.map { it.id })
    }

    @Test
    fun `find locates a loaded entry and returns null otherwise`() = runBlocking {
        val r = repo()
        r.append(entry("a"))
        assertEquals("a", r.find("a")?.id)
        assertNull(r.find("nope"))
    }

    @Test
    fun `appending many entries keeps them all`() = runBlocking {
        val r = repo()
        repeat(200) { r.append(entry("id$it", at = it.toLong())) }

        val reopened = repo()
        reopened.load()
        assertEquals(200, reopened.entries.value.size)
    }
}

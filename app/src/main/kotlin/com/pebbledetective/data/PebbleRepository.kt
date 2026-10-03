package com.pebbledetective.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Whether a pebble has been through Deep Research yet. */
enum class PebbleStatus { CAPTURED, RESEARCHED }

/**
 * One researched pebble.
 *
 * [planetId] is stored, never recomputed. Grey pebbles pick their planet at
 * random, so recomputing on replay would show a different answer every time
 * the logbook entry is opened.
 */
@Serializable
data class PebbleEntry(
    val id: String,
    val capturedAtEpochMs: Long,
    val timeZoneId: String,
    val status: PebbleStatus = PebbleStatus.CAPTURED,
    val planetId: String? = null,
    val dominantColourArgb: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * The logbook, stored as JSON Lines in internal storage.
 *
 * One record per line, appended. That is O(1) per capture and crash
 * tolerant: a kill mid-write costs at most a trailing partial line, which is
 * dropped on read. Rewriting a whole JSON array on every capture would be
 * O(n) *and* a window in which the entire history can be lost - a much worse
 * failure for something a child is meant to keep.
 *
 * Edits and deletes are rarer, so those compact the file through a temporary
 * file and an atomic rename.
 */
class PebbleRepository(
    directory: File,
    /** Called when an entry is removed, so its photographs go too. */
    private val onDelete: suspend (String) -> Unit = {},
) {
    private val file = File(directory.apply { mkdirs() }, "index.jsonl")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _entries = MutableStateFlow<List<PebbleEntry>>(emptyList())

    /** Newest first, which is the order the logbook shows them. */
    val entries: StateFlow<List<PebbleEntry>> = _entries.asStateFlow()

    suspend fun load() = mutex.withLock {
        _entries.value = withContext(Dispatchers.IO) { readAll() }
    }

    suspend fun append(entry: PebbleEntry) = mutex.withLock {
        withContext(Dispatchers.IO) {
            file.appendText(json.encodeToString(entry) + "\n")
        }
        _entries.value = (_entries.value + entry).sortedByDescending { it.capturedAtEpochMs }
    }

    /** Replaces an entry in place, compacting the file. */
    suspend fun update(entry: PebbleEntry) = mutex.withLock {
        val updated = _entries.value.map { if (it.id == entry.id) entry else it }
        withContext(Dispatchers.IO) { rewrite(updated) }
        _entries.value = updated.sortedByDescending { it.capturedAtEpochMs }
    }

    suspend fun delete(id: String) = mutex.withLock {
        val remaining = _entries.value.filterNot { it.id == id }
        withContext(Dispatchers.IO) { rewrite(remaining) }
        onDelete(id)
        _entries.value = remaining
    }

    fun find(id: String): PebbleEntry? = _entries.value.firstOrNull { it.id == id }

    private fun readAll(): List<PebbleEntry> {
        if (!file.exists()) return emptyList()
        val parsed = file.readLines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            // A partial trailing line from an interrupted write is expected,
            // and is simply skipped rather than failing the whole logbook.
            runCatching { json.decodeFromString<PebbleEntry>(trimmed) }.getOrNull()
        }
        // Later lines win, so an updated record supersedes its earlier self.
        return parsed.associateBy { it.id }.values.sortedByDescending { it.capturedAtEpochMs }
    }

    private fun rewrite(entries: List<PebbleEntry>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(entries.joinToString("") { json.encodeToString(it) + "\n" })
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

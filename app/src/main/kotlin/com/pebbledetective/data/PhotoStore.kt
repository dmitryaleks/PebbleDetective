package com.pebbledetective.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photographs of pebbles, on internal storage.
 *
 * Nothing here is world-readable and nothing is written to the shared gallery:
 * a child's photographs stay inside the app unless they are explicitly
 * exported.
 *
 * Each pebble gets a full-size JPEG and a thumbnail. The logbook only ever
 * decodes thumbnails - decoding fifty full-size frames into a scrolling list
 * is how you jank or run out of memory.
 */
class PhotoStore(context: Context) {

    private val root = File(context.filesDir, "pebbles").apply { mkdirs() }
    private val thumbs = File(root, "thumbs").apply { mkdirs() }

    /**
     * Decoded thumbnails, bounded by bytes.
     *
     * The logbook list and the gallery's thumbnail strip both scroll through
     * these, and decoding a JPEG from disk on every recycle is visible as
     * stutter. Full photographs are deliberately *not* cached - they are
     * ~6MB decoded and only one is on screen at a time.
     */
    private val thumbCache = object : LruCache<String, Bitmap>(THUMB_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun photoFile(id: String) = File(root, "$id.jpg")

    fun thumbFile(id: String) = File(thumbs, "$id.jpg")

    /** Writes the full photo and its thumbnail. The bitmap must already be upright. */
    suspend fun save(id: String, upright: Bitmap): Unit = withContext(Dispatchers.IO) {
        photoFile(id).outputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, FULL_QUALITY, out)
        }
        val thumb = upright.scaledToFit(THUMB_PX)
        thumbFile(id).outputStream().use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
        }
        if (thumb !== upright) thumb.recycle()
    }

    suspend fun loadPhoto(id: String): Bitmap? = withContext(Dispatchers.IO) {
        photoFile(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    suspend fun loadThumb(id: String): Bitmap? {
        thumbCache.get(id)?.let { return it }
        val decoded = withContext(Dispatchers.IO) {
            thumbFile(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        }
        if (decoded != null) thumbCache.put(id, decoded)
        return decoded
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        thumbCache.remove(id)
        photoFile(id).delete()
        thumbFile(id).delete()
    }

    /** Ids that have a photo on disk, used to rebuild a damaged index. */
    suspend fun knownIds(): Set<String> = withContext(Dispatchers.IO) {
        root.listFiles { f -> f.isFile && f.name.endsWith(".jpg") }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            .orEmpty()
    }

    /** Total bytes used by photographs, for the logbook's storage readout. */
    suspend fun bytesUsed(): Long = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private companion object {
        const val FULL_QUALITY = 88
        const val THUMB_QUALITY = 80
        const val THUMB_PX = 256

        /** Enough for a few dozen thumbnails, and a small slice of the heap. */
        val THUMB_CACHE_BYTES =
            (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(8L * 1024 * 1024).toInt()
    }
}

/** Longest edge scaled to [maxEdge], preserving aspect. Returns the original if already small. */
private fun Bitmap.scaledToFit(maxEdge: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxEdge) return this
    val scale = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}

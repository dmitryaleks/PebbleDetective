package com.pebbledetective.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    suspend fun loadThumb(id: String): Bitmap? = withContext(Dispatchers.IO) {
        thumbFile(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
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
    }
}

/** Longest edge scaled to [maxEdge], preserving aspect. Returns the original if already small. */
private fun Bitmap.scaledToFit(maxEdge: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxEdge) return this
    val scale = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}

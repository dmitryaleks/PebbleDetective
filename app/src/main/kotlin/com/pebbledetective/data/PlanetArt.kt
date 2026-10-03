package com.pebbledetective.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pebbledetective.domain.Planet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The bundled NASA photographs, prepared as discs that sit on a starfield.
 *
 * Used by the title sequence, which shows all nine bodies at once and so
 * cannot afford to decode nine full-size frames.
 */
object PlanetArt {

    /**
     * Decodes [planet] at roughly [targetPx] across, with space keyed out.
     *
     * Two things matter here. The decode is subsampled, because the splash
     * draws these a couple of hundred pixels wide and nine full frames would
     * cost tens of megabytes and a visible stall on the way into the app.
     * And the alpha comes from luminance rather than from a circular crop:
     * the frames are a body on black, so a plain crop leaves a black ring
     * around the smaller bodies, while keying lets the lit limb soften into
     * the stars the way it does on the banner.
     */
    suspend fun disc(context: Context, planet: Planet, targetPx: Int): Bitmap? =
        withContext(Dispatchers.Default) {
            val source = decodeSubsampled(context, planet, targetPx) ?: return@withContext null
            val square = square(source, targetPx)
            if (square !== source) source.recycle()

            val side = square.width
            val pixels = IntArray(side * square.height)
            square.getPixels(pixels, 0, side, 0, 0, side, square.height)
            val centre = (side - 1) / 2f
            for (y in 0 until square.height) {
                for (x in 0 until side) {
                    val i = y * side + x
                    val colour = pixels[i]
                    val r = (colour shr 16) and 0xFF
                    val g = (colour shr 8) and 0xFF
                    val b = colour and 0xFF
                    val luma = (r * 299 + g * 587 + b * 114) / 1000
                    // Below about 16 is space; ramp quickly so the body
                    // itself never goes translucent.
                    var alpha =
                        if (luma < SPACE_LEVEL) 0 else minOf(255, (luma - SPACE_LEVEL) * 7)

                    // And a circular mask on top. Keying alone is not enough
                    // for the Sun, whose frame is lit corner to corner: it
                    // came through as a bright square on the starfield.
                    val dx = (x - centre) / centre
                    val dy = (y - centre) / centre
                    val radius = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (radius > 1f) {
                        alpha = 0
                    } else if (radius > FEATHER) {
                        alpha = (alpha * (1f - (radius - FEATHER) / (1f - FEATHER))).toInt()
                    }
                    pixels[i] = (alpha shl 24) or (colour and 0x00FFFFFF)
                }
            }
            val keyed = Bitmap.createBitmap(
                pixels, side, square.height, Bitmap.Config.ARGB_8888,
            )
            square.recycle()
            keyed
        }

    private fun decodeSubsampled(context: Context, planet: Planet, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.assets.open(planet.assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
        }.getOrNull()

        var sample = 1
        val smallest = minOf(bounds.outWidth, bounds.outHeight)
        while (smallest > 0 && smallest / (sample * 2) >= targetPx) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            context.assets.open(planet.assetPath).use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    /** Centre-cropped to a square of [size], so nothing is squashed. */
    private fun square(source: Bitmap, size: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(
            source,
            (source.width - side) / 2,
            (source.height - side) / 2,
            side,
            side,
        )
        if (cropped.width == size) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        if (cropped !== source && cropped !== scaled) cropped.recycle()
        return scaled
    }

    private const val SPACE_LEVEL = 16

    /** Where the circular mask starts softening, as a fraction of the radius. */
    private const val FEATHER = 0.94f
}

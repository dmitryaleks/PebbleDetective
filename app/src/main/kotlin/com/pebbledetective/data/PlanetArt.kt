package com.pebbledetective.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pebbledetective.domain.Planet
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.pebbledetective.ui.sky.HALLEY_LOOK
import com.pebbledetective.ui.sky.drawComet
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
        if (planet == Planet.HALLEY) comet(targetPx) else disc(context, planet.assetPath, targetPx)

    /**
     * Halley, which has no photograph in the assets because it is drawn.
     *
     * Every screen that shows where a pebble came from asks this object
     * for a picture - the result screen, the logbook, the share card,
     * the banner on the camera. Rather than teach each of them that one
     * origin is special, the special case lives here and they all get a
     * bitmap like any other.
     *
     * Drawn rather than bundled because there is no photograph of a
     * comet that looks like the idea of a comet: the one close-up anyone
     * has of Halley is Giotto's, which is a dark potato, and the famous
     * long-tailed pictures are of other comets entirely. Reaching into
     * the drawing code from here crosses a layer, which is the price of
     * the other eight screens not having to care.
     */
    private suspend fun comet(targetPx: Int): Bitmap = withContext(Dispatchers.Default) {
        val side = targetPx.coerceAtLeast(64)
        val image = ImageBitmap(side, side)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(image),
            size = Size(side.toFloat(), side.toFloat()),
        ) {
            // Head to the right, tail streaming left across the frame.
            // A circular badge crops to the head and the brightest part
            // of the tail, which still reads as a comet; the result
            // screen, which does not crop, gets the whole thing.
            drawComet(
                look = HALLEY_LOOK,
                phase = 0f,
                at = Offset(side * 0.74f, side * 0.42f),
                headRadius = side * 0.085f,
                awayFromSun = Offset(-0.94f, 0.34f),
                seconds = 0f,
                tailScale = 0.62f,
            )
        }
        image.asAndroidBitmap()
    }

    /**
     * The same, for a bundled frame that is not one of the nine.
     *
     * @param keySpace whether to take the alpha from luminance. True suits a
     *   body photographed against black, where keying lets the lit limb melt
     *   into the starfield. False suits a frame already cropped to its disc:
     *   keying a dark ocean would make it half transparent and put stars
     *   through the Pacific, which is exactly what it did the first time.
     */
    suspend fun disc(
        context: Context,
        assetPath: String,
        targetPx: Int,
        keySpace: Boolean = true,
    ): Bitmap? =
        withContext(Dispatchers.Default) {
            val decoded = decodeSubsampled(context, assetPath, targetPx) ?: return@withContext null
            // Trim the black margin first. The library frames do not all
            // fill their own picture - Jupiter sits in a good deal of empty
            // space - so without this a body is drawn smaller than the
            // radius asked for, and anything positioned against that radius,
            // a rim light in particular, floats off the limb.
            val source = if (keySpace) trimToDisc(decoded) else decoded
            if (source !== decoded) decoded.recycle()
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
                    var alpha = when {
                        !keySpace -> 255
                        luma < SPACE_LEVEL -> 0
                        else -> minOf(255, (luma - SPACE_LEVEL) * 7)
                    }

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

    private fun decodeSubsampled(context: Context, assetPath: String, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
        }.getOrNull()

        var sample = 1
        val smallest = minOf(bounds.outWidth, bounds.outHeight)
        while (smallest > 0 && smallest / (sample * 2) >= targetPx) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            context.assets.open(assetPath).use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    /**
     * Crops away the space around the body.
     *
     * Scans for the bounding box of everything brighter than empty sky and
     * takes the square around it, so the returned bitmap is the body and
     * nothing else. The threshold is a little above the keying level: a
     * handful of stray bright pixels in a corner would otherwise keep the
     * whole frame.
     */
    private fun trimToDisc(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val colour = pixels[row + x]
                val luma = (((colour shr 16) and 0xFF) * 299 +
                    ((colour shr 8) and 0xFF) * 587 +
                    (colour and 0xFF) * 114) / 1000
                if (luma <= TRIM_LEVEL) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right <= left || bottom <= top) return source

        // A square around the middle of what was found, so a body that is
        // not quite centred in its frame is not squashed to one side.
        val centreX = (left + right) / 2
        val centreY = (top + bottom) / 2
        val half = maxOf(right - left, bottom - top) / 2 + 1
        val x0 = (centreX - half).coerceAtLeast(0)
        val y0 = (centreY - half).coerceAtLeast(0)
        val x1 = (centreX + half).coerceAtMost(width)
        val y1 = (centreY + half).coerceAtMost(height)
        if (x1 - x0 < 2 || y1 - y0 < 2) return source
        return Bitmap.createBitmap(source, x0, y0, x1 - x0, y1 - y0)
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

    /**
     * The Earth the journey flies to: a DSCOVR frame of the sunlit disc
     * centred near 135 east, so Japan is facing the viewer as the pebble
     * comes in. The nine library photographs are all the wrong hemisphere.
     */
    const val EARTH_JAPAN_SIDE = "planets/earth_east.jpg"

    private const val SPACE_LEVEL = 16

    /** Above the keying level, so a stray hot pixel cannot widen the crop. */
    private const val TRIM_LEVEL = 26

    /** Where the circular mask starts softening, as a fraction of the radius. */
    private const val FEATHER = 0.94f
}

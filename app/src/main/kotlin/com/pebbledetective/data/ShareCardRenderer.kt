package com.pebbledetective.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/** The text for a share card, already localised by the caller. */
data class ShareCardText(
    val title: String,
    val planetName: String,
    val dateTime: String,
    val coordinates: String?,
    val footer: String,
)

/**
 * Draws a one-page summary of a pebble, for sending to someone.
 *
 * Everything a relative needs in a single image: the photograph, which world
 * it came from, when it was found and where. Drawn rather than screenshotted
 * so it looks composed instead of like a cropped phone screen, and so no UI
 * chrome or status bar goes along with it.
 */
class ShareCardRenderer(private val context: Context) {

    /**
     * Builds the card and returns a content URI another app can read.
     *
     * Written to cacheDir and shared through FileProvider: the file is
     * readable only by whichever app the share sheet hands it to, and the
     * system clears it out later.
     */
    suspend fun render(
        entryId: String,
        photo: Bitmap,
        planet: Bitmap?,
        text: ShareCardText,
    ): Uri? = withContext(Dispatchers.Default) {
        val card = draw(photo, planet, text)
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, "pebble-$entryId.png")

        val written = withContext(Dispatchers.IO) {
            runCatching {
                file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.isSuccess
        }
        card.recycle()
        if (!written) return@withContext null

        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    private fun draw(photo: Bitmap, planet: Bitmap?, text: ShareCardText): Bitmap {
        val card = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(card)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.drawColor(BACKGROUND)
        drawStarfield(canvas, paint)

        val margin = 72f
        var y = 96f

        paint.reset()
        paint.isAntiAlias = true
        paint.color = AMBER
        paint.textSize = 38f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.letterSpacing = 0.14f
        canvas.drawText(text.title.uppercase(), margin, y, paint)
        paint.letterSpacing = 0f
        y += 54f

        // The photograph, square cropped, with a green frame.
        val photoSize = WIDTH - margin * 2
        val photoRect = RectF(margin, y, margin + photoSize, y + photoSize)
        drawRoundedBitmap(canvas, photo, photoRect, 36f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = GREEN
        canvas.drawRoundRect(photoRect, 36f, 36f, paint)
        paint.style = Paint.Style.FILL
        y += photoSize + 72f

        // The world it came from: disc on the left, name on the right.
        val discSize = 190f
        if (planet != null) {
            drawCircularBitmap(canvas, planet, margin + discSize / 2, y + discSize / 2, discSize / 2, paint)
        }
        paint.color = GREEN
        paint.textSize = 92f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(text.planetName, margin + discSize + 36f, y + discSize * 0.56f, paint)
        y += discSize + 56f

        paint.color = FOREGROUND
        paint.textSize = 40f
        paint.typeface = Typeface.SANS_SERIF
        canvas.drawText(text.dateTime, margin, y, paint)
        y += 58f

        text.coordinates?.let { coordinates ->
            paint.color = AMBER
            // A simple pin: a filled circle with a tail.
            canvas.drawCircle(margin + 12f, y - 14f, 13f, paint)
            paint.color = FOREGROUND
            canvas.drawText(coordinates, margin + 44f, y, paint)
            y += 58f
        }

        paint.color = MUTED
        paint.textSize = 32f
        canvas.drawText(text.footer, margin, HEIGHT - 56f, paint)

        return card
    }

    private fun drawStarfield(canvas: Canvas, paint: Paint) {
        // A faint wash so the card is not flat black.
        paint.shader = RadialGradient(
            WIDTH * 0.8f, HEIGHT * 0.12f, WIDTH * 0.9f,
            intArrayOf(0x2A1D2E5A, BACKGROUND), null, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null

        val random = Random(20261003)
        repeat(260) {
            val brightness = random.nextInt(60, 220)
            paint.color = Color.rgb(brightness, brightness, brightness)
            canvas.drawCircle(
                random.nextFloat() * WIDTH,
                random.nextFloat() * HEIGHT,
                if (brightness > 170) 2.4f else 1.5f,
                paint,
            )
        }
    }

    /** Centre-cropped into [target], so a tall photo is not squashed. */
    private fun drawRoundedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        target: RectF,
        radius: Float,
        paint: Paint,
    ) {
        val side = minOf(bitmap.width, bitmap.height)
        val src = Rect(
            (bitmap.width - side) / 2,
            (bitmap.height - side) / 2,
            (bitmap.width + side) / 2,
            (bitmap.height + side) / 2,
        )
        val scaled = Bitmap.createBitmap(
            bitmap, src.left, src.top, src.width(), src.height(),
        )
        val shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(
            Matrix().apply {
                setScale(target.width() / scaled.width, target.height() / scaled.height)
                postTranslate(target.left, target.top)
            }
        )
        paint.shader = shader
        canvas.drawRoundRect(target, radius, radius, paint)
        paint.shader = null
        if (scaled !== bitmap) scaled.recycle()
    }

    /**
     * The planet disc, lifted off the black background it ships on.
     *
     * The NASA frames are a body on black space, so a plain circular crop
     * leaves a dark ring around the smaller bodies. The alpha is keyed off
     * luminance instead, which lets the planet sit on the starfield the way
     * it does on the banner.
     */
    private fun drawCircularBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        centreX: Float,
        centreY: Float,
        radius: Float,
        paint: Paint,
    ) {
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createScaledBitmap(
            Bitmap.createBitmap(
                bitmap,
                (bitmap.width - side) / 2,
                (bitmap.height - side) / 2,
                side,
                side,
            ),
            (radius * 2).toInt().coerceAtLeast(1),
            (radius * 2).toInt().coerceAtLeast(1),
            true,
        )

        val pixels = IntArray(square.width * square.height)
        square.getPixels(pixels, 0, square.width, 0, 0, square.width, square.height)
        for (i in pixels.indices) {
            val colour = pixels[i]
            val r = (colour shr 16) and 0xFF
            val g = (colour shr 8) and 0xFF
            val b = colour and 0xFF
            val luma = (r * 299 + g * 587 + b * 114) / 1000
            // Below ~16 is space; ramp quickly so the body stays solid.
            val alpha = if (luma < 16) 0 else minOf(255, (luma - 16) * 7)
            pixels[i] = (alpha shl 24) or (colour and 0x00FFFFFF)
        }
        val keyed = Bitmap.createBitmap(pixels, square.width, square.height, Bitmap.Config.ARGB_8888)

        canvas.drawBitmap(keyed, centreX - radius, centreY - radius, paint)
        square.recycle()
        keyed.recycle()
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1620
        const val SHARE_DIR = "shared"
        const val BACKGROUND = 0xFF05070D.toInt()
        const val FOREGROUND = 0xFFE6ECF5.toInt()
        const val MUTED = 0xFF8A93A8.toInt()
        const val GREEN = 0xFF4DFFA6.toInt()
        const val AMBER = 0xFFFFC65C.toInt()
    }
}

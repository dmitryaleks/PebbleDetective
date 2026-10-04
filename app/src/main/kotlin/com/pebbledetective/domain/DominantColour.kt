package com.pebbledetective.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Hue in degrees (0..360), saturation and value in 0..1. */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/**
 * Works out the colour of the pebble the child pointed at.
 *
 * Pure: takes raw ARGB pixels and no Android types, so it unit tests on the
 * JVM.
 *
 * `androidx.palette` is deliberately not used. Its quantiser is tuned for
 * album art and biases hard toward vibrant colours, which is exactly wrong
 * for grey-brown rocks.
 *
 * Two things that quietly ruin this if you skip them:
 *
 *  * **Hue is circular.** Averaging 350 degrees and 10 degrees gives 180 -
 *    cyan - instead of red. The hue comes from the modal bin of a histogram,
 *    and within that bin from a circular mean.
 *  * **Wet and shiny pebbles blow out to specular white.** Pixels brighter
 *    than [MAX_VALUE] are discarded, as are near-black shadow pixels, or the
 *    highlight dominates the result.
 */
fun dominantColour(
    pixels: IntArray,
    width: Int,
    height: Int,
    centreX: Int,
    centreY: Int,
    radius: Int,
): Hsv {
    val bins = FloatArray(HUE_BINS)
    val saturations = ArrayList<Float>()
    val values = ArrayList<Float>()
    val sinSum = FloatArray(HUE_BINS)
    val cosSum = FloatArray(HUE_BINS)

    val r2 = radius.toLong() * radius
    val xFrom = max(0, centreX - radius)
    val xTo = min(width - 1, centreX + radius)
    val yFrom = max(0, centreY - radius)
    val yTo = min(height - 1, centreY + radius)

    for (y in yFrom..yTo) {
        val dy = (y - centreY).toLong()
        for (x in xFrom..xTo) {
            val dx = (x - centreX).toLong()
            if (dx * dx + dy * dy > r2) continue

            val hsv = pixels[y * width + x].toHsv()
            if (hsv.value < MIN_VALUE || hsv.value > MAX_VALUE) continue

            saturations += hsv.saturation
            values += hsv.value

            val bin = ((hsv.hue / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
            // Weight by saturation: grey pixels should not vote on hue.
            bins[bin] += hsv.saturation
            val radians = Math.toRadians(hsv.hue.toDouble())
            sinSum[bin] += (sin(radians) * hsv.saturation).toFloat()
            cosSum[bin] += (cos(radians) * hsv.saturation).toFloat()
        }
    }

    if (saturations.isEmpty()) return Hsv(0f, 0f, 0f)

    val modal = bins.indices.maxBy { bins[it] }
    val hue = if (bins[modal] <= 0f) {
        0f
    } else {
        val degrees = Math.toDegrees(atan2(sinSum[modal].toDouble(), cosSum[modal].toDouble()))
        ((degrees + 360.0) % 360.0).toFloat()
    }

    return Hsv(hue, saturations.median(), values.median())
}

/**
 * Maps a pebble's colour onto a body of the solar system.
 *
 * Grey or colourless pebbles pick at random, as the brief asks. The caller
 * must persist the result: recomputing it when a logbook entry is reopened
 * would show a different planet every time, which a child will notice and
 * which reads as the app being broken.
 */
fun pickPlanet(colour: Hsv, random: Random = Random.Default): Planet {
    // Very dark rock, before the saturation test - a near-black pebble has
    // unreliable hue and saturation.
    if (colour.value < 0.22f) return Planet.MERCURY

    // Grey or unknown: the brief says pick at random.
    if (colour.saturation < 0.18f) {
        if (colour.saturation >= 0.12f && colour.hue in 25f..60f) return Planet.SATURN
        // Except for a pale grey stone, which is the Moon and nothing else.
        // It is the one world in the game a child has looked straight at,
        // and it is exactly this colour; sending that stone to a random
        // planet throws away the one guess they could have made themselves.
        if (colour.value >= 0.55f) return Planet.MOON
        return Planet.sources.random(random)
    }

    return when (colour.hue) {
        in 0f..15f, in 330f..360f -> Planet.MARS
        in 15f..40f -> Planet.JUPITER
        in 40f..65f -> if (colour.value >= 0.80f) Planet.SUN else Planet.VENUS
        in 65f..205f -> Planet.URANUS
        in 205f..280f -> Planet.NEPTUNE
        else -> Planet.MARS
    }
}

private fun Int.toHsv(): Hsv {
    val r = ((this shr 16) and 0xFF) / 255f
    val g = ((this shr 8) and 0xFF) / 255f
    val b = (this and 0xFF) / 255f

    val maxC = maxOf(r, g, b)
    val minC = minOf(r, g, b)
    val delta = maxC - minC

    val hue = when {
        delta == 0f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return Hsv(
        hue = (hue + 360f) % 360f,
        saturation = if (maxC == 0f) 0f else delta / maxC,
        value = maxC,
    )
}

private fun List<Float>.median(): Float {
    val sorted = sorted()
    return sorted[sorted.size / 2]
}

private const val HUE_BINS = 24
private const val MIN_VALUE = 0.12f
private const val MAX_VALUE = 0.96f

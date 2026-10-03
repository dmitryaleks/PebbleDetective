package com.pebbledetective.ui.journey

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pebbledetective.ui.theme.ScannerGreen
import com.pebbledetective.ui.theme.SignalAmber
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** A point on the globe, in degrees. */
data class LonLat(val lon: Double, val lat: Double)

/** Where the pebble comes down: Koto ward, on the east side of Tokyo Bay. */
val LANDING_SITE = LonLat(139.8170, 35.6720)

/**
 * The slice of the world currently on screen.
 *
 * Longitude is squeezed by the cosine of the latitude, otherwise Japan comes
 * out noticeably too wide - at 36 degrees north a degree of longitude covers
 * only about four fifths of what a degree of latitude does.
 */
private class Viewport(val centre: LonLat, spanDegrees: Double, size: Size) {
    private val scale = min(size.width, size.height) / spanDegrees
    private val lonScale = cos(Math.toRadians(centre.lat))
    private val midX = size.width / 2f
    private val midY = size.height / 2f

    /** How much of the globe is on screen, in degrees. */
    val spanLat = size.height / scale
    val spanLon = size.width / scale / lonScale

    /** How many pixels a span of degrees of latitude covers. */
    fun pixelsPerDegree(): Float = scale.toFloat()

    fun project(point: LonLat): Offset = Offset(
        x = midX + ((point.lon - centre.lon) * lonScale * scale).toFloat(),
        y = midY - ((point.lat - centre.lat) * scale).toFloat(),
    )
}

/**
 * The last leg of the journey: the whole of Japan zooming in to the streets
 * of Koto, with the pebble dropping into the middle of it.
 *
 * Deliberately schematic - coastlines, a bay, the loop line and a river -
 * rather than a map. It reads as the detective's display rather than
 * pretending to be satellite imagery, which keeps it of a piece with the
 * schematic planets earlier in the flight.
 *
 * @param zoom 0 for the whole country, 1 for the landing site.
 * @param landedFraction 0 until touchdown, then 0..1 as the impact spreads.
 * @param alpha fades the whole scene in behind the atmospheric entry glow.
 */
fun DrawScope.drawDescent(zoom: Float, landedFraction: Float, alpha: Float) {
    if (alpha <= 0.01f) return

    // Interpolated geometrically. A linear zoom appears to stall at the end,
    // because each step then covers a smaller fraction of what is left.
    val span = COUNTRY_SPAN * (SITE_SPAN / COUNTRY_SPAN).pow(zoom.toDouble())

    // The pan is tied to the span rather than to the zoom parameter. With a
    // linear pan the map scale collapses far faster than the camera travels,
    // and Tokyo slides off the edge of the screen two thirds of the way
    // down; tying the two together holds the landing site in frame the whole
    // way and brings it to the centre exactly as the zoom finishes.
    val pan = 1.0 - (span - SITE_SPAN) / (COUNTRY_SPAN - SITE_SPAN)
    val centre = LonLat(
        lon = COUNTRY_CENTRE.lon + (LANDING_SITE.lon - COUNTRY_CENTRE.lon) * pan,
        lat = COUNTRY_CENTRE.lat + (LANDING_SITE.lat - COUNTRY_CENTRE.lat) * pan,
    )
    val view = Viewport(centre, span, size)

    // Three levels of detail, each handed over before the one before it is
    // blown up past what its handful of points can carry: the islands of
    // Japan, then the coast of Kanto with Tokyo Bay cut into it, then the
    // city furniture. The thresholds are in zoom, but they were chosen by
    // map scale - roughly 450km, 250km and 130km across.
    val islands = ((0.27f - zoom) / 0.07f).coerceIn(0f, 1f) * alpha
    val coast = ((zoom - 0.21f) / 0.07f).coerceIn(0f, 1f) * alpha
    val city = ((zoom - 0.42f) / 0.15f).coerceIn(0f, 1f) * alpha

    drawSea(alpha)
    if (islands > 0.01f) drawIslands(view, islands)
    if (coast > 0.01f) drawKanto(view, coast, city)
    drawLandingSite(view, zoom, landedFraction, alpha)
    if (landedFraction <= 0f) drawFallingPebble(view, zoom, alpha)
}

private fun DrawScope.drawSea(alpha: Float) {
    drawRect(color = SEA.copy(alpha = alpha))

    // A faint graticule, so the zoom is legible even over empty water.
    val step = size.minDimension / 9f
    val colour = ScannerGreen.copy(alpha = 0.06f * alpha)
    var x = 0f
    while (x < size.width) {
        drawLine(colour, Offset(x, 0f), Offset(x, size.height))
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(colour, Offset(0f, y), Offset(size.width, y))
        y += step
    }
}

private fun DrawScope.drawIslands(view: Viewport, alpha: Float) {
    for (island in JAPAN) {
        val path = Path()
        island.forEachIndexed { index, point ->
            val at = view.project(point)
            if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
        }
        path.close()
        drawPath(path, color = ScannerGreen.copy(alpha = alpha * 0.16f))
        drawPath(
            path = path,
            color = ScannerGreen.copy(alpha = alpha * 0.85f),
            style = Stroke(width = 2f * density),
        )
    }
}

/**
 * Kanto: the coast around Tokyo Bay, with the city drawn on top of it.
 *
 * One land polygon rather than a bay floating in the sea, so the water is
 * simply where the land is not - which is what makes the bay recognisable.
 *
 * @param alpha the coastline itself.
 * @param detail the streets, the loop line and the river, which only earn
 *   their place once the map is close enough for them to be distinct.
 */
private fun DrawScope.drawKanto(view: Viewport, alpha: Float, detail: Float) {
    val land = Path()
    KANTO.forEachIndexed { index, point ->
        val at = view.project(point)
        if (index == 0) land.moveTo(at.x, at.y) else land.lineTo(at.x, at.y)
    }
    land.close()
    drawPath(land, color = ScannerGreen.copy(alpha = alpha * 0.16f))
    drawPath(
        path = land,
        color = ScannerGreen.copy(alpha = alpha * 0.85f),
        style = Stroke(width = 2f * density),
    )

    if (detail <= 0.01f) return

    // Clipped to the land, so no road runs out across the water.
    clipPath(land) {
        drawBlocks(view, detail)

        // The Yamanote loop, the most recognisable shape in the city from
        // above, with the main roads radiating out of it.
        val hub = view.project(YAMANOTE_CENTRE)
        val rim = view.project(LonLat(YAMANOTE_CENTRE.lon, YAMANOTE_CENTRE.lat + LOOP_DEGREES))
        val loop = hub.y - rim.y

        // Stubs out of the hub, not lines across the whole screen: drawn
        // full length they stopped reading as roads and became a star.
        val reach = loop * 1.9f
        for (i in 0 until 6) {
            val angle = Math.toRadians(i * 60.0 + 15.0)
            drawLine(
                color = ScannerGreen.copy(alpha = detail * 0.35f),
                start = hub,
                end = Offset(
                    hub.x + (cos(angle) * reach).toFloat(),
                    hub.y + (sin(angle) * reach).toFloat(),
                ),
                strokeWidth = 2f * density,
            )
        }
        if (loop > 2f) {
            drawCircle(
                color = ScannerGreen.copy(alpha = detail * 0.75f),
                radius = loop,
                center = hub,
                style = Stroke(width = 2.5f * density),
            )
        }
    }

    // The Sumida and the Arakawa, with Koto between them as it really is.
    for (course in listOf(SUMIDA, ARAKAWA)) {
        val river = Path()
        course.forEachIndexed { index, point ->
            val at = view.project(point)
            if (index == 0) river.moveTo(at.x, at.y) else river.lineTo(at.x, at.y)
        }
        drawPath(
            path = river,
            color = RIVER.copy(alpha = detail),
            style = Stroke(width = 4f * density, cap = StrokeCap.Round),
        )
    }
}

/**
 * City blocks: a grid on the ground rather than on the screen, so it grows
 * with the zoom and gives the last few seconds a sense of coming down.
 */
private fun DrawScope.drawBlocks(view: Viewport, alpha: Float) {
    // Only once a block is big enough to be a block. From higher up the
    // grid would be a solid wash and thousands of lines a frame.
    if (view.pixelsPerDegree() * BLOCK.toFloat() < 8f) return

    val colour = ScannerGreen.copy(alpha = 0.16f * alpha)
    val top = view.centre.lat + view.spanLat / 2
    val bottom = view.centre.lat - view.spanLat / 2
    val left = view.centre.lon - view.spanLon / 2
    val right = view.centre.lon + view.spanLon / 2

    var lon = Math.floor(left / BLOCK) * BLOCK
    while (lon <= right) {
        drawLine(colour, view.project(LonLat(lon, top)), view.project(LonLat(lon, bottom)), density)
        lon += BLOCK
    }
    var lat = Math.floor(bottom / BLOCK) * BLOCK
    while (lat <= top) {
        drawLine(colour, view.project(LonLat(left, lat)), view.project(LonLat(right, lat)), density)
        lat += BLOCK
    }
}

/** About a kilometre, which is roughly a block of central Tokyo. */
private const val BLOCK = 0.01

/** The radius of the loop line, in degrees of latitude. */
private const val LOOP_DEGREES = 0.030

/** The target: a closing crosshair while falling, then the impact. */
private fun DrawScope.drawLandingSite(
    view: Viewport,
    zoom: Float,
    landedFraction: Float,
    alpha: Float,
) {
    val at = view.project(LANDING_SITE)
    val landed = landedFraction > 0f
    val colour = if (landed) SignalAmber else ScannerGreen

    // A ring that tightens as the descent proceeds, so the eye is pulled in.
    val ring = size.minDimension * (0.34f - 0.27f * zoom).coerceAtLeast(0.07f)
    drawCircle(
        color = colour.copy(alpha = 0.5f * alpha),
        radius = ring,
        center = at,
        style = Stroke(width = 2f * density),
    )
    val tick = colour.copy(alpha = 0.65f * alpha)
    drawLine(tick, Offset(at.x - ring, at.y), Offset(at.x - ring * 0.45f, at.y), 2f * density)
    drawLine(tick, Offset(at.x + ring * 0.45f, at.y), Offset(at.x + ring, at.y), 2f * density)
    drawLine(tick, Offset(at.x, at.y - ring), Offset(at.x, at.y - ring * 0.45f), 2f * density)
    drawLine(tick, Offset(at.x, at.y + ring * 0.45f), Offset(at.x, at.y + ring), 2f * density)

    if (landed) {
        // Rings spreading out from the spot, staggered so they read as waves.
        for (wave in 0..2) {
            val phase = (landedFraction * 1.5f - wave * 0.25f).coerceIn(0f, 1f)
            if (phase <= 0f) continue
            drawCircle(
                color = SignalAmber.copy(alpha = (1f - phase) * 0.6f * alpha),
                radius = size.minDimension * (0.04f + phase * 0.32f),
                center = at,
                style = Stroke(width = 2.5f * density),
            )
        }
    }
    drawCircle(color = colour.copy(alpha = alpha), radius = 7f * density, center = at)
}

/** The pebble itself, still falling in towards the site. */
private fun DrawScope.drawFallingPebble(view: Viewport, zoom: Float, alpha: Float) {
    val target = view.project(LANDING_SITE)
    // Comes in from above and a little to the side, closing on the target.
    val remaining = 1f - zoom
    val at = Offset(
        x = target.x + size.width * 0.18f * remaining,
        y = target.y - size.height * 0.46f * remaining,
    )
    val radius = (4f + 5f * zoom) * density

    // A short trail back along the way it came.
    drawLine(
        color = SignalAmber.copy(alpha = 0.45f * alpha),
        start = Offset(
            at.x + size.width * 0.05f * remaining + 6f * density,
            at.y - size.height * 0.12f * remaining - 10f * density,
        ),
        end = at,
        strokeWidth = 3f * density,
        cap = StrokeCap.Round,
    )
    drawCircle(SignalAmber.copy(alpha = 0.30f * alpha), radius * 2.4f, at)
    drawCircle(SignalAmber.copy(alpha = alpha), radius, at)
}

private val SEA = Color(0xFF04121E)
private val RIVER = Color(0xFF2E7FB8)

private const val COUNTRY_SPAN = 15.0
private const val SITE_SPAN = 0.17
private val COUNTRY_CENTRE = LonLat(137.5, 37.6)
private val YAMANOTE_CENTRE = LonLat(139.7450, 35.6850)

// Coarse outlines: these are drawn a few hundred pixels across and are meant
// to read as a diagram, not as a chart anyone could navigate by.
private val HONSHU = listOf(
    LonLat(130.9, 33.9), LonLat(132.0, 34.3), LonLat(133.0, 34.4), LonLat(134.5, 34.7),
    LonLat(135.4, 34.6), LonLat(136.8, 34.6), LonLat(137.5, 34.6), LonLat(138.3, 34.6),
    LonLat(139.0, 35.0), LonLat(139.8, 35.6), LonLat(140.9, 35.7), LonLat(141.0, 36.5),
    LonLat(141.0, 38.3), LonLat(141.5, 39.5), LonLat(141.8, 40.5), LonLat(141.0, 41.2),
    LonLat(140.3, 41.5), LonLat(139.9, 40.5), LonLat(139.8, 39.5), LonLat(139.9, 38.5),
    LonLat(138.5, 37.8), LonLat(137.2, 37.0), LonLat(136.7, 36.8), LonLat(136.0, 35.8),
    LonLat(135.5, 35.5), LonLat(134.5, 35.6), LonLat(133.0, 35.5), LonLat(132.0, 35.4),
    LonLat(131.0, 34.5),
)

private val HOKKAIDO = listOf(
    LonLat(140.0, 42.0), LonLat(140.5, 41.8), LonLat(141.5, 42.5), LonLat(143.0, 42.0),
    LonLat(144.5, 42.9), LonLat(145.5, 43.3), LonLat(145.0, 44.3), LonLat(143.5, 44.3),
    LonLat(141.5, 45.3), LonLat(141.0, 45.5), LonLat(140.3, 43.3),
)

private val KYUSHU = listOf(
    LonLat(130.9, 33.9), LonLat(131.5, 33.6), LonLat(131.8, 32.8), LonLat(131.5, 31.5),
    LonLat(130.6, 31.0), LonLat(130.2, 31.5), LonLat(129.8, 32.6), LonLat(130.3, 33.5),
)

private val SHIKOKU = listOf(
    LonLat(134.0, 34.3), LonLat(134.7, 33.8), LonLat(134.2, 33.4), LonLat(133.0, 33.2),
    LonLat(132.5, 32.9), LonLat(132.3, 33.5), LonLat(133.0, 34.2),
)

private val JAPAN = listOf(HONSHU, HOKKAIDO, KYUSHU, SHIKOKU)

/**
 * The coast of Kanto, clockwise from inland of the north-west: north to the
 * Ibaraki shore, down the Pacific side, round the tip of Boso and back up
 * the east shore of Tokyo Bay, along the waterfront past Koto, then down
 * the west shore and out round Miura and Izu.
 */
private val KANTO = listOf(
    LonLat(138.50, 36.30), LonLat(139.30, 36.90), LonLat(140.40, 36.70),
    LonLat(140.85, 36.05), LonLat(140.75, 35.75), LonLat(140.87, 35.72),
    LonLat(140.55, 35.45), LonLat(140.25, 35.15), LonLat(139.98, 34.92),
    LonLat(139.78, 35.12), LonLat(139.72, 35.30), LonLat(139.82, 35.45),
    LonLat(139.90, 35.57), LonLat(139.88, 35.645), LonLat(139.83, 35.655),
    LonLat(139.78, 35.635), LonLat(139.73, 35.600), LonLat(139.70, 35.52),
    LonLat(139.68, 35.45), LonLat(139.72, 35.35), LonLat(139.68, 35.30),
    LonLat(139.62, 35.14), LonLat(139.33, 35.28), LonLat(139.15, 35.12),
    LonLat(138.95, 34.70), LonLat(138.75, 34.95), LonLat(138.60, 35.10),
    LonLat(138.30, 35.50), LonLat(138.40, 36.00),
)

/** The Sumida, north to south into the bay, west of Koto. */
private val SUMIDA = listOf(
    LonLat(139.800, 35.790), LonLat(139.805, 35.750), LonLat(139.795, 35.715),
    LonLat(139.800, 35.690), LonLat(139.790, 35.665), LonLat(139.780, 35.645),
)

/** The Arakawa, east of Koto. */
private val ARAKAWA = listOf(
    LonLat(139.830, 35.795), LonLat(139.845, 35.755), LonLat(139.850, 35.720),
    LonLat(139.860, 35.690), LonLat(139.865, 35.665), LonLat(139.870, 35.645),
)

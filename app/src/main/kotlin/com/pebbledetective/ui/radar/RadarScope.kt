package com.pebbledetective.ui.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.pebbledetective.domain.RadarTimeline
import com.pebbledetective.ui.theme.ScannerGreen
import com.pebbledetective.ui.theme.SignalAmber
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Everything the scope needs to draw itself. */
data class RadarScene(
    /** Metres to the hidden pebble, or null while there is no fix yet. */
    val distanceMetres: Double?,
    /** Where the pebble lies relative to the way the phone points, degrees clockwise. */
    val relativeBearing: Double,
    /** The outer ring, in metres. */
    val rangeMetres: Double,
    val found: Boolean,
    /** Milliseconds since the radar opened, driving the sweep and the pulse. */
    val elapsedMs: Long,
)

/**
 * The radar scope: rings, a rotating sweep, the target blip and an arrow.
 *
 * Drawn over the camera, so everything is high-contrast green on whatever is
 * behind it. The sweep and the pulse both run at well under 3Hz - large-area
 * flashing is a real seizure risk and this is a children's app.
 */
@Composable
fun RadarScope(
    scene: RadarScene,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val ringStyle = remember { TextStyle(fontSize = 11.sp, color = ScannerGreen.copy(alpha = 0.8f)) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) * 0.40f

        drawGlow(centre, radius)
        drawRings(centre, radius, scene.rangeMetres, measurer, ringStyle)
        drawCrosshair(centre, radius)
        drawSweep(centre, radius, scene.elapsedMs)
        drawCardinals(centre, radius, measurer, ringStyle)

        val distance = scene.distanceMetres
        if (distance != null) {
            val clamped = distance.coerceAtMost(scene.rangeMetres)
            val fraction = (clamped / scene.rangeMetres).toFloat()
            // Screen angle: 0 degrees relative bearing is straight up.
            val angle = Math.toRadians(scene.relativeBearing - 90.0)
            val blip = Offset(
                centre.x + (cos(angle) * radius * fraction).toFloat(),
                centre.y + (sin(angle) * radius * fraction).toFloat(),
            )
            drawArrow(centre, blip, scene.found)
            drawBlip(blip, scene.elapsedMs, scene.found, radius)
            // A target beyond the outer ring still needs pointing at.
            if (distance > scene.rangeMetres) drawOffScaleTick(centre, radius, angle)
        }

        drawPlayer(centre)
    }
}

private fun DrawScope.drawGlow(centre: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(ScannerGreen.copy(alpha = 0.16f), Color.Transparent),
            center = centre,
            radius = radius * 1.15f,
        ),
        radius = radius * 1.15f,
        center = centre,
    )
}

private fun DrawScope.drawRings(
    centre: Offset,
    radius: Float,
    rangeMetres: Double,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    for (step in 1..RINGS) {
        val r = radius * step / RINGS
        drawCircle(
            color = ScannerGreen.copy(alpha = if (step == RINGS) 0.85f else 0.3f),
            radius = r,
            center = centre,
            style = Stroke(width = if (step == RINGS) 2.5f * density else 1.2f * density),
        )
        val metres = (rangeMetres * step / RINGS).toInt()
        val label = measurer.measure("${metres}m", style)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(centre.x + 6f * density, centre.y - r - label.size.height - 2f),
        )
    }
}

private fun DrawScope.drawCrosshair(centre: Offset, radius: Float) {
    val colour = ScannerGreen.copy(alpha = 0.22f)
    drawLine(colour, Offset(centre.x - radius, centre.y), Offset(centre.x + radius, centre.y), density)
    drawLine(colour, Offset(centre.x, centre.y - radius), Offset(centre.x, centre.y + radius), density)
}

/** The classic sweep: a bright leading edge with a fading wedge behind it. */
private fun DrawScope.drawSweep(centre: Offset, radius: Float, elapsedMs: Long) {
    val angle = RadarTimeline.sweepDegrees(elapsedMs)

    rotate(degrees = angle, pivot = centre) {
        for (i in 0 until TAIL_STEPS) {
            val spread = TAIL_DEGREES * (i + 1) / TAIL_STEPS
            drawArc(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ScannerGreen.copy(alpha = 0.30f * (1f - i.toFloat() / TAIL_STEPS)),
                        Color.Transparent,
                    ),
                    center = centre,
                    radius = radius,
                ),
                startAngle = -90f - spread,
                sweepAngle = spread,
                useCenter = true,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = Size(radius * 2, radius * 2),
            )
        }
        drawLine(
            color = ScannerGreen,
            start = centre,
            end = Offset(centre.x, centre.y - radius),
            strokeWidth = 2.5f * density,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawCardinals(
    centre: Offset,
    radius: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    // The scope is drawn phone-relative, so north is wherever the heading
    // says it is - these are fixed screen positions describing the device.
    val marks = listOf("N" to -90.0, "E" to 0.0, "S" to 90.0, "W" to 180.0)
    for ((label, degrees) in marks) {
        val angle = Math.toRadians(degrees)
        val at = Offset(
            centre.x + (cos(angle) * (radius + 18f * density)).toFloat(),
            centre.y + (sin(angle) * (radius + 18f * density)).toFloat(),
        )
        val measured = measurer.measure(label, style)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(at.x - measured.size.width / 2f, at.y - measured.size.height / 2f),
        )
    }
}

/** A tapered arrow from the player to the blip. */
private fun DrawScope.drawArrow(from: Offset, to: Offset, found: Boolean) {
    val colour = if (found) SignalAmber else ScannerGreen
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length < 1f) return

    val ux = dx / length
    val uy = dy / length
    // Stop short so the head does not sit under the blip.
    val tip = Offset(to.x - ux * 14f * density, to.y - uy * 14f * density)

    drawLine(
        color = colour.copy(alpha = 0.95f),
        start = Offset(from.x + ux * 14f * density, from.y + uy * 14f * density),
        end = tip,
        strokeWidth = 3.5f * density,
        cap = StrokeCap.Round,
    )

    val head = 13f * density
    val left = Offset(tip.x - ux * head + -uy * head * 0.55f, tip.y - uy * head + ux * head * 0.55f)
    val right = Offset(tip.x - ux * head - -uy * head * 0.55f, tip.y - uy * head - ux * head * 0.55f)
    drawPath(
        path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(left.x, left.y)
            lineTo(right.x, right.y)
            close()
        },
        color = colour,
    )
}

/** The pebble, pulsing so it is obvious among the rings. */
private fun DrawScope.drawBlip(at: Offset, elapsedMs: Long, found: Boolean, radius: Float) {
    val colour = if (found) SignalAmber else ScannerGreen
    val phase = RadarTimeline.pulsePhase(elapsedMs)

    // An expanding ring that fades as it grows.
    drawCircle(
        color = colour.copy(alpha = (1f - phase) * 0.55f),
        radius = radius * 0.04f + phase * radius * 0.16f,
        center = at,
        style = Stroke(width = 2f * density),
    )
    drawCircle(color = colour.copy(alpha = 0.28f), radius = 13f * density, center = at)
    drawCircle(color = colour, radius = 6.5f * density, center = at)
}

/** A tick on the rim when the pebble is further away than the scope shows. */
private fun DrawScope.drawOffScaleTick(centre: Offset, radius: Float, angleRadians: Double) {
    val inner = radius * 1.02f
    val outer = radius * 1.12f
    drawLine(
        color = SignalAmber,
        start = Offset(
            centre.x + (cos(angleRadians) * inner).toFloat(),
            centre.y + (sin(angleRadians) * inner).toFloat(),
        ),
        end = Offset(
            centre.x + (cos(angleRadians) * outer).toFloat(),
            centre.y + (sin(angleRadians) * outer).toFloat(),
        ),
        strokeWidth = 4f * density,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawPlayer(centre: Offset) {
    drawCircle(ScannerGreen.copy(alpha = 0.25f), radius = 11f * density, center = centre)
    drawCircle(ScannerGreen, radius = 4.5f * density, center = centre)
}

private const val RINGS = 3
private const val TAIL_DEGREES = 70f
private const val TAIL_STEPS = 5

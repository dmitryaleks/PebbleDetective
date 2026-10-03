package com.pebbledetective.ui.research

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.pebbledetective.ui.theme.ScannerGreen
import com.pebbledetective.ui.theme.SignalAmber
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Connecting to satellite" - a dish sweeping for a signal, with expanding
 * rings pulsing out to a small satellite overhead.
 *
 * Like everything else in the sequence this is a pure function of elapsed
 * time, so it survives a language switch mid-animation.
 */
@Composable
fun SatelliteLink(
    elapsedMs: Long,
    acquired: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val centre = Offset(size.width / 2f, size.height * 0.55f)
        val radius = minOf(size.width, size.height) * 0.26f
        val seconds = elapsedMs / 1000f
        val colour = if (acquired) SignalAmber else ScannerGreen

        // Range rings.
        for (ring in 1..3) {
            drawCircle(
                color = colour.copy(alpha = 0.10f * ring),
                radius = radius * ring / 3f,
                center = centre,
                style = Stroke(width = 1.5f * density),
            )
        }

        // Dashed horizon ellipse, for a sense of ground.
        drawArc(
            color = colour.copy(alpha = 0.25f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(centre.x - radius, centre.y - radius * 0.34f),
            size = Size(radius * 2, radius * 0.68f),
            style = Stroke(
                width = 1.5f * density,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 14f)),
            ),
        )

        // Sweeping beam, one revolution per 1.4s - well under a flashing rate
        // that could trouble a photosensitive child.
        val sweepDegrees = (seconds / 1.4f) * 360f
        rotate(degrees = sweepDegrees, pivot = centre) {
            drawLine(
                color = colour.copy(alpha = 0.8f),
                start = centre,
                end = Offset(centre.x, centre.y - radius),
                strokeWidth = 2.5f * density,
                cap = StrokeCap.Round,
            )
        }

        // Pulses travelling out to the satellite.
        val satellite = Offset(size.width * 0.74f, size.height * 0.22f)
        val pulse = (seconds % 1.2f) / 1.2f
        drawCircle(
            color = colour.copy(alpha = (1f - pulse) * 0.5f),
            radius = radius * 0.25f * (0.4f + pulse),
            center = satellite,
            style = Stroke(width = 2f * density),
        )

        drawLine(
            color = colour.copy(alpha = if (acquired) 0.9f else 0.3f),
            start = centre,
            end = satellite,
            strokeWidth = 1.5f * density,
            pathEffect = if (acquired) null else PathEffect.dashPathEffect(floatArrayOf(8f, 12f)),
        )

        drawSatellite(satellite, radius * 0.16f, colour)
        drawDish(centre, radius * 0.28f, colour)
    }
}

private fun DrawScope.drawSatellite(at: Offset, half: Float, colour: androidx.compose.ui.graphics.Color) {
    drawRect(
        color = colour,
        topLeft = Offset(at.x - half * 0.4f, at.y - half * 0.4f),
        size = Size(half * 0.8f, half * 0.8f),
    )
    // Solar panels.
    for (side in intArrayOf(-1, 1)) {
        drawRect(
            color = colour.copy(alpha = 0.6f),
            topLeft = Offset(at.x + side * half * 1.5f - half * 0.4f, at.y - half * 0.25f),
            size = Size(half * 0.8f, half * 0.5f),
        )
        drawLine(
            color = colour,
            start = Offset(at.x + side * half * 0.4f, at.y),
            end = Offset(at.x + side * half * 1.1f, at.y),
            strokeWidth = 1.5f * density,
        )
    }
}

private fun DrawScope.drawDish(at: Offset, size: Float, colour: androidx.compose.ui.graphics.Color) {
    val tilt = -35.0
    val dx = (cos(Math.toRadians(tilt)) * size).toFloat()
    val dy = (sin(Math.toRadians(tilt)) * size).toFloat()
    drawArc(
        color = colour,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(at.x - size, at.y - size),
        size = Size(size * 2, size * 2),
        style = Stroke(width = 3f * density, cap = StrokeCap.Round),
    )
    drawLine(colour, at, Offset(at.x + dx, at.y + dy), 2f * density, StrokeCap.Round)
    drawLine(
        color = colour,
        start = Offset(at.x - size * 0.5f, at.y + size * 0.7f),
        end = Offset(at.x + size * 0.5f, at.y + size * 0.7f),
        strokeWidth = 2.5f * density,
        cap = StrokeCap.Round,
    )
}

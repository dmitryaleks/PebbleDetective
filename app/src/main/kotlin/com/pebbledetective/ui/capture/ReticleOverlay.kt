package com.pebbledetective.ui.capture

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.pebbledetective.ui.theme.ScannerGreen
import com.pebbledetective.ui.theme.SignalAmber

/** What the reticle is currently doing, which drives how it is drawn. */
enum class ReticleState { SEARCHING, ARMED, LOCKED }

/**
 * The targeting reticle drawn over the camera preview.
 *
 * While [state] is [ReticleState.ARMED] the ring fills in proportion to
 * [steadyProgress], so a child can see that holding still is what the app is
 * waiting for. Without that feedback an unmet stability gate just looks broken.
 */
@Composable
fun ReticleOverlay(
    target: Offset?,
    state: ReticleState,
    steadyProgress: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "reticle")

    // A slow sweep, and a gentle breathe. Both are well under 3Hz: rapid
    // full-area flashing is a genuine seizure risk in a children's app.
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3_600, easing = androidx.compose.animation.core.LinearEasing)),
        label = "sweep",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1_400), repeatMode = RepeatMode.Reverse),
        label = "breathe",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centre = target ?: Offset(size.width / 2f, size.height / 2f)
        val base = minOf(size.width, size.height) * 0.22f
        val radius = when (state) {
            ReticleState.SEARCHING -> base * breathe
            ReticleState.ARMED -> base * 0.92f
            ReticleState.LOCKED -> base * 0.80f
        }
        val colour = if (state == ReticleState.LOCKED) SignalAmber else ScannerGreen

        drawCorners(centre, radius, colour, state)

        // Faint full ring, then the steadiness arc drawn over it.
        drawCircle(
            color = colour.copy(alpha = 0.22f),
            radius = radius,
            center = centre,
            style = Stroke(width = dp(2f)),
        )
        if (state == ReticleState.ARMED && steadyProgress > 0f) {
            drawArc(
                color = colour,
                startAngle = -90f,
                sweepAngle = 360f * steadyProgress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = dp(5f), cap = StrokeCap.Round),
            )
        }

        // Scanning sweep only while hunting for a target.
        if (state == ReticleState.SEARCHING) {
            rotate(degrees = sweep, pivot = centre) {
                drawLine(
                    color = colour.copy(alpha = 0.55f),
                    start = centre,
                    end = Offset(centre.x, centre.y - radius),
                    strokeWidth = dp(2f),
                    cap = StrokeCap.Round,
                )
            }
        }

        // Centre cross.
        val tick = radius * 0.16f
        drawLine(colour, Offset(centre.x - tick, centre.y), Offset(centre.x + tick, centre.y), dp(2f))
        drawLine(colour, Offset(centre.x, centre.y - tick), Offset(centre.x, centre.y + tick), dp(2f))

        if (state == ReticleState.LOCKED) {
            drawCircle(color = colour.copy(alpha = 0.18f), radius = radius, center = centre)
        }
    }
}

/** Four bracket corners, pulled in tight when the target is locked. */
private fun DrawScope.drawCorners(centre: Offset, radius: Float, colour: Color, state: ReticleState) {
    val gap = if (state == ReticleState.LOCKED) radius * 1.05f else radius * 1.35f
    val arm = radius * 0.34f
    val w = dp(3f)
    for (sx in intArrayOf(-1, 1)) {
        for (sy in intArrayOf(-1, 1)) {
            val cx = centre.x + sx * gap
            val cy = centre.y + sy * gap
            drawLine(colour, Offset(cx, cy), Offset(cx - sx * arm, cy), w, StrokeCap.Round)
            drawLine(colour, Offset(cx, cy), Offset(cx, cy - sy * arm), w, StrokeCap.Round)
        }
    }
}

/** dp -> px inside a DrawScope, which works in raw pixels. */
private fun DrawScope.dp(value: Float): Float = value * density

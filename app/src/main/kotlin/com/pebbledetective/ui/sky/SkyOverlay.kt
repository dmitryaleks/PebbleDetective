package com.pebbledetective.ui.sky

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pebbledetective.domain.MeteorTimeline
import com.pebbledetective.ui.theme.ScannerGreen
import com.pebbledetective.ui.theme.SignalAmber
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** One body, already placed on the screen. */
data class SkyMarker(
    val at: Offset,
    val disc: ImageBitmap?,
    val label: TextLayoutResult,
    val radiusPx: Float,
    val isSun: Boolean,
    val belowHorizon: Boolean,
    val dimmed: Boolean,
)

/**
 * A body sitting on the live camera image.
 *
 * The photograph is the marker: a ring and a name over the real view is a
 * star chart, but the actual face of Saturn hanging over the street is the
 * thing that makes a child turn round and look.
 */
fun DrawScope.drawSkyMarker(marker: SkyMarker) {
    val alpha = if (marker.dimmed) 0.35f else 1f
    val radius = marker.radiusPx

    // A halo, so a small dark body still reads against a bright sky.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                (if (marker.isSun) SignalAmber else ScannerGreen).copy(alpha = 0.33f * alpha),
                Color.Transparent,
            ),
            center = marker.at,
            radius = radius * 2.4f,
        ),
        radius = radius * 2.4f,
        center = marker.at,
    )

    val disc = marker.disc
    if (disc != null) {
        val side = (radius * 2f).toInt().coerceAtLeast(1)
        drawImage(
            image = disc,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(disc.width, disc.height),
            dstOffset = IntOffset((marker.at.x - radius).toInt(), (marker.at.y - radius).toInt()),
            dstSize = IntSize(side, side),
            alpha = alpha,
        )
    } else {
        drawCircle(ScannerGreen.copy(alpha = alpha), radius, marker.at)
    }

    // A thin ring so the body reads as a target you can tap, not as a
    // smudge on the lens.
    drawCircle(
        color = ScannerGreen.copy(alpha = 0.75f * alpha),
        radius = radius * 1.28f,
        center = marker.at,
        style = Stroke(width = 1.6f * density),
    )

    if (marker.belowHorizon) {
        // Dashed, because it is under the ground you are standing on.
        val dashes = 18
        for (i in 0 until dashes step 2) {
            val from = (i.toFloat() / dashes) * 360f
            drawArc(
                color = ScannerGreen.copy(alpha = 0.5f * alpha),
                startAngle = from,
                sweepAngle = 360f / dashes,
                useCenter = false,
                topLeft = Offset(marker.at.x - radius * 1.7f, marker.at.y - radius * 1.7f),
                size = Size(radius * 3.4f, radius * 3.4f),
                style = Stroke(width = 2f * density),
            )
        }
    }

    // The name gets a plate of its own. A label floating over a sunlit
    // street is unreadable whatever colour it is.
    val label = marker.label
    val labelAt = Offset(
        marker.at.x - label.size.width / 2f,
        marker.at.y + radius * 1.45f,
    )
    val padX = 7f * density
    val padY = 3f * density
    drawRoundRect(
        color = Color(0xFF05070D).copy(alpha = 0.72f * alpha),
        topLeft = Offset(labelAt.x - padX, labelAt.y - padY),
        size = Size(label.size.width + padX * 2, label.size.height + padY * 2),
        cornerRadius = CornerRadius(6f * density, 6f * density),
    )
    drawText(textLayoutResult = label, topLeft = labelAt, alpha = alpha)
}

/**
 * The arrow that says "turn this way".
 *
 * Sits at the centre of the screen and points along the shortest swing to
 * the body being tracked, which works whether it is just off the edge or
 * directly behind the observer.
 */
fun DrawScope.drawGuideArrow(direction: Offset, offAxisDegrees: Double, label: TextLayoutResult?) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val reach = size.minDimension * 0.30f
    val tip = Offset(centre.x + direction.x * reach, centre.y + direction.y * reach)

    // Longer and more urgent the further round they have to turn.
    val urgency = (offAxisDegrees / 180.0).toFloat().coerceIn(0f, 1f)
    val colour = SignalAmber.copy(alpha = 0.55f + 0.45f * urgency)

    drawLine(
        color = colour.copy(alpha = colour.alpha * 0.55f),
        start = Offset(centre.x + direction.x * reach * 0.35f, centre.y + direction.y * reach * 0.35f),
        end = tip,
        strokeWidth = 5f * density,
        cap = StrokeCap.Round,
    )

    val head = size.minDimension * 0.055f
    val angle = Math.toDegrees(kotlin.math.atan2(direction.y.toDouble(), direction.x.toDouble()))
    rotate(degrees = angle.toFloat() + 90f, pivot = tip) {
        drawPath(
            path = Path().apply {
                moveTo(tip.x, tip.y + head * 0.9f)
                lineTo(tip.x - head * 0.72f, tip.y - head * 0.5f)
                lineTo(tip.x + head * 0.72f, tip.y - head * 0.5f)
                close()
            },
            color = colour,
        )
    }

    if (label != null) {
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                tip.x - label.size.width / 2f,
                tip.y + head * 1.2f,
            ),
        )
    }
}

/** Where the meteor is on screen, and where it is going. */
data class MeteorScene(
    val head: Offset?,
    val tail: List<Offset>,
    val impact: Offset?,
    val elapsedMs: Long,
    val seed: Int,
)

/**
 * The meteor itself.
 *
 * Built out of a hot core, a sheath of plasma, a tapering wake and shed
 * fragments, because that is what actually sells a fireball: no single
 * element does, and a plain bright line reads as a laser. Everything is
 * additive-ish bright over the camera image, which is why it looks like
 * light rather than like paint.
 */
fun DrawScope.drawMeteor(scene: MeteorScene) {
    val elapsed = scene.elapsedMs
    val heat = MeteorTimeline.heat(elapsed)
    val head = scene.head

    // The wake, drawn from the oldest segment forward so the bright end
    // lands on top.
    if (scene.tail.size >= 2) {
        for (i in 0 until scene.tail.size - 1) {
            val t = (i + 1).toFloat() / scene.tail.size
            val width = (1.5f + 9f * t * t) * density * (0.5f + heat)
            drawLine(
                color = trailColour(t).copy(alpha = 0.10f + 0.65f * t * t),
                start = scene.tail[i],
                end = scene.tail[i + 1],
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
        // A soft outer smoke trail, wider and fainter.
        for (i in 0 until scene.tail.size - 1) {
            val t = (i + 1).toFloat() / scene.tail.size
            drawLine(
                color = Color(0xFFB08060).copy(alpha = 0.05f + 0.13f * t),
                start = scene.tail[i],
                end = scene.tail[i + 1],
                strokeWidth = (6f + 26f * t) * density,
                cap = StrokeCap.Round,
            )
        }
    }

    if (head != null && heat > 0f) {
        // Sized against the screen rather than in raw pixels, so it is
        // the same fireball on a small phone as on a big one.
        val core = size.minDimension * (0.016f + 0.030f * heat)

        // Outer glow, then plasma sheath, then a white-hot core: three
        // layers is the minimum that reads as something burning rather than
        // as a coloured dot.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFB055).copy(alpha = 0.55f * heat),
                    Color(0xFFFF6A1A).copy(alpha = 0.22f * heat),
                    Color.Transparent,
                ),
                center = head,
                radius = core * 7f,
            ),
            radius = core * 7f,
            center = head,
        )
        drawCircle(Color(0xFFFFC469).copy(alpha = 0.85f * heat), core * 2.1f, head)
        drawCircle(Color(0xFFFFF0C8).copy(alpha = heat), core, head)
        drawCircle(Color.White.copy(alpha = heat * 0.95f), core * 0.45f, head)

        // Fragments shedding off the back, seeded so they do not jitter
        // about between frames.
        val random = Random(scene.seed)
        val shed = (heat * 9).toInt()
        for (i in 0 until shed) {
            val age = ((elapsed / 90L + i) % 9).toFloat() / 9f
            val spread = (random.nextFloat() - 0.5f) * 2.4f
            val back = scene.tail.lastOrNull()?.let { head - it } ?: Offset(0f, -1f)
            val length = hypot(back.x, back.y).coerceAtLeast(1f)
            val dirX = -back.x / length
            val dirY = -back.y / length
            val at = Offset(
                head.x + dirX * core * 7f * age - dirY * spread * core * 2.4f * age,
                head.y + dirY * core * 7f * age + dirX * spread * core * 2.4f * age,
            )
            drawCircle(
                color = Color(0xFFFFA23C).copy(alpha = (1f - age) * 0.8f * heat),
                radius = core * (0.38f - 0.22f * age).coerceAtLeast(0.06f),
                center = at,
            )
        }
    }

    val impact = scene.impact
    val flash = MeteorTimeline.flash(elapsed)
    if (flash > 0f && impact != null) {
        // One flash, capped well below white-out: a children's app should
        // not strobe, and this already reads as a bang.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF4D6).copy(alpha = 0.55f * flash),
                    Color(0xFFFF8A2A).copy(alpha = 0.22f * flash),
                    Color.Transparent,
                ),
                center = impact,
                radius = size.maxDimension * 0.75f,
            ),
            size = size,
        )
        drawCircle(Color.White.copy(alpha = flash), size.minDimension * 0.05f * flash, impact)
    }

    val wave = MeteorTimeline.shockwave(elapsed)
    if (wave > 0f && impact != null) {
        // Dust rings, staggered, flattened into ellipses because they are
        // spreading along the ground rather than facing the camera.
        for (ring in 0..2) {
            val phase = (wave * 1.35f - ring * 0.2f).coerceIn(0f, 1f)
            if (phase <= 0f) continue
            val radius = size.minDimension * (0.04f + phase * 0.46f)
            drawArc(
                color = Color(0xFFD9C3A6).copy(alpha = (1f - phase) * 0.45f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(impact.x - radius, impact.y - radius * 0.34f),
                size = Size(radius * 2f, radius * 0.68f),
                style = Stroke(width = (3f - 2f * phase) * density),
            )
        }

        val embers = MeteorTimeline.emberGlow(elapsed)
        if (embers > 0f) {
            val glow = size.minDimension * 0.10f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF7A1E).copy(alpha = 0.6f * embers),
                        Color.Transparent,
                    ),
                    center = impact,
                    radius = glow,
                ),
                radius = glow,
                center = impact,
            )
            // A few sparks sitting in the crater, cooling.
            val random = Random(scene.seed + 31)
            repeat(10) {
                val angle = random.nextFloat() * 2f * Math.PI.toFloat()
                val distance = random.nextFloat() * glow * 0.7f
                drawCircle(
                    color = Color(0xFFFFC65C).copy(alpha = embers * (0.3f + random.nextFloat() * 0.6f)),
                    radius = 2.2f * density,
                    center = Offset(
                        impact.x + cos(angle) * distance,
                        impact.y + sin(angle) * distance * 0.4f,
                    ),
                )
            }
        }
    }
}

/** Hot at the head, cooling to smoke at the far end of the wake. */
private fun trailColour(t: Float): Color = when {
    t > 0.82f -> Color(0xFFFFF3D2)
    t > 0.55f -> Color(0xFFFFB043)
    t > 0.3f -> Color(0xFFE2651C)
    else -> Color(0xFF8A4A24)
}

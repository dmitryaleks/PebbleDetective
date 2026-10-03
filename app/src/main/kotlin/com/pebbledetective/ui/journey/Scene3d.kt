package com.pebbledetective.ui.journey

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import com.pebbledetective.domain.Planet
import com.pebbledetective.domain.Projection
import kotlin.math.abs
import kotlin.random.Random

/**
 * A warping starfield held in flat arrays.
 *
 * Deliberately allocation-free once built: the draw phase runs sixty times a
 * second, and building a `List<Offset>` per frame to hand to `drawPoints`
 * would churn hundreds of objects and stutter under GC. Positions are
 * mutated in place and projected into one reusable float array, which goes
 * straight to the native canvas as a single call.
 */
class Starfield(val count: Int = 420, seed: Int = 1234) {
    private val xs = FloatArray(count)
    private val ys = FloatArray(count)
    private val zs = FloatArray(count)

    /** x,y pairs, reused every frame. */
    private val projected = FloatArray(count * 2)
    private val paint = Paint().apply { isAntiAlias = true }

    init {
        val random = Random(seed)
        for (i in 0 until count) {
            xs[i] = (random.nextFloat() - 0.5f) * SPREAD
            ys[i] = (random.nextFloat() - 0.5f) * SPREAD
            zs[i] = random.nextFloat() * DEPTH + Projection.NEAR_PLANE
        }
    }

    /** Advances the field and draws it. [speed] is world units per second. */
    fun drawAt(scope: DrawScope, elapsedSeconds: Float, speed: Float, colour: Color) {
        val centreX = scope.size.width / 2f
        val centreY = scope.size.height / 2f

        var written = 0
        for (i in 0 until count) {
            // Position is derived from elapsed time rather than accumulated
            // per frame, so a dropped frame cannot make the field drift.
            var z = zs[i] - (elapsedSeconds * speed) % DEPTH
            if (z <= Projection.NEAR_PLANE) z += DEPTH
            if (!Projection.isVisible(z)) continue

            projected[written++] = Projection.screenX(xs[i], z, centreX)
            projected[written++] = Projection.screenY(ys[i], z, centreY)
        }

        paint.color = colour.toArgb()
        paint.strokeWidth = 2.2f * scope.density
        paint.strokeCap = android.graphics.Paint.Cap.ROUND
        scope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawPoints(projected, 0, written, paint)
        }
    }

    private companion object {
        const val SPREAD = 2_600f
        const val DEPTH = 1_800f
    }
}

/** The stylised palette each body is drawn with in the journey. */
private data class PlanetLook(
    val core: Color,
    val edge: Color,
    val band: Color? = null,
    val ring: Color? = null,
    val corona: Color? = null,
)

private fun lookFor(planet: Planet): PlanetLook = when (planet) {
    Planet.SUN -> PlanetLook(
        core = Color(0xFFFFF3C4), edge = Color(0xFFFF8A1E), corona = Color(0xFFFFB74D),
    )
    Planet.MERCURY -> PlanetLook(core = Color(0xFFBFBFBF), edge = Color(0xFF6B6B6B))
    Planet.VENUS -> PlanetLook(core = Color(0xFFF6E2A8), edge = Color(0xFFB98B3C))
    Planet.EARTH -> PlanetLook(core = Color(0xFF6FC3F7), edge = Color(0xFF1C4E8A), band = Color(0xFF4CAF50))
    Planet.MARS -> PlanetLook(core = Color(0xFFE2703A), edge = Color(0xFF8C3415))
    Planet.JUPITER -> PlanetLook(core = Color(0xFFE8CBA4), edge = Color(0xFF9A6B3F), band = Color(0xFFC1733F))
    Planet.SATURN -> PlanetLook(core = Color(0xFFF0DFAE), edge = Color(0xFFAD8B4A), ring = Color(0xFFD9C79A))
    Planet.URANUS -> PlanetLook(core = Color(0xFFBFF0EC), edge = Color(0xFF4C9A96))
    Planet.NEPTUNE -> PlanetLook(core = Color(0xFF6E8CF5), edge = Color(0xFF1B2E8C), band = Color(0xFF3C56C4))
}

/**
 * Draws a body as a shaded disc rather than a photograph.
 *
 * The result screen shows the real NASA frame; the journey is deliberately
 * schematic, so it reads as a diagram of the trip rather than a fake
 * photograph of one.
 */
fun DrawScope.drawSchematicPlanet(
    planet: Planet,
    centre: Offset,
    radius: Float,
    spin: Float,
) {
    if (radius <= 0.5f) return
    val look = lookFor(planet)

    // Corona sits outside the disc, so it is drawn before the clip.
    look.corona?.let { corona ->
        for (step in 3 downTo 1) {
            drawCircle(
                color = corona.copy(alpha = 0.10f * step),
                radius = radius * (1f + 0.22f * step),
                center = centre,
            )
        }
    }

    // Rings also extend past the limb.
    look.ring?.let { ring ->
        rotate(degrees = -18f, pivot = centre) {
            for (scale in listOf(1.9f, 2.25f)) {
                drawArc(
                    color = ring.copy(alpha = 0.75f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(centre.x - radius * scale, centre.y - radius * scale * 0.26f),
                    size = Size(radius * scale * 2f, radius * scale * 0.52f),
                    style = Stroke(width = radius * 0.1f),
                )
            }
        }
    }

    // Everything that belongs *on* the sphere is clipped to it. Without this
    // the cloud bands run past the limb as visible rectangles.
    val disc = androidx.compose.ui.graphics.Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                left = centre.x - radius,
                top = centre.y - radius,
                right = centre.x + radius,
                bottom = centre.y + radius,
            )
        )
    }

    clipPath(disc) {
        // Lit from the upper left, which is enough to read as a sphere.
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(look.core, look.edge),
                center = Offset(centre.x - radius * 0.3f, centre.y - radius * 0.3f),
                radius = radius * 1.6f,
            ),
            radius = radius,
            center = centre,
        )

        look.band?.let { band ->
            for (i in -2..2) {
                drawLine(
                    color = band.copy(alpha = 0.35f),
                    start = Offset(centre.x - radius, centre.y + i * radius * 0.3f),
                    end = Offset(centre.x + radius, centre.y + i * radius * 0.3f),
                    strokeWidth = radius * 0.16f,
                )
            }
        }

        // A soft terminator. A half-disc arc gave a hard straight chord that
        // read as a pie slice rather than a shadowed limb.
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                center = Offset(centre.x - radius * 0.45f, centre.y - radius * 0.45f),
                radius = radius * 1.85f,
            ),
            radius = radius,
            center = centre,
        )
    }
}

/**
 * The pebble, as a tumbling faceted lump.
 *
 * Drawn from a fixed outline rotated in two axes; at the sizes involved a
 * real mesh would be invisible effort.
 */
fun DrawScope.drawPebble(centre: Offset, radius: Float, spin: Float) {
    if (radius <= 0.5f) return
    val facets = 7
    val path = androidx.compose.ui.graphics.Path()
    for (i in 0 until facets) {
        val angle = (i.toFloat() / facets) * 2f * Math.PI.toFloat() + spin
        // Irregular radii make it read as a rock rather than a ball.
        val wobble = 0.72f + 0.28f * abs(kotlin.math.sin(angle * 2.3f + spin * 0.7f))
        val px = centre.x + kotlin.math.cos(angle) * radius * wobble
        val py = centre.y + kotlin.math.sin(angle) * radius * wobble
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()

    drawPath(path, color = Color(0xFF9AA3B2))
    drawPath(path, color = Color(0xFFD7DEE8), style = Stroke(width = radius * 0.14f))
    drawCircle(
        color = Color(0xFF6C7686),
        radius = radius * 0.3f,
        center = Offset(centre.x + radius * 0.22f, centre.y + radius * 0.18f),
    )
}

/** A trailing streak behind the pebble, to sell the speed. */
fun DrawScope.drawTrail(from: Offset, to: Offset, width: Float, colour: Color) {
    drawLine(
        color = colour,
        start = from,
        end = to,
        strokeWidth = width,
        cap = StrokeCap.Round,
    )
}

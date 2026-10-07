package com.pebbledetective.ui.journey

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    // Only ever a stand-in: the comet is drawn by its own code, which
    // knows about tails. This is what is left if that never runs.
    Planet.HALLEY -> PlanetLook(core = Color(0xFFFFFFFF), edge = Color(0xFF6FB8FF))
    Planet.SUN -> PlanetLook(
        core = Color(0xFFFFF3C4), edge = Color(0xFFFF8A1E), corona = Color(0xFFFFB74D),
    )
    Planet.MERCURY -> PlanetLook(core = Color(0xFFBFBFBF), edge = Color(0xFF6B6B6B))
    Planet.VENUS -> PlanetLook(core = Color(0xFFF6E2A8), edge = Color(0xFFB98B3C))
    Planet.EARTH -> PlanetLook(core = Color(0xFF6FC3F7), edge = Color(0xFF1C4E8A), band = Color(0xFF4CAF50))
    Planet.MOON -> PlanetLook(core = Color(0xFFE8E6E1), edge = Color(0xFF7D7A75))
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
 * A body drawn from a photograph rather than from a palette.
 *
 * The journey is the longest look at a world the app ever gives - Earth
 * reaches nine hundred pixels across on the approach, and the source planet
 * six hundred as it leaves - and a shaded disc does not hold up at that
 * size. The photograph supplies the continents, the bands and the limb
 * darkening; what is drawn around it is the part a flat frame cannot have.
 */
fun DrawScope.drawPhotoPlanet(
    image: ImageBitmap?,
    centre: Offset,
    radius: Float,
    planet: Planet,
    alpha: Float = 1f,
) {
    if (radius <= 0.5f || alpha <= 0.01f) return
    // Until the decode lands, the schematic one stands in, so arriving at
    // the screen early never shows a hole in space.
    if (image == null) {
        drawSchematicPlanet(planet, centre, radius, spin = 0f)
        return
    }
    if (alpha < 1f && radius < 2f) return

    when (planet) {
        // No rim: a comet has no limb to catch the light on, and the
        // picture it is drawn from already carries its own glow.
        Planet.HALLEY -> Unit
        // Air, lit from behind at the limb. Two layers, because a single
        // gradient reads as a blurred edge rather than as an atmosphere.
        Planet.EARTH -> {
            // Stops placed by hand so the bright band sits just outside
            // the limb. Evenly spaced colours put the peak underneath the
            // planet, where it is invisible, and leave a wash around it.
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    0.00f to Color.Transparent,
                    0.80f to Color.Transparent,
                    0.88f to Color(0xFF8CCBFF).copy(alpha = 0.58f),
                    1.00f to Color.Transparent,
                    center = centre,
                    radius = radius * 1.16f,
                ),
                radius = radius * 1.16f,
                center = centre,
            )
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF2E6FCC).copy(alpha = 0.22f * alpha),
                        Color.Transparent,
                    ),
                    center = centre,
                    radius = radius * 1.42f,
                ),
                radius = radius * 1.42f,
                center = centre,
            )
        }
        // The Sun is the one body that makes its own light, so the glow
        // goes a long way out and it never gets a shadow.
        Planet.SUN -> {
            for (step in 3 downTo 1) {
                drawCircle(
                    color = Color(0xFFFFB74D).copy(alpha = 0.13f * step * alpha),
                    radius = radius * (1f + 0.26f * step),
                    center = centre,
                )
            }
        }
        // Everything else gets a faint rim in its own colour. Not physics:
        // a small dark body against a starfield needs an edge or it reads
        // as a hole punched in the sky.
        else -> {
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    0.00f to Color.Transparent,
                    0.78f to Color.Transparent,
                    0.86f to lookFor(planet).core.copy(alpha = 0.34f * alpha),
                    1.00f to Color.Transparent,
                    center = centre,
                    radius = radius * 1.22f,
                ),
                radius = radius * 1.22f,
                center = centre,
            )
        }
    }

    val side = (radius * 2f).toInt().coerceAtLeast(1)
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset((centre.x - radius).toInt(), (centre.y - radius).toInt()),
        dstSize = IntSize(side, side),
        alpha = alpha,
    )

    // Shading toward the lower right, clipped to the globe. The library
    // frames are mostly full-disc mosaics with no terminator of their own,
    // so without this they sit flat on the starfield like stickers.
    if (planet == Planet.SUN) return
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
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xFF02060F).copy(alpha = 0.38f * alpha)),
                center = Offset(centre.x - radius * 0.38f, centre.y - radius * 0.42f),
                radius = radius * 1.75f,
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
fun DrawScope.drawPebble(
    centre: Offset,
    radius: Float,
    spin: Float,
    colour: Color = Color(0xFF9AA3B2),
) {
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

    // The colour measured off the real stone, with the lit edge and the
    // pitting derived from it. Taking it straight would give a flat chip
    // of paint; a rock needs the same hue three ways.
    drawPath(path, color = colour)
    drawPath(path, color = colour.lighten(0.45f), style = Stroke(width = radius * 0.14f))
    drawCircle(
        color = colour.darken(0.3f),
        radius = radius * 0.3f,
        center = Offset(centre.x + radius * 0.22f, centre.y + radius * 0.18f),
    )
}

/** Toward white, for the lit edge. */
private fun Color.lighten(amount: Float) = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

/** Toward black, for the pitting. */
private fun Color.darken(amount: Float) =
    Color(red * (1f - amount), green * (1f - amount), blue * (1f - amount), alpha)

/**
 * The pebble catching fire as Earth's air starts to bite.
 *
 * A sheath around it and a wake streaming off the back, both growing with
 * the last third of the crossing. It is the same construction as the
 * meteor in the sky mode, at a tenth of the size: a hot core, a plasma
 * halo and a tapering tail, because no one of those reads as burning on
 * its own.
 */
fun DrawScope.drawPebbleFire(centre: Offset, radius: Float, fire: Float, travel: Float) {
    if (fire <= 0.01f || radius <= 0.5f) return

    val reach = radius * (4f + 16f * fire)
    // Streaming back the way it came, which on this arc is up and behind.
    val backX = -0.42f
    val backY = -1f

    // Smoke first, wide and dull, then the flame inside it. One pass of
    // translucent discs came out as a string of beads; two passes at
    // different widths is what makes it read as something burning.
    val steps = 14
    for (i in steps downTo 1) {
        val along = i.toFloat() / steps
        val at = Offset(centre.x + backX * reach * along, centre.y + backY * reach * along)
        drawCircle(
            color = Color(0xFF7A4A2A).copy(alpha = fire * 0.16f * (1f - along)),
            radius = radius * (2.2f - 1.1f * along) * (0.8f + fire),
            center = at,
        )
    }
    for (i in steps downTo 1) {
        val along = i.toFloat() / steps
        val at = Offset(centre.x + backX * reach * along, centre.y + backY * reach * along)
        drawCircle(
            color = trailHeat(1f - along).copy(alpha = fire * 0.55f * (1f - along * 0.85f)),
            radius = radius * (1.25f - 0.75f * along) * (0.7f + fire),
            center = at,
        )
    }

    val halo = radius * (3.2f + 3.6f * fire)
    drawCircle(
        brush = androidx.compose.ui.graphics.Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFE9C0).copy(alpha = 0.90f * fire),
                Color(0xFFFF8A2A).copy(alpha = 0.42f * fire),
                Color.Transparent,
            ),
            center = centre,
            radius = halo,
        ),
        radius = halo,
        center = centre,
    )
    // The leading face, which is the part actually ploughing into the air
    // and the only part that should be white hot.
    drawCircle(
        color = Color(0xFFFFF6E0).copy(alpha = 0.85f * fire * travel),
        radius = radius * (0.7f + 0.3f * fire),
        center = Offset(centre.x - backX * radius * 0.45f, centre.y - backY * radius * 0.45f),
    )
}

private fun trailHeat(t: Float): Color = when {
    t > 0.75f -> Color(0xFFFFF3D2)
    t > 0.45f -> Color(0xFFFFB043)
    else -> Color(0xFFE2651C)
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

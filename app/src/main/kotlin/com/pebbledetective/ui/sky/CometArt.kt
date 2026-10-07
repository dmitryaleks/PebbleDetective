package com.pebbledetective.ui.sky

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pebbledetective.domain.Comet
import kotlin.math.cos
import kotlin.math.sin

/**
 * What one comet looks like.
 *
 * Eight sets of these, so that walking the control round the compass
 * walks it through eight different objects rather than moving one light
 * sideways. The colours are the ones comets actually come in - the green
 * of fluorescing carbon, the gold of a dusty one, the blue-white of ion -
 * and the geometry is the honest trade between the two tails: a comet
 * with a broad curved dust fan has a short faint ion tail, and the ones
 * with a needle of ion reaching halfway across the sky barely have a fan.
 */
private data class CometLook(
    val core: Color,
    val coma: Color,
    val dust: Color,
    val ion: Color,
    /** Dust tail reach, in head radii. */
    val span: Float,
    /** How wide the fan opens. */
    val spread: Float,
    /** How far it bends off the straight anti-sunward line. */
    val curve: Float,
    /** Ion tail reach, in head radii. */
    val ionReach: Float,
    /** Nucleus size, as a fraction of the head. */
    val nucleus: Float,
)

private fun lookOf(comet: Comet): CometLook = when (comet) {
    // Ice. The one everyone pictures.
    Comet.FIRST -> CometLook(
        core = Color(0xFFFFFFFF), coma = Color(0xFFBFE4FF),
        dust = Color(0xFFDCEBFF), ion = Color(0xFF6FB8FF),
        span = 9.0f, spread = 2.2f, curve = 0.60f, ionReach = 10.0f, nucleus = 0.30f,
    )
    // Green, which is carbon fluorescing and the thing that surprises
    // people most about a comet seen through a telescope.
    Comet.SECOND -> CometLook(
        core = Color(0xFFEAFFF4), coma = Color(0xFF9CF5C8),
        dust = Color(0xFFD6FFE8), ion = Color(0xFF4FE3A8),
        span = 7.2f, spread = 2.8f, curve = 1.05f, ionReach = 6.5f, nucleus = 0.26f,
    )
    // Gold and thin: an old comet, most of its dust long gone.
    Comet.THIRD -> CometLook(
        core = Color(0xFFFFF6E0), coma = Color(0xFFFFD9A0),
        dust = Color(0xFFFFE9C4), ion = Color(0xFFFFB45C),
        span = 11.0f, spread = 1.7f, curve = 0.35f, ionReach = 12.0f, nucleus = 0.33f,
    )
    // Violet, and bent hard: a fat slow one close to the Sun.
    Comet.FOURTH -> CometLook(
        core = Color(0xFFFBEAFF), coma = Color(0xFFD6A8FF),
        dust = Color(0xFFEBD2FF), ion = Color(0xFFA86BFF),
        span = 7.8f, spread = 3.1f, curve = 1.40f, ionReach = 5.5f, nucleus = 0.24f,
    )
    Comet.FIFTH -> CometLook(
        core = Color(0xFFE8FFFF), coma = Color(0xFF9DEFF0),
        dust = Color(0xFFCFFBFF), ion = Color(0xFF35D8E8),
        span = 9.6f, spread = 2.0f, curve = 0.72f, ionReach = 11.0f, nucleus = 0.31f,
    )
    // The broadest fan of the eight, and barely any ion at all.
    Comet.SIXTH -> CometLook(
        core = Color(0xFFFFEFF2), coma = Color(0xFFFFB4C4),
        dust = Color(0xFFFFD6DE), ion = Color(0xFFFF6E90),
        span = 6.6f, spread = 3.5f, curve = 1.65f, ionReach = 4.4f, nucleus = 0.22f,
    )
    // The needle: almost no dust, and an ion tail across the whole sky.
    Comet.SEVENTH -> CometLook(
        core = Color(0xFFFFFFFF), coma = Color(0xFFD7E7FF),
        dust = Color(0xFFE9F1FF), ion = Color(0xFF8FA8FF),
        span = 12.5f, spread = 1.4f, curve = 0.24f, ionReach = 13.5f, nucleus = 0.35f,
    )
    // Dirty and orange, shedding everything it has.
    Comet.EIGHTH -> CometLook(
        core = Color(0xFFFFF0DF), coma = Color(0xFFFFC28A),
        dust = Color(0xFFFFD8B0), ion = Color(0xFFFF8A3C),
        span = 8.2f, spread = 3.0f, curve = 1.25f, ionReach = 6.0f, nucleus = 0.28f,
    )
}

/**
 * Draws a comet with its head at [at] and its tails pointing away from
 * the Sun.
 *
 * Built the way the pebble's own fire is: overlapping translucent discs
 * rather than a gradient along a path, because Skia has no such brush and
 * because a chain of discs is what a dust tail is - a trail of grains,
 * each catching the light. Three strands of them at slightly different
 * curvatures make a fan instead of a tube, which is the difference
 * between a comet and a torch beam.
 *
 * @param awayFromSun a unit vector on screen. Both tails stream along it,
 *   because that is the one thing everyone half-remembers about comets and
 *   gets wrong: they do not trail behind their own motion, they are blown
 *   off by the Sun and point away from it however they are travelling.
 * @param seconds wall clock, for a slow shimmer in the coma. The sky
 *   overlay already redraws on every reading from the rotation sensor, so
 *   this costs no clock of its own.
 */
fun DrawScope.drawComet(
    comet: Comet,
    at: Offset,
    headRadius: Float,
    awayFromSun: Offset,
    seconds: Float,
) {
    val look = lookOf(comet)
    // A unit vector across the tail, for the fan and the curve.
    val across = Offset(-awayFromSun.y, awayFromSun.x)
    // Slow, shallow, and out of phase between the two tails, so the thing
    // breathes rather than blinks.
    val pulse = 1f + 0.06f * sin(seconds * 0.9f + comet.number)
    val flicker = 1f + 0.18f * sin(seconds * 2.3f + comet.number * 1.7f)

    drawDustTail(at, headRadius, awayFromSun, across, look, pulse)
    drawIonTail(at, headRadius, awayFromSun, across, look, seconds, flicker)
    drawComa(at, headRadius, awayFromSun, look, pulse)
    drawNucleus(at, headRadius, look, flicker)
}

/**
 * The broad curved fan: strands of grains, each one a chain of discs.
 *
 * Painted rather than added. Additive is what light does and it looks
 * glorious against a night sky, but this is an overlay on a live camera
 * and the camera is often pointed at a bright one: added to a sunlit
 * wall, a comet disappears entirely. The tails are therefore ordinary
 * translucent discs, which read on a dark sky and on a light one, and
 * the glow around the head is added on top where it can afford to be.
 *
 * Five strands weighted bright in the middle and faint at the edges, so
 * the fan has soft sides. Three evenly bright ones made a cone with a
 * hard edge, which is a torch beam.
 */
private fun DrawScope.drawDustTail(
    at: Offset,
    headRadius: Float,
    away: Offset,
    across: Offset,
    look: CometLook,
    pulse: Float,
) {
    // A dark pass first, down the middle of the fan only.
    //
    // The same trick the planet labels use: a pale translucent tail laid
    // over a sunlit wall has nothing to be paler than. Darkening the
    // backdrop a little under the tail gives the bright pass something to
    // read against, and over a night sky - which is already black - it
    // does nothing at all.
    for (step in DUST_STEPS downTo 0) {
        val t = step / DUST_STEPS.toFloat()
        val along = t * look.span * headRadius
        val side = look.curve * t * t * headRadius
        drawCircle(
            color = SHADE.copy(alpha = 0.085f * (1f - t)),
            radius = headRadius * (0.34f + look.spread * t * t * 0.52f) * pulse,
            center = Offset(
                at.x + away.x * along + across.x * side,
                at.y + away.y * along + across.y * side,
            ),
        )
    }

    for ((index, strand) in STRANDS.withIndex()) {
        // The outer strands bend further: the finest dust is pushed
        // hardest by the light and falls furthest behind.
        val bend = look.curve * (1f + strand * 0.6f)
        val reach = look.span * (1f - 0.10f * kotlin.math.abs(strand))
        val weight = STRAND_WEIGHTS[index]
        for (step in DUST_STEPS downTo 0) {
            val t = step / DUST_STEPS.toFloat()
            val along = t * reach * headRadius
            // Quadratic, so it leaves the head straight and bends later.
            val side = (bend * t * t + strand * 0.42f * t) * headRadius
            val centre = Offset(
                at.x + away.x * along + across.x * side,
                at.y + away.y * along + across.y * side,
            )
            // Narrow at the head and opening slowly, rather than a cone.
            val radius = headRadius * (0.30f + look.spread * t * t * 0.45f) * pulse
            val alpha = 0.150f * weight * (1f - t) * (1f - t * 0.55f)
            drawCircle(
                color = look.dust.copy(alpha = alpha),
                radius = radius,
                center = centre,
            )
        }
    }
}

/**
 * The narrow straight one: gas, blown dead along the line from the Sun.
 *
 * A few faint wandering streaks rather than one stroke. An ion tail is
 * knotty and striated, and a single clean line reads as a laser pointer -
 * which is exactly what the first attempt looked like.
 */
private fun DrawScope.drawIonTail(
    at: Offset,
    headRadius: Float,
    away: Offset,
    across: Offset,
    look: CometLook,
    seconds: Float,
    flicker: Float,
) {
    val reach = look.ionReach * headRadius
    for (streak in 0 until ION_STREAKS) {
        val offset = (streak - (ION_STREAKS - 1) / 2f) * headRadius * 0.14f
        var previous = Offset(at.x + across.x * offset, at.y + across.y * offset)
        for (step in 1..ION_STEPS) {
            val t = step / ION_STEPS.toFloat()
            // A slow wander, so the streaks are not parallel rails.
            val wander = sin(t * 5.2f + seconds * 0.6f + streak * 2.1f) *
                headRadius * 0.30f * t
            val point = Offset(
                at.x + away.x * reach * t + across.x * (offset + wander),
                at.y + away.y * reach * t + across.y * (offset + wander),
            )
            drawLine(
                color = look.ion.copy(alpha = 0.32f * (1f - t) * flicker),
                start = previous,
                end = point,
                strokeWidth = headRadius * 0.055f * (1f - t * 0.5f),
                cap = StrokeCap.Round,
            )
            previous = point
        }
    }
}

/**
 * The halo around the nucleus.
 *
 * Bulged towards the Sun, which is where the gas is coming off, so the
 * head is an egg rather than a ball and the comet reads as moving even
 * when it is hanging still.
 */
private fun DrawScope.drawComa(
    at: Offset,
    headRadius: Float,
    away: Offset,
    look: CometLook,
    pulse: Float,
) {
    val sunward = Offset(at.x - away.x * headRadius * 0.22f, at.y - away.y * headRadius * 0.22f)

    // Stacked discs rather than a radial gradient. A `Brush` built inside
    // the draw compiles a shader on every frame, and this canvas redraws
    // on every reading from the rotation sensor on top of a live camera
    // preview - the one screen in the app that cannot afford it. Eight
    // circles with a quadratic falloff are a soft glow to the eye and
    // cost nothing to build.
    for (ring in COMA_RINGS downTo 1) {
        val t = ring / COMA_RINGS.toFloat()
        drawCircle(
            color = look.coma.copy(alpha = 0.075f * (1f - t) * (1f - t) + 0.018f),
            radius = headRadius * 2.1f * t * pulse,
            center = sunward,
        )
    }
    for (ring in CORE_RINGS downTo 1) {
        val t = ring / CORE_RINGS.toFloat()
        val colour = if (t < 0.45f) look.core else look.coma
        // This one is added: the inner coma is the part that should look
        // like a light source rather than like paint, and it is small
        // enough that blowing out to white on a bright wall is right.
        drawCircle(
            color = colour.copy(alpha = 0.16f * (1f - t * 0.7f)),
            radius = headRadius * 0.95f * t,
            center = sunward,
            blendMode = BlendMode.Plus,
        )
    }
}

/** The lump of ice itself, and the glint that says how bright it is. */
private fun DrawScope.drawNucleus(
    at: Offset,
    headRadius: Float,
    look: CometLook,
    flicker: Float,
) {
    val radius = headRadius * look.nucleus
    // Four spikes, short and soft. A diffraction glint is what the eye
    // reads as "this is a point of light, not a painted disc".
    for (spoke in 0 until 4) {
        val angle = Math.toRadians(45.0 + spoke * 90.0)
        val reach = headRadius * (if (spoke % 2 == 0) 2.3f else 1.5f) * flicker
        drawLine(
            color = look.core.copy(alpha = 0.16f),
            start = at,
            end = Offset(
                at.x + (cos(angle) * reach).toFloat(),
                at.y + (sin(angle) * reach).toFloat(),
            ),
            strokeWidth = headRadius * 0.06f,
            cap = StrokeCap.Round,
            blendMode = BlendMode.Plus,
        )
    }
    // An outline first. Over a sunlit wall the glow has nothing to work
    // with and the nucleus needs an edge of its own to stay a thing.
    drawCircle(
        color = SHADE.copy(alpha = 0.38f),
        radius = radius * 1.45f,
        center = at,
        style = Stroke(width = headRadius * 0.05f),
    )
    drawCircle(color = look.core.copy(alpha = 0.95f), radius = radius, center = at)
    drawCircle(color = Color.White, radius = radius * 0.45f, center = at)
}

/**
 * Enough discs to read as a continuous fan, few enough to cost nothing.
 *
 * Every one of these is an alpha blend over a camera preview on a canvas
 * that redraws at the rate the rotation sensor reports, so the budget is
 * tighter here than anywhere else in the app. The discs overlap heavily,
 * so halving the count costs far less than it sounds.
 */
/** Near-black, for the pass that gives the bright one something to sit on. */
private val SHADE = Color(0xFF05070D)

/** Bright in the middle, faint at the edges: a fan, not a cone. */
private val STRANDS = listOf(-1f, -0.5f, 0f, 0.5f, 1f)
private val STRAND_WEIGHTS = listOf(0.35f, 0.75f, 1f, 0.75f, 0.35f)

private const val DUST_STEPS = 18
private const val ION_STREAKS = 3
private const val ION_STEPS = 10
private const val COMA_RINGS = 8
private const val CORE_RINGS = 5

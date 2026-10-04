package com.pebbledetective.ui.splash

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pebbledetective.R
import com.pebbledetective.data.PlanetArt
import com.pebbledetective.domain.Planet
import com.pebbledetective.domain.SplashTimeline
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.common.animationsDisabled
import com.pebbledetective.ui.journey.drawPebble
import com.pebbledetective.ui.theme.PebbleGrey
import com.pebbledetective.ui.theme.ScannerGreen
import com.pebbledetective.ui.theme.SignalAmber

/**
 * The title sequence: the solar system, a pebble falling to Earth, and the
 * name of the app.
 *
 * The same picture as the banner on the project page, rebuilt for a tall
 * screen and assembled a piece at a time rather than arriving whole. The
 * bodies are the real NASA photographs the rest of the app uses, keyed off
 * their black backgrounds so they sit on the starfield.
 *
 * It lasts under three seconds and a tap anywhere ends it early. A splash
 * screen is a toll paid on every single launch, and this is a children's
 * app: the third time through, nobody is admiring it.
 */
@Composable
fun SplashScreen(
    session: SessionViewModel,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = animationsDisabled()

    val frame = remember { mutableLongStateOf(0L) }
    val stars = remember { StaticStarfield() }

    // The discs are decoded off the main thread and appear as they arrive.
    // The sky and the title do not wait for them, so a slow first decode
    // delays nothing: at worst a body fades in a frame or two late.
    val discs by produceState(initialValue = emptyMap<Planet, ImageBitmap>(), context) {
        val loaded = LinkedHashMap<Planet, ImageBitmap>()
        for (planet in Planet.entries) {
            val bitmap: Bitmap = PlanetArt.disc(context, planet, DISC_PX) ?: continue
            loaded[planet] = bitmap.asImageBitmap()
            // Published as each one lands rather than all at the end.
            value = LinkedHashMap(loaded)
        }
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            // Nothing to watch, so do not make them wait for it.
            frame.longValue = SplashTimeline.TOTAL_MS
            onFinished()
            return@LaunchedEffect
        }
        val startedAt = System.nanoTime()
        while (true) {
            withFrameNanos { }
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000L
            frame.longValue = elapsed
            if (SplashTimeline.isComplete(elapsed)) break
        }
        onFinished()
    }

    // The scanner lock, for anyone who has turned sound on.
    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) session.playTitleCue()
    }

    val elapsed = frame.longValue
    val titleAlpha = SplashTimeline.title(elapsed)

    val name = stringResource(R.string.app_name)
    val tagline = stringResource(R.string.splash_tagline)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF03040A))
            .pointerInput(Unit) { detectTapGestures { onFinished() } }
            .semantics { contentDescription = "$name. $tagline" },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sky = SplashTimeline.sky(elapsed)
            drawNebula(sky)
            stars.draw(this, sky)
            drawBodies(discs, elapsed)
            drawDescent(elapsed)
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                // Narrow on purpose: the pebble's trail comes down the left
                // of the screen, and a full-width subtitle ran right across
                // it.
                .fillMaxWidth(0.82f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = tagline,
                style = MaterialTheme.typography.labelMedium,
                color = SignalAmber.copy(alpha = titleAlpha),
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.splash_title_top),
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color(0xFFEEF4FC).copy(alpha = titleAlpha),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.splash_title_bottom),
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = ScannerGreen.copy(alpha = titleAlpha),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.splash_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = PebbleGrey.copy(alpha = titleAlpha * 0.95f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Text(
            text = stringResource(R.string.splash_hint),
            style = MaterialTheme.typography.labelSmall,
            color = PebbleGrey.copy(alpha = titleAlpha * 0.55f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 24.dp, bottom = 14.dp),
        )
    }
}

/** A soft wash of colour, so the background is not flat black. */
private fun DrawScope.drawNebula(alpha: Float) {
    if (alpha <= 0.01f) return
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF131C3A).copy(alpha = 0.85f * alpha), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.20f),
            radius = size.width * 1.1f,
        ),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF2A1636).copy(alpha = 0.8f * alpha), Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.80f),
            radius = size.width * 1.0f,
        ),
    )
}

/** The bodies, along a chain receding to the right. */
private fun DrawScope.drawBodies(discs: Map<Planet, ImageBitmap>, elapsedMs: Long) {
    ARC.forEachIndexed { index, body ->
        val progress = SplashTimeline.body(index, elapsedMs)
        if (progress <= 0.01f) return@forEachIndexed
        val disc = discs[body.planet] ?: return@forEachIndexed

        // They swell very slightly into place rather than simply fading, so
        // the chain reads as arriving rather than as a slide transition.
        val diameter = size.width * body.diameter * (0.88f + 0.12f * progress)
        val centre = Offset(size.width * body.x, size.height * body.y)

        if (body.planet == Planet.SUN) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF9A2A).copy(alpha = 0.32f * progress),
                        Color.Transparent,
                    ),
                    center = centre,
                    radius = diameter * 0.95f,
                ),
                radius = diameter * 0.95f,
                center = centre,
            )
        }
        drawDisc(disc, centre, diameter, progress)
    }
}

/** Earth, the pebble on its way in, and the brackets closing on it. */
private fun DrawScope.drawDescent(elapsedMs: Long) {
    val trail = SplashTimeline.trail(elapsedMs)
    val pebble = SplashTimeline.pebble(elapsedMs)
    val lock = SplashTimeline.reticle(elapsedMs)
    val target = Offset(size.width * PEBBLE_X, size.height * PEBBLE_Y)

    if (trail > 0.01f) {
        // Drawn as a run of short segments so it can brighten and thicken
        // toward the pebble, which a single stroked path cannot do.
        val from = Offset(size.width * TRAIL_FROM_X, size.height * TRAIL_FROM_Y)
        val control = Offset(size.width * TRAIL_CTRL_X, size.height * TRAIL_CTRL_Y)
        val steps = 28
        var previous = from
        for (i in 1..steps) {
            val t = (i.toFloat() / steps)
            if (t > trail) break
            val point = quadratic(from, control, target, t)
            drawLine(
                color = SignalAmber.copy(alpha = 0.05f + 0.40f * t * t),
                start = previous,
                end = point,
                strokeWidth = (1.0f + 3.4f * t * t) * density,
                cap = StrokeCap.Round,
            )
            previous = point
        }
    }

    if (pebble > 0.01f) {
        val radius = size.width * 0.062f * pebble
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SignalAmber.copy(alpha = 0.30f * pebble), Color.Transparent),
                center = target,
                radius = radius * 2.6f,
            ),
            radius = radius * 2.6f,
            center = target,
        )
        drawPebble(target, radius, spin = 0.6f)
    }

    if (lock > 0.01f) {
        // The brackets fly in from well outside and settle on the pebble,
        // the same gesture the capture reticle makes when it locks.
        val gap = size.width * (0.30f - 0.14f * lock)
        val arm = size.width * 0.045f
        val colour = ScannerGreen.copy(alpha = lock)
        for (sx in intArrayOf(-1, 1)) {
            for (sy in intArrayOf(-1, 1)) {
                val cx = target.x + sx * gap
                val cy = target.y + sy * gap
                drawLine(colour, Offset(cx, cy), Offset(cx - sx * arm, cy), 3.5f * density, StrokeCap.Round)
                drawLine(colour, Offset(cx, cy), Offset(cx, cy - sy * arm), 3.5f * density, StrokeCap.Round)
            }
        }
        drawCircle(
            color = ScannerGreen.copy(alpha = lock * 0.45f),
            radius = gap * 0.78f,
            center = target,
            style = Stroke(width = 1.6f * density),
        )
    }
}

private fun DrawScope.drawDisc(
    disc: ImageBitmap,
    centre: Offset,
    diameter: Float,
    alpha: Float,
) {
    val side = diameter.toInt().coerceAtLeast(1)
    drawImage(
        image = disc,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(disc.width, disc.height),
        dstOffset = IntOffset((centre.x - diameter / 2).toInt(), (centre.y - diameter / 2).toInt()),
        dstSize = IntSize(side, side),
        alpha = alpha,
    )
}

private fun quadratic(from: Offset, control: Offset, to: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * from.x + 2f * u * t * control.x + t * t * to.x,
        u * u * from.y + 2f * u * t * control.y + t * t * to.y,
    )
}

/**
 * A still field of stars.
 *
 * The journey has a warping one; here the sky should be calm, and the
 * movement should come from what arrives on top of it.
 */
private class StaticStarfield(count: Int = 360, seed: Int = 97) {
    private val xs = FloatArray(count)
    private val ys = FloatArray(count)
    private val sizes = FloatArray(count)
    private val alphas = FloatArray(count)

    init {
        val random = kotlin.random.Random(seed)
        for (i in 0 until count) {
            xs[i] = random.nextFloat()
            ys[i] = random.nextFloat()
            // Mostly faint with a few bright: a uniform field reads as noise.
            val bright = random.nextFloat()
            alphas[i] = 0.18f + bright * bright * 0.8f
            sizes[i] = if (bright > 0.85f) 2.1f else 1.2f
        }
    }

    fun draw(scope: DrawScope, alpha: Float) {
        if (alpha <= 0.01f) return
        for (i in xs.indices) {
            scope.drawCircle(
                color = Color(0xFFDCE8FF).copy(alpha = alphas[i] * alpha),
                radius = sizes[i] * scope.density,
                center = Offset(xs[i] * scope.size.width, ys[i] * scope.size.height),
            )
        }
    }
}

/** A body in the chain: centre and diameter as fractions of the screen. */
private data class Body(
    val planet: Planet,
    val x: Float,
    val y: Float,
    val diameter: Float,
)

/**
 * The chain, large and near at the top left, receding to the right.
 *
 * Hand placed rather than laid on a curve: the bodies are different sizes
 * and Saturn carries rings, so an even spacing leaves them either colliding
 * or adrift.
 */
private val ARC = listOf(
    Body(Planet.SUN, 0.10f, 0.085f, 0.30f),
    Body(Planet.MERCURY, 0.34f, 0.150f, 0.085f),
    Body(Planet.VENUS, 0.47f, 0.088f, 0.115f),
    Body(Planet.MARS, 0.63f, 0.158f, 0.140f),
    Body(Planet.JUPITER, 0.84f, 0.085f, 0.180f),
    Body(Planet.SATURN, 0.80f, 0.248f, 0.165f),
    Body(Planet.URANUS, 0.52f, 0.268f, 0.105f),
    Body(Planet.NEPTUNE, 0.28f, 0.305f, 0.085f),
    // Earth is last so it draws over the chain: it is the destination, not
    // part of the line, and it sits close enough to run off the corner.
    Body(Planet.EARTH, 1.00f, 0.95f, 0.95f),
    // And its Moon, just off the limb. It belongs beside Earth rather than
    // in the row of planets, which is both where it really is and what
    // makes the bottom corner read as a pair rather than as one big world.
    Body(Planet.MOON, 0.625f, 0.781f, 0.085f),
)

private const val DISC_PX = 256

private const val PEBBLE_X = 0.36f
private const val PEBBLE_Y = 0.755f
private const val TRAIL_FROM_X = 0.22f
private const val TRAIL_FROM_Y = 0.345f
private const val TRAIL_CTRL_X = 0.03f
private const val TRAIL_CTRL_Y = 0.60f

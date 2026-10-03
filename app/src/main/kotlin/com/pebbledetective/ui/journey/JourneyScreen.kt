package com.pebbledetective.ui.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebbledetective.R
import com.pebbledetective.domain.JourneyPhase
import com.pebbledetective.domain.JourneyTimeline
import com.pebbledetective.domain.Planet
import com.pebbledetective.domain.Projection
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.common.animationsDisabled
import com.pebbledetective.ui.result.nameRes

/**
 * The pebble's flight home: twenty seconds of schematic space travel.
 *
 * It leaves its planet, crosses space, burns through the atmosphere and then
 * keeps going - down through a schematic Japan and in over Tokyo to the
 * street in Koto where it finally comes to rest.
 *
 * Like the research sequence, the whole scene is a pure function of a
 * wall-clock elapsed time held in the session, so a language switch
 * mid-flight resumes on the same frame and the animation cannot be
 * collapsed by a zero animator duration scale.
 *
 * The screen never leaves by itself. The landed scene stays up until the
 * child taps Done, which is what that button is for.
 */
@Composable
fun JourneyScreen(
    session: SessionViewModel,
    onFinished: () -> Unit,
) {
    val result by session.result.collectAsStateWithLifecycle()
    val startedAt by session.journeyStartedAt.collectAsStateWithLifecycle()
    val source = Planet.fromId(result?.planetId) ?: Planet.MARS

    val frame = remember { mutableLongStateOf(0L) }
    val starfield = remember { Starfield() }

    val reducedMotion = animationsDisabled()

    LaunchedEffect(Unit) { session.beginJourney() }

    LaunchedEffect(startedAt, reducedMotion) {
        if (startedAt == null) return@LaunchedEffect
        if (reducedMotion) session.skipJourney()
        while (true) {
            withFrameNanos { }
            val elapsed = session.journeyElapsedMs()
            frame.longValue = elapsed
            if (JourneyTimeline.isComplete(elapsed)) break
        }
        // The frames stop here, but the scene does not: the last one holds
        // on screen under the Done button rather than snapping away.
        session.finishJourney()
    }

    val elapsed = frame.longValue
    val phase = JourneyTimeline.phaseAt(elapsed)
    val landed = phase == JourneyPhase.TOUCHDOWN

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF03040A))) {
        JourneyCanvas(
            source = source,
            elapsedMs = elapsed,
            starfield = starfield,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = when (phase) {
                    JourneyPhase.DEPARTURE, JourneyPhase.LAUNCH ->
                        stringResource(R.string.journey_departing, stringResource(source.nameRes))
                    JourneyPhase.CRUISE -> stringResource(R.string.journey_cruising)
                    JourneyPhase.ENTRY -> stringResource(R.string.journey_entering)
                    JourneyPhase.APPROACH -> stringResource(R.string.journey_approach)
                    JourneyPhase.DESCENT -> stringResource(R.string.journey_descent)
                    JourneyPhase.TOUCHDOWN -> stringResource(R.string.journey_arrived)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            // The readout follows the flight: distance left while crossing
            // space, then altitude once there is ground below, then the
            // place itself.
            Text(
                text = when (phase) {
                    JourneyPhase.DEPARTURE, JourneyPhase.LAUNCH, JourneyPhase.CRUISE ->
                        stringResource(
                            R.string.journey_distance,
                            ((1f - JourneyTimeline.travel(elapsed)) * 100).toInt(),
                        )
                    JourneyPhase.TOUCHDOWN -> stringResource(R.string.journey_site)
                    else -> stringResource(
                        R.string.journey_altitude,
                        JourneyTimeline.altitudeKm(elapsed),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (landed) {
                Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.journey_done))
                }
            } else {
                TextButton(onClick = { session.skipJourney() }) {
                    Text(stringResource(R.string.action_skip))
                }
            }
            val progress = JourneyTimeline.progressAt(elapsed)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .semantics { contentDescription = "${(progress * 100).toInt()}%" },
            )
        }
    }
}

@Composable
private fun JourneyCanvas(
    source: Planet,
    elapsedMs: Long,
    starfield: Starfield,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val centreX = size.width / 2f
        val centreY = size.height / 2f
        val reveal = JourneyTimeline.mapReveal(elapsedMs)

        if (reveal < 1f) {
            drawSpaceLeg(source, elapsedMs, starfield, centreX, centreY)
        }
        if (reveal > 0f) {
            drawDescent(
                zoom = JourneyTimeline.descentZoom(elapsedMs),
                landedFraction = JourneyTimeline.landedFraction(elapsedMs),
                alpha = reveal,
            )
        }

        // Atmospheric entry glow over the top of whichever scene is showing,
        // kept to a slow swell rather than a flash. A heat vignette around
        // the edge of the viewport: a single big translucent disc read as a
        // muddy brown ring behind the planet rather than as air glowing.
        val heat = JourneyTimeline.entryHeat(elapsedMs)
        if (heat > 0.01f) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFFF7A2A).copy(alpha = 0.55f * heat),
                    ),
                    center = Offset(centreX, centreY),
                    radius = size.maxDimension * 0.62f,
                ),
                size = size,
            )
        }
    }
}

/** The part of the flight that happens between the worlds. */
private fun DrawScope.drawSpaceLeg(
    source: Planet,
    elapsedMs: Long,
    starfield: Starfield,
    centreX: Float,
    centreY: Float,
) {
    val seconds = elapsedMs / 1000f
    val travel = JourneyTimeline.travel(elapsedMs)

    // Stars speed up once under way, which reads as acceleration.
    val warp = 120f + 760f * travel
    starfield.drawAt(this, seconds, warp, Color(0xFFCFE6FF).copy(alpha = 0.85f))

    // Painter's algorithm: just three bodies, so sort by depth directly.
    // The source recedes behind the camera while Earth comes forward.
    val sourceZ = 520f + travel * 2_600f
    val earthZ = 3_400f - travel * 2_980f

    // Perspective pulls everything toward the centre as z grows, so the two
    // bodies would converge and overlap with small world offsets. The source
    // drifts aside as it recedes; Earth slides to the middle as it closes,
    // otherwise it leaves the screen entirely at the end.
    val sourceX = -340f - travel * 420f
    val sourceY = -200f - travel * 160f
    val earthX = 320f * (1f - travel)
    val earthY = 180f * (1f - travel)

    val pebbleZ = Projection.bezier(500f, 900f, 430f, travel)
    val pebbleX = Projection.bezier(-80f, 260f, 40f, travel)
    val pebbleY = Projection.bezier(-40f, -190f, 30f, travel)

    data class Body(val z: Float, val draw: () -> Unit)

    val bodies = mutableListOf<Body>()

    if (Projection.isVisible(sourceZ)) {
        bodies += Body(sourceZ) {
            drawSchematicPlanet(
                planet = source,
                centre = Offset(
                    Projection.screenX(sourceX, sourceZ, centreX),
                    Projection.screenY(sourceY, sourceZ, centreY),
                ),
                radius = Projection.screenRadius(190f, sourceZ),
                spin = seconds * 0.4f,
            )
        }
    }
    if (Projection.isVisible(earthZ)) {
        bodies += Body(earthZ) {
            drawSchematicPlanet(
                planet = Planet.EARTH,
                centre = Offset(
                    Projection.screenX(earthX, earthZ, centreX),
                    Projection.screenY(earthY, earthZ, centreY),
                ),
                radius = Projection.screenRadius(210f, earthZ),
                spin = seconds * 0.3f,
            )
        }
    }
    if (Projection.isVisible(pebbleZ) && elapsedMs > 1_200L) {
        bodies += Body(pebbleZ) {
            val at = Offset(
                Projection.screenX(pebbleX, pebbleZ, centreX),
                Projection.screenY(pebbleY, pebbleZ, centreY),
            )
            val r = Projection.screenRadius(16f, pebbleZ)
            if (travel > 0f) {
                val back = (travel - 0.03f).coerceAtLeast(0f)
                val trailFrom = Offset(
                    Projection.screenX(
                        Projection.bezier(-80f, 260f, 40f, back),
                        Projection.bezier(500f, 900f, 430f, back),
                        centreX,
                    ),
                    Projection.screenY(
                        Projection.bezier(-40f, -190f, 30f, back),
                        Projection.bezier(500f, 900f, 430f, back),
                        centreY,
                    ),
                )
                drawTrail(trailFrom, at, r * 0.7f, Color(0x66FFC65C))
            }
            drawPebble(at, r, seconds * 3.1f)
        }
    }

    bodies.sortedByDescending { it.z }.forEach { it.draw() }
}

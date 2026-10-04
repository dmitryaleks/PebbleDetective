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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebbledetective.R
import com.pebbledetective.data.PlanetArt
import com.pebbledetective.domain.Astronomy
import com.pebbledetective.domain.JourneyPhase
import com.pebbledetective.domain.JourneyTimeline
import com.pebbledetective.domain.Planet
import com.pebbledetective.domain.Projection
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.common.animationsDisabled
import com.pebbledetective.ui.result.fromNameRes
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

    // Earth grows to most of the screen on the approach, so it is decoded
    // at full size and off the main thread. The schematic one stands in
    // until it lands.
    val context = LocalContext.current
    val earth by produceState(initialValue = null as ImageBitmap?, context) {
        value = PlanetArt
            .disc(context, PlanetArt.EARTH_JAPAN_SIDE, EARTH_PX, keySpace = false)
            ?.asImageBitmap()
    }
    // The world being left is on screen large at the start and tiny by the
    // end, so it needs less than Earth does.
    val origin by produceState(initialValue = null as ImageBitmap?, context, source) {
        value = PlanetArt.disc(context, source, SOURCE_PX)?.asImageBitmap()
    }
    // And the rest of the system, for the opening shot. Small, because
    // none of them is ever more than a few dozen pixels across, and loaded
    // one at a time so the first frame does not wait for the last.
    val system by produceState(initialValue = emptyMap<Planet, ImageBitmap>(), context) {
        val loaded = LinkedHashMap<Planet, ImageBitmap>()
        for (planet in SolarSystem.BODIES + Planet.SUN) {
            val bitmap = PlanetArt.disc(context, planet, SYSTEM_PX) ?: continue
            loaded[planet] = bitmap.asImageBitmap()
            value = LinkedHashMap(loaded)
        }
    }

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
    // The solar system is drawn as it stood when the pebble was picked up,
    // which for a stone found a minute ago is now, and for one replayed
    // out of the logbook is the evening it was found.
    val foundAt = result?.capturedAtEpochMs ?: System.currentTimeMillis()
    // The pebble in the animation is the colour of the pebble in the hand.
    val pebbleColour = result?.dominantColourArgb?.takeIf { it != 0 }
        ?.let { Color(it) } ?: DEFAULT_PEBBLE
    val landed = phase == JourneyPhase.TOUCHDOWN

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF03040A))) {
        JourneyCanvas(
            source = source,
            origin = origin,
            earth = earth,
            system = system,
            foundAtEpochMs = foundAt,
            pebbleColour = pebbleColour,
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
                    JourneyPhase.SYSTEM -> stringResource(R.string.journey_system)
                    JourneyPhase.CLOSING ->
                        stringResource(R.string.journey_closing, stringResource(source.nameRes))
                    JourneyPhase.DEPARTURE, JourneyPhase.LAUNCH ->
                        stringResource(R.string.journey_departing, stringResource(source.fromNameRes))
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
                    // Real, and different every time: the distance to that
                    // world on the day the stone was found.
                    JourneyPhase.SYSTEM, JourneyPhase.CLOSING -> stringResource(
                        R.string.journey_au_away,
                        "%.1f".format(Astronomy.distanceFromEarthAu(source, foundAt)),
                    )
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
    origin: ImageBitmap?,
    earth: ImageBitmap?,
    system: Map<Planet, ImageBitmap>,
    foundAtEpochMs: Long,
    pebbleColour: Color,
    elapsedMs: Long,
    starfield: Starfield,
    modifier: Modifier = Modifier,
) {
    // Held rather than allocated: it is only used during the one-second
    // dissolve, but a Paint built inside the draw lambda is a new object
    // on every frame of it.
    val fade = remember { Paint() }

    Canvas(modifier = modifier) {
        val centreX = size.width / 2f
        val centreY = size.height / 2f
        val reveal = JourneyTimeline.mapReveal(elapsedMs)

        // The two scenes cross-dissolve rather than stack. Drawn at full
        // strength over each other, a schematic Honshu the size of the
        // Pacific slides across a photograph of the real one, which reads
        // as a bug rather than as a transition.
        val closing = JourneyTimeline.closing(elapsedMs)
        val leg: DrawScope.() -> Unit = {
            if (closing < 1f) {
                // Still on the map of the system, or on the way out of it.
                drawSystemLeg(
                    source, system, origin, earth, starfield,
                    foundAtEpochMs, elapsedMs,
                )
            } else {
                drawSpaceLeg(
                    source, origin, earth, pebbleColour,
                    elapsedMs, starfield, centreX, centreY,
                )
            }
        }
        when {
            reveal <= 0f -> leg()
            reveal < 1f -> drawIntoCanvas { canvas ->
                fade.alpha = 1f - reveal
                canvas.saveLayer(Rect(Offset.Zero, size), fade)
                leg()
                canvas.restore()
            }
        }
        if (reveal > 0f) {
            drawDescent(
                zoom = JourneyTimeline.descentZoom(elapsedMs),
                landedFraction = JourneyTimeline.landedFraction(elapsedMs),
                alpha = reveal,
            )
        }

        // A wash of fire across the whole frame at the moment of the cut,
        // brightest exactly halfway through the dissolve and gone at both
        // ends. One flash rather than a flicker: a strobe is a seizure risk
        // and this already does the job, which is to hide the join between
        // a photograph of Earth and a diagram of Japan.
        val cover = 4f * reveal * (1f - reveal)
        if (cover > 0.01f) {
            drawRect(color = Color(0xFFFF8A2A).copy(alpha = 0.58f * cover))
            drawRect(color = Color(0xFFFFD79A).copy(alpha = 0.22f * cover))
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

/**
 * Where the source planet sits during the crossing.
 *
 * Pulled out of the drawing so the opening shot can aim at it: the camera
 * closes in by interpolating from where the planet is on the map of the
 * solar system to exactly here, which is what makes the two scenes one
 * move rather than a cut.
 */
private fun DrawScope.legSource(travel: Float): Placed {
    val z = 520f + travel * 2_600f
    // Perspective pulls everything toward the centre as z grows, so the
    // two bodies would converge with small world offsets. The source
    // drifts aside as it recedes.
    val x = -340f - travel * 420f
    val y = -200f - travel * 160f
    return Placed(
        at = Offset(
            Projection.screenX(x, z, size.width / 2f),
            Projection.screenY(y, z, size.height / 2f),
        ),
        radius = Projection.screenRadius(190f, z),
        depth = z,
    )
}

/** And Earth, which comes the other way. */
private fun DrawScope.legEarth(travel: Float): Placed {
    val z = 3_400f - travel * 2_980f
    // Earth slides to the middle as it closes, or it leaves the screen
    // entirely at the end.
    val x = 320f * (1f - travel)
    val y = 180f * (1f - travel)
    return Placed(
        at = Offset(
            Projection.screenX(x, z, size.width / 2f),
            Projection.screenY(y, z, size.height / 2f),
        ),
        radius = Projection.screenRadius(210f, z),
        depth = z,
    )
}

/** A body placed on screen: where, how big, how far back. */
private data class Placed(val at: Offset, val radius: Float, val depth: Float)

private fun lerp(from: Placed, to: Placed, t: Float) = Placed(
    at = Offset(
        from.at.x + (to.at.x - from.at.x) * t,
        from.at.y + (to.at.y - from.at.y) * t,
    ),
    radius = from.radius + (to.radius - from.radius) * t,
    depth = from.depth + (to.depth - from.depth) * t,
)

/**
 * The opening shot: the solar system as it stood on the day the pebble was
 * found, then the camera closing on the two worlds the story is about.
 *
 * The planets other than those two fade out as the move finishes, and the
 * two that remain are interpolated straight onto their marks in the
 * crossing that follows, so there is no cut between the scenes at all.
 */
private fun DrawScope.drawSystemLeg(
    source: Planet,
    discs: Map<Planet, ImageBitmap>,
    origin: ImageBitmap?,
    earth: ImageBitmap?,
    starfield: Starfield,
    epochMillis: Long,
    elapsedMs: Long,
): Pair<Placed, Placed> {
    val closing = JourneyTimeline.closing(elapsedMs)
    val drift = JourneyTimeline.systemDrift(elapsedMs)
    val centre = Offset(size.width / 2f, size.height * 0.46f)

    // The view barely moves while it is being read, then swings as the
    // camera leaves. Tilt opens up a little so the plane flattens out.
    val spin = -28f + drift * 14f
    val tilt = 55f - closing * 14f
    val scale = size.minDimension * 0.46f * (1f + drift * 0.06f)
    val fade = 1f - closing

    // The stars come up as the system map goes down, so the crossing does
    // not begin with a field of them snapping into existence.
    if (closing > 0.01f) {
        starfield.drawAt(
            this, elapsedMs / 1000f, 120f,
            Color(0xFFCFE6FF).copy(alpha = 0.85f * closing),
        )
    }
    if (fade > 0.01f) {
        with(SolarSystem) {
            drawSunGlow(centre, size.minDimension * 0.42f, fade)
            drawOrbits(epochMillis, scale, tilt, centre, fade)
        }
    }

    fun placement(planet: Planet): Placed {
        val world = Astronomy.heliocentricEcliptic(planet, epochMillis)
        val projected = SolarSystem.project(world, size, scale, tilt, spin, centre)
        var at = projected.at
        if (planet == Planet.MOON) {
            // Pushed away from the Sun by a couple of Earth-widths, or it
            // sits underneath the Earth and is never seen at all.
            val earth = SolarSystem.project(
                Astronomy.heliocentricEcliptic(Planet.EARTH, epochMillis),
                size, scale, tilt, spin, centre,
            ).at
            val away = Offset(earth.x - centre.x, earth.y - centre.y)
            val length = kotlin.math.hypot(away.x, away.y).coerceAtLeast(1f)
            val step = size.minDimension * SolarSystem.sizeOf(Planet.EARTH) *
                SolarSystem.MOON_NUDGE
            at = Offset(earth.x + away.x / length * step, earth.y + away.y / length * step)
        }
        return Placed(
            at = at,
            radius = size.minDimension * SolarSystem.sizeOf(planet),
            depth = projected.depth,
        )
    }

    val sourceNow = lerp(placement(source), legSource(0f), closing)
    val earthNow = lerp(placement(Planet.EARTH), legEarth(0f), closing)

    // Everything else, furthest first, fading as the camera leaves.
    if (fade > 0.01f) {
        val others = (SolarSystem.BODIES + Planet.SUN)
            .filter { it != source && it != Planet.EARTH }
            .map { it to placement(it) }
            .sortedBy { (_, p) -> -p.depth }
        for ((planet, at) in others) {
            drawPhotoPlanet(discs[planet], at.at, at.radius, planet, alpha = fade)
        }
    }

    // The two that matter, drawn last and at full strength throughout -
    // and from the same frames the crossing will use.
    //
    // They used to come out of the small set loaded for the system map,
    // which for Earth is a different photograph entirely: the library
    // Blue Marble shows the Americas and the crossing flies towards the
    // Japan-side DSCOVR frame. The continents changed under you at the
    // moment the camera arrived. The system-map copies are only a
    // stand-in now, for the frame or two before the large ones decode.
    val sourceFace = origin ?: discs[source]
    val earthFace = earth ?: discs[Planet.EARTH]
    if (sourceNow.depth > earthNow.depth) {
        drawPhotoPlanet(sourceFace, sourceNow.at, sourceNow.radius, source)
        drawPhotoPlanet(earthFace, earthNow.at, earthNow.radius, Planet.EARTH)
    } else {
        drawPhotoPlanet(earthFace, earthNow.at, earthNow.radius, Planet.EARTH)
        drawPhotoPlanet(sourceFace, sourceNow.at, sourceNow.radius, source)
    }
    return sourceNow to earthNow
}

/** The part of the flight that happens between the worlds. */
/** The part of the flight that happens between the worlds. */
private fun DrawScope.drawSpaceLeg(
    source: Planet,
    origin: ImageBitmap?,
    earth: ImageBitmap?,
    pebbleColour: Color,
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
    val sourcePlace = legSource(travel)
    val earthPlace = legEarth(travel)
    val sourceZ = sourcePlace.depth
    val earthZ = earthPlace.depth

    // The rock runs on its own clock: still on the surface while the
    // worlds have already begun to move, then accelerating away.
    val along = JourneyTimeline.pebbleTravel(elapsedMs)
    val pebbleZ = Projection.bezier(PEBBLE_FROM_Z, 900f, 430f, along)
    val pebbleX = Projection.bezier(PEBBLE_FROM_X, 260f, 40f, along)
    val pebbleY = Projection.bezier(PEBBLE_FROM_Y, -190f, 30f, along)

    data class Body(val z: Float, val draw: () -> Unit)

    val bodies = mutableListOf<Body>()

    if (Projection.isVisible(sourceZ)) {
        bodies += Body(sourceZ) {
            drawPhotoPlanet(origin, sourcePlace.at, sourcePlace.radius, source)
        }
    }
    if (Projection.isVisible(earthZ)) {
        bodies += Body(earthZ) {
            drawPhotoPlanet(earth, earthPlace.at, earthPlace.radius, Planet.EARTH)
        }
    }
    if (Projection.isVisible(pebbleZ) && elapsedMs > 1_200L) {
        bodies += Body(pebbleZ) {
            val at = Offset(
                Projection.screenX(pebbleX, pebbleZ, centreX),
                Projection.screenY(pebbleY, pebbleZ, centreY),
            )
            val r = Projection.screenRadius(16f, pebbleZ)
            if (along > 0f) {
                val back = (along - 0.03f).coerceAtLeast(0f)
                val trailFrom = Offset(
                    Projection.screenX(
                        Projection.bezier(PEBBLE_FROM_X, 260f, 40f, back),
                        Projection.bezier(PEBBLE_FROM_Z, 900f, 430f, back),
                        centreX,
                    ),
                    Projection.screenY(
                        Projection.bezier(PEBBLE_FROM_Y, -190f, 30f, back),
                        Projection.bezier(PEBBLE_FROM_Z, 900f, 430f, back),
                        centreY,
                    ),
                )
                drawTrail(trailFrom, at, r * 0.7f, Color(0x66FFC65C))
            }
            // Lying on the surface and turning slowly, then tumbling
            // harder the faster it goes.
            val breakout = JourneyTimeline.breakout(elapsedMs)
            val spin = seconds * (1.2f + 5.5f * along)
            drawPebble(at, r * (0.65f + 0.35f * breakout), spin, pebbleColour)
            drawPebbleFire(at, r, JourneyTimeline.pebbleFire(elapsedMs), along)
        }
    }

    bodies.sortedByDescending { it.z }.forEach { it.draw() }
}

/** Earth fills most of the screen on the approach, so decode it big. */
private const val EARTH_PX = 1024

/** The rest of the system is never more than a few dozen pixels across. */
private const val SYSTEM_PX = 256

/**
 * Where the rock starts: lying on the source planet rather than hanging
 * beside it.
 *
 * The source is a sphere of world radius 190 centred at (-340, -200, 520)
 * when the crossing opens, so this is a point on its face, on the side
 * that leans towards Earth and slightly towards the camera. The arc used
 * to begin a hundred units clear of the surface, which read as a rock
 * that had already left.
 */
private const val PEBBLE_FROM_X = -188f
private const val PEBBLE_FROM_Y = -95f
private const val PEBBLE_FROM_Z = 482f

/** For a pebble whose colour was never measured, or came out black. */
private val DEFAULT_PEBBLE = Color(0xFF9AA3B2)

/** The source peaks at about six hundred pixels across as it departs. */
private const val SOURCE_PX = 768

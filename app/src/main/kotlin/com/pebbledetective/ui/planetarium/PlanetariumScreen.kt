package com.pebbledetective.ui.planetarium

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebbledetective.R
import com.pebbledetective.audio.SoundCue
import com.pebbledetective.data.PlanetArt
import com.pebbledetective.data.locale
import com.pebbledetective.domain.Orrery
import com.pebbledetective.domain.Planet
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.common.TopControls
import com.pebbledetective.ui.journey.Placed
import com.pebbledetective.ui.journey.SolarSystem
import com.pebbledetective.ui.journey.Starfield
import com.pebbledetective.ui.journey.drawPhotoPlanet
import com.pebbledetective.ui.result.nameRes
import com.pebbledetective.ui.theme.ScannerGreen
import com.pebbledetective.ui.theme.SignalAmber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The solar system as a thing to play with rather than to watch.
 *
 * The same orrery the journey opens on - the same orbits, the same
 * compression of the radii, the same photographs - but with the clock
 * handed over. Slide the day back and forth, or let it run at up to twenty
 * days a second and watch Mercury lap the Sun while Saturn barely leans.
 *
 * Three things are worth knowing about how it is put together:
 *
 * The moment is the state. Every body's position is a pure function of it,
 * so there is nothing to keep in step and scrubbing backwards is the same
 * operation as running forwards.
 *
 * The clock, the zoom and the rotation are read *only inside the draw
 * lambda*, so a frame of running time invalidates the drawing and neither
 * composition nor layout. The date and the slider read a separate whole-day
 * mark instead, which changes twenty times a second at the fastest speed
 * rather than sixty.
 *
 * Focusing on a world slides the whole picture rather than cropping it: the
 * chosen body sits at the middle of the screen and everything else stays
 * where its orbit puts it, which at a close zoom means mostly off the edge.
 * Because the shift is recomputed every frame, focusing and then running
 * time pins that one world still and sweeps the rest past it.
 */
@Composable
fun PlanetariumScreen(
    session: SessionViewModel,
    initialFocus: Planet?,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onSky: () -> Unit,
    onRadar: () -> Unit,
    onDetection: () -> Unit,
) {
    val context = LocalContext.current
    val language by session.language.collectAsStateWithLifecycle()
    val soundEnabled by session.soundEnabled.collectAsStateWithLifecycle()

    // Loaded one at a time, so the first frame does not wait for the last.
    val discs by produceState(initialValue = emptyMap<Planet, ImageBitmap>(), context) {
        val loaded = LinkedHashMap<Planet, ImageBitmap>()
        for (planet in SolarSystem.BODIES + Planet.SUN) {
            val bitmap = PlanetArt.disc(context, planet, DISC_PX) ?: continue
            loaded[planet] = bitmap.asImageBitmap()
            value = LinkedHashMap(loaded)
        }
    }

    // Where the slider's middle is. Fixed at the moment the screen opened,
    // so the scale under the thumb does not creep while it is being used.
    val openedAt = rememberSaveable { System.currentTimeMillis() }

    // Read in the draw lambda and nowhere else.
    val epoch = remember { mutableLongStateOf(openedAt) }
    val zoom = remember {
        mutableFloatStateOf(initialFocus?.let { focusZoom(it, openedAt) } ?: 1f)
    }
    val spin = remember { mutableFloatStateOf(-28f) }
    val tilt = remember { mutableFloatStateOf(55f) }

    // Read in callbacks and effects, which do not subscribe to it.
    val offset = remember { mutableDoubleStateOf(0.0) }

    // And the whole-day mark the controls read, which is the same number
    // rounded off so that the slider is not relaid out sixty times a
    // second while time is running.
    var dayMark by remember { mutableIntStateOf(0) }
    var speed by remember { mutableIntStateOf(0) }
    var backwards by rememberSaveable { mutableStateOf(false) }
    var showNames by rememberSaveable { mutableStateOf(true) }
    var focus by remember { mutableStateOf(initialFocus) }

    val starfield = remember { Starfield() }
    val hits = remember { Hits() }

    // Resolved here rather than inside the semantics block, which is not
    // a composable scope.
    val sceneDescription = stringResource(R.string.cd_orrery)

    val measurer = rememberTextMeasurer()
    val names = (SolarSystem.BODIES + Planet.SUN).associateWith { stringResource(it.nameRes) }
    val labels = remember(names, measurer) {
        names.mapValues { (_, name) -> measurer.measure(name, LABEL_STYLE) }
    }

    LaunchedEffect(speed, backwards) {
        if (speed == 0) return@LaunchedEffect
        var previous = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (previous != 0L) {
                // The frame's own length, so a stutter costs a longer step
                // rather than a slower sky. Capped, or coming back from the
                // background jumps a month.
                val seconds = ((now - previous) / 1e9).coerceAtMost(0.25)
                val moved = Orrery.advance(offset.doubleValue, speed, backwards, seconds)
                offset.doubleValue = moved
                epoch.longValue = Orrery.epochAt(openedAt, moved)
                val whole = moved.roundToInt()
                if (whole != dayMark) dayMark = whole
                if (Orrery.atLimit(moved)) {
                    speed = 0
                    break
                }
            }
            previous = now
        }
    }

    fun goTo(days: Double) {
        val clamped = Orrery.clamp(days)
        offset.doubleValue = clamped
        epoch.longValue = Orrery.epochAt(openedAt, clamped)
        dayMark = clamped.roundToInt()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF03040A))) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        val tapped = hits.nearest(at, density)
                        if (tapped != focus) session.sound.play(SoundCue.UI_TAP)
                        focus = tapped
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, pinch, _ ->
                        zoom.floatValue = (zoom.floatValue * pinch).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        // Dragging turns the model rather than moving it:
                        // sideways swings the camera round the Sun, up and
                        // down flattens the plane towards edge-on.
                        spin.floatValue -= pan.x * 0.20f
                        tilt.floatValue = (tilt.floatValue + pan.y * 0.16f).coerceIn(14f, 86f)
                    }
                }
                .semantics { contentDescription = sceneDescription },
        ) {
            val epochMs = epoch.longValue
            val zoomNow = zoom.floatValue
            val spinNow = spin.floatValue
            val tiltNow = tilt.floatValue
            val focused = focus

            val base = Offset(size.width / 2f, size.height * 0.40f)
            val scale = size.minDimension * 0.46f * zoomNow
            // Discs grow more slowly than the orbits do. Held fixed, a
            // zoomed-in system is a field of specks with enormous gaps;
            // grown in step, two planets fill the screen and nothing else
            // is on it.
            val discScale = zoomNow.pow(0.45f)

            // Focusing slides the whole picture, so the chosen body lands
            // in the middle and everything else keeps its real place
            // around it.
            val centre = if (focused == null) {
                base
            } else {
                val where = with(SolarSystem) {
                    placeBody(focused, epochMs, scale, tiltNow, spinNow, base, discScale)
                }
                Offset(base.x + (base.x - where.at.x), base.y + (base.y - where.at.y))
            }

            // Still, not streaming: this is a model on a table, not a
            // flight. Speed zero freezes the field where it was seeded.
            starfield.drawAt(this, 0f, 0f, Color(0xFFCFE6FF).copy(alpha = 0.7f))

            with(SolarSystem) {
                drawSunGlow(centre, size.minDimension * 0.42f * zoomNow.coerceAtMost(2.2f), 1f)
                drawOrbits(epochMs, scale, tiltNow, centre, 0.85f)
            }

            val placed = (SolarSystem.BODIES + Planet.SUN)
                .map { planet ->
                    planet to with(SolarSystem) {
                        placeBody(planet, epochMs, scale, tiltNow, spinNow, centre, discScale)
                    }
                }
                // Painter's algorithm: the far side of the plane first.
                .sortedBy { (_, where) -> -where.depth }

            hits.replaceWith(placed)

            for ((planet, where) in placed) {
                if (planet == focused) drawFocusRing(where)
                drawPhotoPlanet(discs[planet], where.at, where.radius, planet)
            }

            if (showNames) drawLabels(placed, labels, focused)
        }

        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            TopControls(
                language = language,
                soundEnabled = soundEnabled,
                onCycleLanguage = session::cycleLanguage,
                onToggleSound = session::toggleSound,
                onOpenHistory = onOpenHistory,
                onRadar = onRadar,
                onSky = onSky,
                onDetection = onDetection,
            )
        }

        TimeControls(
            modifier = Modifier.align(Alignment.BottomCenter),
            dateText = remember(dayMark, language, openedAt) {
                DateTimeFormatter
                    .ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(language.locale)
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(Orrery.epochAt(openedAt, dayMark.toDouble())))
            },
            dayMark = dayMark,
            speed = speed,
            backwards = backwards,
            showNames = showNames,
            focusName = focus?.let { stringResource(it.nameRes) },
            onScrub = { days -> goTo(days.toDouble()) },
            onToday = {
                session.sound.play(SoundCue.UI_TAP)
                goTo(0.0)
            },
            onSpeed = {
                session.sound.play(SoundCue.UI_TAP)
                speed = it
            },
            onToggleBackwards = {
                session.sound.play(SoundCue.UI_TAP)
                backwards = !backwards
            },
            onToggleNames = {
                session.sound.play(SoundCue.UI_TAP)
                showNames = !showNames
            },
            onClearFocus = {
                session.sound.play(SoundCue.UI_TAP)
                focus = null
            },
            onBack = onBack,
        )
    }
}


/**
 * The names, with the ones that would sit on top of each other dropped.
 *
 * At the wide view the inner four planets are a few pixels apart and five
 * labels land in the same square inch - which was unreadable, and worse
 * than unreadable because it hid the planets underneath. Biggest first,
 * then anything whose plate would overlap one already placed is left out;
 * zooming in gives them room and they come back. The body being followed
 * always keeps its name, whatever it collides with.
 */
private fun DrawScope.drawLabels(
    placed: List<Pair<Planet, Placed>>,
    labels: Map<Planet, TextLayoutResult>,
    focused: Planet?,
) {
    val padX = 6f * density
    val padY = 2f * density
    val taken = ArrayList<Rect>(placed.size)

    val order = placed.sortedByDescending { (planet, where) ->
        if (planet == focused) Float.MAX_VALUE else where.radius
    }
    for ((planet, where) in order) {
        val label = labels[planet] ?: continue
        // A body wholly off the edge gets no name. Clamping a label back
        // onto the screen for one put Uranus's name in the corner with no
        // Uranus under it, which reads as a mistake rather than as a hint.
        val reach = where.radius
        if (where.at.x < -reach || where.at.x > size.width + reach) continue
        if (where.at.y < -reach || where.at.y > size.height + reach) continue

        // Held inside the screen, or a body near the edge has half a name.
        val left = (where.at.x - label.size.width / 2f)
            .coerceIn(padX, (size.width - label.size.width - padX).coerceAtLeast(padX))
        val at = Offset(left, where.at.y + where.radius + 6f * density)
        val plate = Rect(
            at.x - padX,
            at.y - padY,
            at.x + label.size.width + padX,
            at.y + label.size.height + padY,
        )
        if (planet != focused && taken.any { it.overlaps(plate) }) continue
        taken += plate

        drawRoundRect(
            color = Color(0xFF05070D).copy(alpha = 0.66f),
            topLeft = Offset(plate.left, plate.top),
            size = Size(plate.width, plate.height),
            cornerRadius = CornerRadius(5f * density, 5f * density),
        )
        drawText(textLayoutResult = label, topLeft = at)
    }
}

/**
 * Where everything ended up last frame, so a tap can find it.
 *
 * Deliberately not snapshot state: it is written during the draw, and a
 * state write there would invalidate the drawing that wrote it.
 */
private class Hits {
    private val planets = ArrayList<Planet>(10)
    private val places = ArrayList<Placed>(10)

    fun replaceWith(placed: List<Pair<Planet, Placed>>) {
        planets.clear()
        places.clear()
        for ((planet, where) in placed) {
            planets += planet
            places += where
        }
    }

    /**
     * The body under a tap, or null for empty space.
     *
     * Nearest rather than first hit, and with a floor on the target so
     * that Mercury at a wide zoom - four pixels across - can still be
     * tapped by a child's finger.
     */
    fun nearest(at: Offset, density: Float): Planet? {
        var best: Planet? = null
        var bestDistance = Float.MAX_VALUE
        for (i in planets.indices) {
            val where = places[i]
            val reach = maxOf(where.radius * 1.5f, 22f * density)
            val distance = hypot(at.x - where.at.x, at.y - where.at.y)
            if (distance <= reach && distance < bestDistance) {
                best = planets[i]
                bestDistance = distance
            }
        }
        return best
    }
}

/** A ring round the body being followed, so it is clear which one that is. */
private fun DrawScope.drawFocusRing(where: Placed) {
    drawCircle(
        color = ScannerGreen.copy(alpha = 0.75f),
        radius = where.radius * 1.55f,
        center = where.at,
        style = Stroke(width = 1.8f * density),
    )
    drawCircle(
        color = ScannerGreen.copy(alpha = 0.10f),
        radius = where.radius * 1.55f,
        center = where.at,
    )
}

/**
 * How far in to start when the planetarium is opened on one world.
 *
 * Enough that the chosen body's orbit is about a third of the way across
 * the screen: Mercury needs three times the zoom Earth does, and Neptune,
 * which is what the whole picture is scaled to, needs less than one.
 */
private fun focusZoom(planet: Planet, epochMillis: Long): Float {
    val fraction = SolarSystem.orbitFraction(planet, epochMillis)
    if (fraction < 0.02f) return 1f
    return (0.33f / (0.46f * fraction)).coerceIn(MIN_ZOOM, MAX_ZOOM)
}

/** The date, the scrubber and the speeds, over a scrim at the bottom. */
@Composable
private fun TimeControls(
    modifier: Modifier,
    dateText: String,
    dayMark: Int,
    speed: Int,
    backwards: Boolean,
    showNames: Boolean,
    focusName: String?,
    onScrub: (Float) -> Unit,
    onToday: () -> Unit,
    onSpeed: (Int) -> Unit,
    onToggleBackwards: () -> Unit,
    onToggleNames: () -> Unit,
    onClearFocus: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF070B16).copy(alpha = 0.82f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = showNames,
                onClick = onToggleNames,
                label = { Text(stringResource(R.string.orrery_names)) },
            )
            TextButton(onClick = onToday) { Text(stringResource(R.string.orrery_today)) }
        }

        val scrubber = stringResource(R.string.cd_orrery_scrubber)
        Slider(
            value = dayMark.toFloat(),
            onValueChange = onScrub,
            valueRange = -Orrery.RANGE_DAYS.toFloat()..Orrery.RANGE_DAYS.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = scrubber },
        )

        Text(
            text = stringResource(R.string.orrery_speed_caption),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = backwards,
                onClick = onToggleBackwards,
                label = {
                    Icon(
                        imageVector = Icons.Filled.FastRewind,
                        contentDescription = stringResource(R.string.orrery_backwards),
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            for (option in Orrery.SPEEDS) {
                FilterChip(
                    selected = speed == option,
                    onClick = { onSpeed(option) },
                    label = {
                        if (option == 0) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.orrery_pause),
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text(option.toString())
                        }
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (focusName != null) {
                // Which world is being followed, and the way to stop.
                // Takes the hint's place rather than a row of its own:
                // by the time anything is focused the hint has been read.
                FilterChip(
                    selected = true,
                    onClick = onClearFocus,
                    label = { Text(focusName) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.orrery_show_all),
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                Text(
                    text = stringResource(R.string.orrery_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.cd_back)) }
        }
    }
}

/** Big enough to look like a planet when two of them fill the screen. */
private const val DISC_PX = 384

private const val MIN_ZOOM = 0.45f
private const val MAX_ZOOM = 9f

private val LABEL_STYLE = TextStyle(
    fontSize = 13.sp,
    fontWeight = FontWeight.Medium,
    color = SignalAmber,
)

package com.pebbledetective.ui.sky

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebbledetective.R
import com.pebbledetective.data.PlanetArt
import com.pebbledetective.domain.Astronomy
import com.pebbledetective.domain.MeteorTimeline
import com.pebbledetective.domain.Planet
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.SkyState
import com.pebbledetective.ui.common.TopControls
import com.pebbledetective.ui.result.nameRes
import com.pebbledetective.ui.theme.ScannerGreen
import com.pebbledetective.ui.theme.SignalAmber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.hypot
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Planets around: the solar system drawn over the street.
 *
 * Where everything is comes out of [Astronomy], which solves the orbits on
 * the phone from six numbers per planet - so this works in a field with no
 * signal, and will still work in 2050. Which way the phone is pointing
 * comes from the rotation vector, and the two meet in a pinhole projection
 * at the camera own field of view, so a label lands on the patch of sky it
 * belongs to rather than merely on the right side of the screen.
 *
 * Accuracy is set by the compass, not by the orbits: a phone magnetometer
 * is routinely ten degrees out indoors or near metal. The positions
 * themselves are good to a few arcminutes.
 */
@Composable
fun SkyScreen(
    session: SessionViewModel,
    onRadar: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val language by session.language.collectAsStateWithLifecycle()
    val soundEnabled by session.soundEnabled.collectAsStateWithLifecycle()
    val sky by session.sky.collectAsStateWithLifecycle()

    val attitude = rememberAttitude()
    // The compass points at the magnetic pole and the planets are worked
    // out against the real one, so every bearing is shifted by this before
    // it is drawn.
    val declination = sky.declinationDegrees
    val measurer = rememberTextMeasurer()
    val verticalFov = remember(context) { rearCameraVerticalFov(context) }

    val discs by produceState(initialValue = emptyMap<Planet, ImageBitmap>(), context) {
        val loaded = LinkedHashMap<Planet, ImageBitmap>()
        for (planet in Astronomy.VISIBLE_BODIES) {
            val bitmap = PlanetArt.disc(context, planet, DISC_PX) ?: continue
            loaded[planet] = bitmap.asImageBitmap()
            value = LinkedHashMap(loaded)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { session.onSkyPermissionResult() }

    LaunchedEffect(Unit) {
        if (!session.hasLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        } else {
            session.startSky()
        }
    }

    DisposableEffect(Unit) { onDispose { session.stopSky() } }

    // One clock for the meteor, ticking only while something is falling.
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(sky.meteor?.startedAtElapsedMs) {
        if (sky.meteor == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val elapsed = session.meteorElapsedMs()
            frame.longValue = elapsed
            if (MeteorTimeline.isComplete(elapsed)) break
        }
    }

    val names = Astronomy.VISIBLE_BODIES.associateWith { stringResource(it.nameRes) }
    // Laid out once per language rather than on every frame: text measurement
    // in the draw phase is the classic way to turn a sixty hertz overlay into
    // a thirty hertz one.
    val labels = remember(names, measurer) {
        names.mapValues { (_, name) -> measurer.measure(name, LABEL_STYLE) }
    }
    val followText = stringResource(R.string.sky_follow)
    val followLabel = remember(followText, measurer) { measurer.measure(followText, ARROW_STYLE) }
    // Focused on one body, only that one is drawn, above the horizon or
    // not - pointing the phone at the ground to find Jupiter underneath you
    // is half the appeal.
    val shown = remember(sky.sightings, sky.focus) {
        sky.focus?.let { focus -> sky.sightings.filter { it.planet == focus } }
            ?: sky.sightings.filter { it.isUp }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(shown, verticalFov) {
                detectTapGestures { tap ->
                    val canvas = Size(size.width.toFloat(), size.height.toFloat())
                    val focal = focalPixels(canvas.height, verticalFov)
                    val hit = shown
                        .mapNotNull { sighting ->
                            attitude.value.project(
                                sighting.azimuthDegrees - declination,
                                sighting.altitudeDegrees,
                                canvas,
                                focal,
                            )?.let { sighting to it }
                        }
                        .filter { (_, at) -> hypot(at.x - tap.x, at.y - tap.y) < TAP_SLOP_PX * density }
                        .minByOrNull { (_, at) -> hypot(at.x - tap.x, at.y - tap.y) }
                    if (hit != null) session.launchMeteor(hit.first.planet)
                }
            },
    ) {
        SkyCameraBackdrop()

        // Readability, not decoration. Green on a sunlit wall is invisible,
        // and the toolbar is the one part of this screen that has to be
        // legible outdoors in the middle of the day.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.34f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.80f),
                        0.70f to Color.Black.copy(alpha = 0.62f),
                        0.88f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.18f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.75f),
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val pointing = attitude.value
            val focal = focalPixels(size.height, verticalFov)

            for (sighting in shown) {
                val at = pointing.project(
                    sighting.azimuthDegrees - declination,
                    sighting.altitudeDegrees,
                    size,
                    focal,
                ) ?: continue
                if (!at.isOnScreen(size, margin = size.minDimension * 0.25f)) continue

                drawSkyMarker(
                    SkyMarker(
                        at = at,
                        disc = discs[sighting.planet],
                        label = labels.getValue(sighting.planet),
                        radiusPx = size.minDimension *
                            if (sighting.planet == Planet.SUN) 0.085f else 0.062f,
                        isSun = sighting.planet == Planet.SUN,
                        belowHorizon = !sighting.isUp,
                        dimmed = !sighting.isUp,
                    )
                )
            }

            // The arrow, for whatever is being tracked but is not in view.
            val focus = sky.focus?.let(sky::of)
            if (focus != null) {
                val offAxis = pointing.offAxisDegrees(
                    focus.azimuthDegrees - declination, focus.altitudeDegrees,
                )
                if (offAxis > ON_TARGET_DEGREES) {
                    drawGuideArrow(
                        direction = pointing.screenDirection(
                            focus.azimuthDegrees - declination, focus.altitudeDegrees,
                        ),
                        offAxisDegrees = offAxis,
                        label = measurer.measure("${offAxis.toInt()}°", ARROW_STYLE),
                    )
                }
            }

            val shot = sky.meteor
            if (shot != null) {
                val elapsed = frame.longValue
                val travel = MeteorTimeline.travel(elapsed)

                // Where the meteor is in the world at a given point along
                // its path, as a bearing and an angle above the horizon.
                fun bearingAt(fraction: Float): Pair<Double, Double> {
                    val azimuth = shot.fromAzimuthDegrees +
                        Astronomy.separationDegrees(
                            shot.fromAzimuthDegrees, shot.toAzimuthDegrees,
                        ) * fraction
                    val altitude = shot.fromAltitudeDegrees +
                        (shot.toAltitudeDegrees - shot.fromAltitudeDegrees) * fraction
                    return azimuth - declination to altitude
                }

                fun along(fraction: Float): Offset? {
                    val (azimuth, altitude) = bearingAt(fraction)
                    return pointing.project(azimuth, altitude, size, focal)
                }

                val tail = buildList {
                    // The wake is sampled back along the path rather than
                    // recorded frame by frame, so it stays put in the world
                    // while the phone moves instead of smearing with it.
                    for (i in TAIL_SEGMENTS downTo 0) {
                        val back = (travel - i * TAIL_SPAN / TAIL_SEGMENTS).coerceAtLeast(0f)
                        along(back)?.let { add(it) }
                    }
                }
                drawMeteor(
                    MeteorScene(
                        head = along(travel),
                        tail = tail,
                        impact = along(1f),
                        elapsedMs = elapsed,
                        seed = shot.startedAtElapsedMs.toInt(),
                    )
                )

                // It falls through more sky than the camera can see, so it
                // leaves the frame on the way down. Pointing after it is the
                // natural thing to do, and this says so - the alternative
                // was a child staring at the spot where it used to be.
                val chasing = if (elapsed < MeteorTimeline.CUE_IMPACT_MS) travel else 1f
                val onScreen = along(chasing)?.isOnScreen(size, margin = -size.minDimension * 0.08f)
                if (onScreen != true) {
                    val (azimuth, altitude) = bearingAt(chasing)
                    drawGuideArrow(
                        direction = pointing.screenDirection(azimuth, altitude),
                        offAxisDegrees = pointing.offAxisDegrees(azimuth, altitude),
                        label = followLabel,
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            TopControls(
                language = language,
                soundEnabled = soundEnabled,
                onCycleLanguage = session::cycleLanguage,
                onToggleSound = session::toggleSound,
                onOpenHistory = onOpenHistory,
                // Already here, so this button lets every planet back in.
                onSky = session::clearSkyFocus,
                skyActive = true,
                onRadar = onRadar,
            )
            PlanetBars(
                sky = sky,
                discs = discs,
                names = names,
                onFocus = session::toggleSkyFocus,
            )
            SkyStatus(sky = sky, names = names)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    if (sky.meteor != null) R.string.sky_incoming else R.string.sky_tap_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (sky.meteor != null) SignalAmber else ScannerGreen.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            // The next stage of the hunt, not the last one: find a world,
            // then let the radar put a piece of it somewhere nearby, then
            // go and photograph what you turn up.
            Button(onClick = onRadar, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sky_to_radar))
            }
        }
    }
}

/** The two rows of icons: what is up now, and what is under your feet. */
@Composable
private fun PlanetBars(
    sky: SkyState,
    discs: Map<Planet, ImageBitmap>,
    names: Map<Planet, String>,
    onFocus: (Planet) -> Unit,
) {
    val above = sky.above
    val below = sky.below
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (above.isNotEmpty()) {
            PlanetRow(
                caption = stringResource(R.string.sky_above),
                sightings = above,
                discs = discs,
                names = names,
                focus = sky.focus,
                faded = false,
                onFocus = onFocus,
            )
        }
        if (below.isNotEmpty()) {
            PlanetRow(
                caption = stringResource(R.string.sky_below),
                sightings = below,
                discs = discs,
                names = names,
                focus = sky.focus,
                faded = true,
                onFocus = onFocus,
            )
        }
    }
}

@Composable
private fun PlanetRow(
    caption: String,
    sightings: List<Astronomy.Sighting>,
    discs: Map<Planet, ImageBitmap>,
    names: Map<Planet, String>,
    focus: Planet?,
    faded: Boolean,
    onFocus: (Planet) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = ScannerGreen.copy(alpha = if (faded) 0.45f else 0.85f),
            modifier = Modifier.padding(end = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items(sightings, key = { it.planet.id }) { sighting ->
                PlanetChip(
                    sighting = sighting,
                    disc = discs[sighting.planet],
                    name = names[sighting.planet].orEmpty(),
                    selected = focus == sighting.planet,
                    faded = faded,
                    onClick = { onFocus(sighting.planet) },
                )
            }
        }
    }
}

@Composable
private fun PlanetChip(
    sighting: Astronomy.Sighting,
    disc: ImageBitmap?,
    name: String,
    selected: Boolean,
    faded: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(
        if (sighting.isUp) R.string.sky_cd_above else R.string.sky_cd_below,
        name,
        sighting.altitudeDegrees.toInt(),
    )
    Box(
        modifier = Modifier
            // Comfortably past the minimum, because the users are children
            // and there are nine of these across one row.
            .size(CHIP)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val ring = if (selected) {
            Modifier.border(2.dp, ScannerGreen, CircleShape)
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .size(DISC)
                .clip(CircleShape)
                .then(ring)
                .alpha(if (faded) 0.42f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            if (disc != null) {
                Image(
                    bitmap = disc,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(DISC - 4.dp),
                )
            } else {
                Text(name.take(2), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** The line under the toolbar: what you are looking for and where it is. */
@Composable
private fun SkyStatus(sky: SkyState, names: Map<Planet, String>) {
    val compass = stringArrayResource(R.array.compass_points)
    val text = when {
        !sky.locationKnown && sky.sightings.isEmpty() ->
            stringResource(R.string.sky_needs_location)
        sky.sightings.isEmpty() -> stringResource(R.string.sky_locating)
        sky.focus != null -> {
            val sighting = sky.of(sky.focus)
            if (sighting == null) {
                ""
            } else {
                stringResource(
                    if (sighting.isUp) R.string.sky_focus_above else R.string.sky_focus_below,
                    names[sighting.planet].orEmpty(),
                    kotlin.math.abs(sighting.altitudeDegrees).toInt(),
                    compass[Astronomy.compassPoint(sighting.azimuthDegrees)],
                )
            }
        }
        else -> stringResource(R.string.sky_count, sky.above.size)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = ScannerGreen,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (sky.remembered && sky.sightings.isNotEmpty()) {
            Text(
                text = stringResource(R.string.sky_remembered_place),
                style = MaterialTheme.typography.labelSmall,
                color = ScannerGreen.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // The one warning this mode genuinely owes a child: it will happily
        // point them straight at the Sun.
        if (sky.focus == Planet.SUN || (sky.focus == null && sky.of(Planet.SUN)?.isUp == true)) {
            Text(
                text = stringResource(R.string.sky_sun_warning),
                style = MaterialTheme.typography.labelSmall,
                color = SignalAmber,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The live rear camera the planets are drawn over.
 *
 * Pinned to a four by three frame on purpose: the projection assumes the
 * full long axis of the sensor fills the height of the screen, which is
 * what makes the field of view read off the lens the right one.
 */
@Composable
private fun SkyCameraBackdrop() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current

    val granted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    if (!granted) return

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val bound = remember { mutableStateOf<Pair<ProcessCameraProvider, Preview>?>(null) }

    LaunchedEffect(previewView) {
        runCatching {
            val provider = suspendCancellableCoroutine { cont ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    {
                        runCatching { future.get() }
                            .onSuccess { cont.resume(it) }
                            .onFailure { cont.resumeWithException(it) }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            }
            val preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()
                )
                .build()
                .apply { surfaceProvider = previewView.surfaceProvider }
            val lens = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            provider.bindToLifecycle(owner, lens, preview)
            bound.value = provider to preview
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Only what this screen bound. unbindAll here would tear down
            // the camera the next screen has already taken, which is a bug
            // this app has made once already.
            runCatching { bound.value?.let { (provider, preview) -> provider.unbind(preview) } }
            bound.value = null
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private val LABEL_STYLE = TextStyle(
    fontSize = 13.sp,
    fontWeight = FontWeight.Medium,
    color = ScannerGreen,
)

private val ARROW_STYLE = TextStyle(fontSize = 15.sp, color = SignalAmber)

private val CHIP = 46.dp
private val DISC = 38.dp
private const val DISC_PX = 192

/** How close a tap has to land to count as hitting a planet. */
private const val TAP_SLOP_PX = 64f

/** Inside this, the body is in view and the arrow would only be in the way. */
private const val ON_TARGET_DEGREES = 12.0

private const val TAIL_SEGMENTS = 22
private const val TAIL_SPAN = 0.34f

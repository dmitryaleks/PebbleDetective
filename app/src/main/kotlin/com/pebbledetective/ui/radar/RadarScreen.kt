package com.pebbledetective.ui.radar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebbledetective.R
import com.pebbledetective.domain.Geo
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.common.TopControls
import com.pebbledetective.ui.common.TopMode
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Radar mode: hunt for a pebble hidden somewhere nearby.
 *
 * A scope drawn over the live camera. The player's position is the centre and
 * moves as they walk; the display rotates with the phone, so the arrow keeps
 * pointing at the same patch of ground however the phone is turned.
 *
 * The backdrop is the rear camera: you are walking toward something, so the
 * useful view is where you are going rather than your own face.
 */
@Composable
fun RadarScreen(
    session: SessionViewModel,
    onSwitchToDetection: () -> Unit,
    onSky: () -> Unit,
    onPlanetarium: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val language by session.language.collectAsStateWithLifecycle()
    val soundEnabled by session.soundEnabled.collectAsStateWithLifecycle()
    val radar by session.radar.collectAsStateWithLifecycle()

    val heading by rememberHeading()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { session.onRadarPermissionResult() }

    LaunchedEffect(Unit) {
        if (!session.hasPreciseLocation()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        } else {
            session.startRadar()
        }
    }

    // Location updates are a battery drain, so they stop whichever way the
    // screen is left - the button, the back gesture or the logbook.
    DisposableEffect(Unit) {
        onDispose { session.stopRadar() }
    }

    // Elapsed since the hunt began, so the drawn sweep and the ping that
    // goes with it share one clock.
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(radar.startedAtElapsedMs) {
        while (true) {
            withFrameNanos { }
            frame.longValue = android.os.SystemClock.elapsedRealtime() - radar.startedAtElapsedMs
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        RadarCameraBackdrop()

        // A heavy green wash so the scope reads clearly over anything.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF041A0E).copy(alpha = 0.62f),
                            Color(0xFF020A06).copy(alpha = 0.92f),
                        )
                    )
                )
        )

        RadarScope(
            scene = RadarScene(
                distanceMetres = radar.distanceMetres,
                relativeBearing = radar.target?.let {
                    Geo.relativeBearing(radar.bearingDegrees ?: 0.0, heading.toDouble())
                } ?: 0.0,
                rangeMetres = radar.rangeMetres,
                found = radar.found,
                elapsedMs = frame.longValue,
            ),
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            TopControls(
                language = language,
                soundEnabled = soundEnabled,
                onCycleLanguage = session::cycleLanguage,
                onToggleSound = session::toggleSound,
                onOpenHistory = onOpenHistory,
                onRadar = { session.startRadar() },
                onSky = onSky,
                onPlanetarium = onPlanetarium,
                onDetection = onSwitchToDetection,
                current = TopMode.RADAR,
            )
            Text(
                text = radar.statusText(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(onClick = onSwitchToDetection, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.radar_to_detection))
            }
            TextButton(onClick = { session.startRadar() }) {
                Text(stringResource(R.string.radar_new_target))
            }
        }
    }
}

/** The status line above the scope. */
@Composable
private fun com.pebbledetective.ui.RadarState.statusText(): String = when {
    !preciseLocation -> stringResource(R.string.radar_needs_precise)
    target == null -> stringResource(R.string.radar_searching)
    found -> stringResource(R.string.radar_found)
    distanceMetres == null -> stringResource(R.string.radar_searching)
    else -> stringResource(R.string.radar_distance, distanceMetres.toInt())
}

/**
 * The live rear camera behind the scope.
 *
 * Failing to bind is not fatal - the radar is perfectly usable against the
 * black background, so a device with no front camera still plays.
 */
@Composable
private fun RadarCameraBackdrop() {
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
    // Held so disposal can retract exactly what this screen bound.
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
            val preview = Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }
            provider.unbindAll()
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
            // Only this screen's preview. unbindAll() here was a real bug:
            // Compose disposes the outgoing screen *after* composing the
            // incoming one, so it tore down the camera that Detection had
            // just bound, freezing its preview and leaving capture dead.
            runCatching { bound.value?.let { (provider, preview) -> provider.unbind(preview) } }
            bound.value = null
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

package com.pebbledetective.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebbledetective.R
import com.pebbledetective.audio.SoundCue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import com.pebbledetective.domain.Planet
import com.pebbledetective.ui.result.fromNameRes
import com.pebbledetective.ui.theme.SignalAmber
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.common.TopControls

/**
 * Live camera, targeting reticle, and the "target captured" freeze frame.
 *
 * Tapping while the phone is still moving *arms* the shot rather than
 * rejecting it: the shutter fires by itself the moment the view settles, and
 * gives up waiting after a few seconds. The spec asks for stabilised *and*
 * tapped, and this satisfies that without ever dead-ending a child whose
 * hands will not stop moving.
 */
@Composable
fun CaptureScreen(
    session: SessionViewModel,
    onCaptured: (Bitmap, Offset) -> Unit,
    onOpenHistory: () -> Unit,
    onRadar: () -> Unit,
    onSky: () -> Unit,
) {
    val context = LocalContext.current
    val language by session.language.collectAsStateWithLifecycle()
    val soundEnabled by session.soundEnabled.collectAsStateWithLifecycle()
    val claimedOrigin by session.claimedOrigin.collectAsStateWithLifecycle()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        asked = true
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // The scanner idles for as long as the camera is up, and stops however
    // the screen is left.
    DisposableEffect(granted) {
        if (granted) session.startDetectionAmbience()
        onDispose { session.stopDetectionAmbience() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPane(session = session, onCaptured = onCaptured)
        } else {
            CameraDenied(
                permanently = asked,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onPicked = { bitmap -> onCaptured(bitmap, Offset(0.5f, 0.5f)) },
            )
        }

        // Controls float over the preview, which stays full bleed.
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            TopControls(
                language = language,
                soundEnabled = soundEnabled,
                onCycleLanguage = session::cycleLanguage,
                onToggleSound = session::toggleSound,
                onOpenHistory = onOpenHistory,
                onRadar = onRadar,
                onSky = onSky,
            )
            claimedOrigin?.let { planet ->
                ClaimedOriginBanner(planet = planet, onDismiss = session::forgetClaimedOrigin)
            }
        }
    }
}

/**
 * Says that the next stone is already spoken for, and lets that be undone.
 *
 * Shown only after a meteor has been called down in the sky mode, and only
 * until a pebble is researched. It has to be visible before the capture,
 * not after: finding out that the answer was decided in advance only once
 * the answer appears would feel like the app cheating.
 */
@Composable
private fun ClaimedOriginBanner(planet: Planet, onDismiss: () -> Unit) {
    // "a piece of Jupiter" wants the same inflected form as "leaving
    // Jupiter" does: осколок Юпитера, not осколок Юпитер.
    val name = stringResource(planet.fromNameRes)
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B1022).copy(alpha = 0.86f))
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.capture_origin_claimed, name),
            style = MaterialTheme.typography.bodySmall,
            color = SignalAmber,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(
                text = stringResource(R.string.capture_origin_forget),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun CameraPane(session: SessionViewModel, onCaptured: (Bitmap, Offset) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val camera = remember { CameraSession(context, previewView) }

    var target by remember { mutableStateOf<Offset?>(null) }
    var armedAt by remember { mutableStateOf<Long?>(null) }
    var frozen by remember { mutableStateOf<Bitmap?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }

    val steadiness by rememberSteadiness(armedAt)

    LaunchedEffect(previewView) {
        // viewPort is null until the view has been measured, and binding
        // without it loses the crop match between preview and still.
        while (previewView.width == 0 || previewView.height == 0) withFrameNanos { }
        camera.bind(owner)
    }

    // Fire as soon as the view settles after the child has tapped.
    LaunchedEffect(armedAt, steadiness.steady) {
        val armed = armedAt ?: return@LaunchedEffect
        if (capturing || !steadiness.steady) return@LaunchedEffect
        capturing = true
        // Hand over from the idle scanner to the capture cues.
        session.stopDetectionAmbience()
        session.sound.play(SoundCue.RETICLE_LOCK)
        val tapped = target
        runCatching { camera.capture() }
            .onSuccess { bitmap ->
                session.sound.play(SoundCue.SHUTTER)
                flash = true
                frozen = bitmap
                // Let the still land on screen before releasing the camera,
                // otherwise there is a visible black gap between the two.
                withFrameNanos { }
                camera.unbind()
                val size = previewView.width.toFloat() to previewView.height.toFloat()
                val normalised = tapped
                    ?.let { Offset(it.x / size.first, it.y / size.second) }
                    ?: Offset(0.5f, 0.5f)
                onCaptured(bitmap, normalised)
            }
            .onFailure {
                capturing = false
                armedAt = null
            }
        if (armed == armedAt) armedAt = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(capturing) {
                detectTapGestures { offset ->
                    if (capturing) return@detectTapGestures
                    target = offset
                    armedAt = android.os.SystemClock.elapsedRealtime()
                }
            }
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // The still is drawn over the live preview, never instead of it, so
        // there is no gap at the swap.
        frozen?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        ReticleOverlay(
            target = target,
            state = when {
                frozen != null || capturing -> ReticleState.LOCKED
                armedAt != null -> ReticleState.ARMED
                else -> ReticleState.SEARCHING
            },
            steadyProgress = steadiness.progress,
        )

        AnimatedVisibility(visible = flash, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
        }
        LaunchedEffect(flash) {
            if (flash) {
                withFrameNanos { }
                flash = false
            }
        }

        Text(
            text = stringResource(
                if (armedAt != null) R.string.capture_hold_still else R.string.capture_hint
            ),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
        )
    }
}

package com.pebbledetective.ui.research

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebbledetective.R
import com.pebbledetective.domain.ResearchPhase
import com.pebbledetective.domain.ResearchTimeline
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.common.animationsDisabled

/**
 * The Deep Research sequence: a prompt, then five seconds of theatre.
 *
 * The clock is a wall-clock elapsed time owned by the session, and the whole
 * scene is a pure function of it. That buys three things at once: switching
 * language mid-research resumes on the same frame, the sequence is immune to
 * animator duration scale being zero, and the timing is unit testable.
 */
@Composable
fun ResearchScreen(
    session: SessionViewModel,
    onFinished: () -> Unit,
    onDeclined: () -> Unit,
) {
    val captured by session.captured.collectAsStateWithLifecycle()
    val startedAt by session.researchStartedAt.collectAsStateWithLifecycle()

    val frame = remember { mutableLongStateOf(0L) }

    // Coarse location is strictly optional: it is asked for once, when the
    // child opts into research, and the answer never gates anything. A
    // refusal simply logs the pebble without a place.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { session.beginResearch() }

    val onYes: () -> Unit = {
        if (session.needsLocationPermission()) {
            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            session.beginResearch()
        }
    }

    val reducedMotion = animationsDisabled()

    LaunchedEffect(startedAt, reducedMotion) {
        if (startedAt == null) return@LaunchedEffect
        // Someone who turned animations off should not be held through the
        // whole sequence just because it runs on a wall clock.
        if (reducedMotion) session.skipResearch()
        // Finite sequence, so withFrameNanos rather than the infinite
        // variant - the latter cooperates with InfiniteAnimationPolicy and
        // makes Compose UI tests hang forever.
        while (true) {
            withFrameNanos { }
            val elapsed = session.researchElapsedMs()
            frame.longValue = elapsed
            if (ResearchTimeline.isComplete(elapsed)) break
        }
        session.finishResearch()
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        captured?.bitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (startedAt == null) {
            ResearchPrompt(
                onYes = onYes,
                onNo = onDeclined,
            )
        } else {
            ResearchSequence(elapsedMs = frame.longValue, onSkip = { session.skipResearch() })
        }
    }
}

@Composable
private fun ResearchPrompt(onYes: () -> Unit, onNo: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically { it / 3 },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(32.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        MaterialTheme.shapes.large,
                    )
                    .padding(28.dp),
            ) {
                Text(
                    text = stringResource(R.string.research_prompt_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.research_prompt_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onYes, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.research_yes))
                }
                TextButton(onClick = onNo, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.research_no))
                }
            }
        }
    }
}

@Composable
private fun ResearchSequence(elapsedMs: Long, onSkip: () -> Unit) {
    val phase = ResearchTimeline.phaseAt(elapsedMs)
    val progress = ResearchTimeline.progressAt(elapsedMs)

    val lines = listOf(
        stringResource(R.string.research_line_scan),
        stringResource(R.string.research_line_spectrum),
        stringResource(R.string.research_line_mineral),
        stringResource(R.string.research_line_crystal),
        stringResource(R.string.research_line_origin),
    )
    val visible = ResearchTimeline.visibleLines(elapsedMs, lines.size)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f))) {
        when (phase) {
            ResearchPhase.ACQUIRING, ResearchPhase.SIGNAL ->
                SatelliteLink(elapsedMs = elapsedMs, acquired = phase == ResearchPhase.SIGNAL)
            ResearchPhase.ANALYSING, ResearchPhase.MATCH ->
                MatrixRain(elapsedMs = elapsedMs)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    when (phase) {
                        ResearchPhase.ACQUIRING -> R.string.research_connecting
                        ResearchPhase.SIGNAL -> R.string.research_signal
                        ResearchPhase.ANALYSING -> R.string.research_analysing
                        ResearchPhase.MATCH -> R.string.research_match
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                // Announce each beat to a screen reader as it happens.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            lines.take(visible).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.action_skip))
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .semantics {
                        contentDescription = "${(progress * 100).toInt()}%"
                    },
            )
        }
    }
}

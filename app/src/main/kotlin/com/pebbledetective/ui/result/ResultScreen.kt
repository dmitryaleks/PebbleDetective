package com.pebbledetective.ui.result

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebbledetective.R
import com.pebbledetective.data.locale
import com.pebbledetective.domain.Planet
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.common.MapLink
import com.pebbledetective.ui.common.TopControls
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Where the pebble came from: a real NASA photograph, the name, and a fact. */
@Composable
fun ResultScreen(
    session: SessionViewModel,
    onJourney: () -> Unit,
    onPlanetarium: (Planet) -> Unit,
    onNewPebble: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val language by session.language.collectAsStateWithLifecycle()
    val soundEnabled by session.soundEnabled.collectAsStateWithLifecycle()
    val result by session.result.collectAsStateWithLifecycle()

    val planet = Planet.fromId(result?.planetId)

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        TopControls(
            language = language,
            soundEnabled = soundEnabled,
            onCycleLanguage = session::cycleLanguage,
            onToggleSound = session::toggleSound,
            onOpenHistory = onOpenHistory,
        )

        if (planet == null) {
            // The analysis coroutine outlives the five seconds only if the
            // child skipped; show that it is still finishing rather than an
            // empty screen.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.result_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )

            PlanetPortrait(planet, onOpen = { onPlanetarium(planet) })

            Text(
                // The caption above reads "this pebble came from", so this
                // is the end of a sentence rather than a label, and Russian
                // wants the genitive here too: прилетел с Юпитера.
                text = stringResource(planet.fromNameRes),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                text = stringResource(planet.factRes),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            result?.let { entry ->
                val formatter = remember(language, entry.timeZoneId) {
                    // Locale.getDefault() is deliberately untouched by the
                    // in-composition language switch, so it must be passed in
                    // explicitly or dates stay in the device language.
                    DateTimeFormatter
                        .ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(language.locale)
                        .withZone(ZoneId.of(entry.timeZoneId))
                }
                Text(
                    text = formatter.format(Instant.ofEpochMilli(entry.capturedAtEpochMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                val lat = entry.latitude
                val lon = entry.longitude
                if (lat != null && lon != null) {
                    MapLink(latitude = lat, longitude = lon)
                }
            }

            Button(onClick = onJourney, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.result_journey))
            }
            TextButton(onClick = onNewPebble, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.result_new))
            }
        }
    }
}

/**
 * The photograph, drifting gently so the screen does not feel like a poster.
 *
 * Tapping it opens the planetarium on that world. The screen has just
 * named a planet, and "where is it, then?" is the next thing anyone asks.
 */
@Composable
private fun PlanetPortrait(planet: Planet, onOpen: () -> Unit) {
    val image by rememberPlanetImage(planet)
    val transition = rememberInfiniteTransition(label = "planet")
    val breathe by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(4_000), repeatMode = RepeatMode.Reverse),
        label = "breathe",
    )

    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap == null) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(
                    R.string.orrery_focus_hint,
                    stringResource(planet.nameRes),
                ),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(breathe)
                    .clickable(onClick = onOpen),
            )
        }
    }
}

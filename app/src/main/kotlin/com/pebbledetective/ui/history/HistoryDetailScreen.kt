package com.pebbledetective.ui.history

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebbledetective.R
import com.pebbledetective.data.locale
import com.pebbledetective.domain.Planet
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.common.TopControls
import com.pebbledetective.ui.result.nameRes
import com.pebbledetective.ui.result.rememberPlanetImage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One pebble from the logbook: the photograph, its planet, and when and where. */
@Composable
fun HistoryDetailScreen(
    session: SessionViewModel,
    entryId: String,
    onBack: () -> Unit,
    onReplay: () -> Unit,
) {
    val language by session.language.collectAsStateWithLifecycle()
    val soundEnabled by session.soundEnabled.collectAsStateWithLifecycle()
    val entries by session.entries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val entry = remember(entries, entryId) { entries.firstOrNull { it.id == entryId } }
    var photo by remember(entryId) { mutableStateOf<Bitmap?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) { photo = session.photo(entryId) }

    // The entry can vanish under us after a delete.
    LaunchedEffect(entry) { if (entry == null) onBack() }
    val current = entry ?: return

    val planet = Planet.fromId(current.planetId)
    val formatter = remember(language, current.timeZoneId) {
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.FULL)
            .withLocale(language.locale)
            .withZone(ZoneId.of(current.timeZoneId))
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        TopControls(
            language = language,
            soundEnabled = soundEnabled,
            onCycleLanguage = session::cycleLanguage,
            onToggleSound = session::toggleSound,
            onOpenHistory = null,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                photo?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (planet != null) {
                val planetImage by rememberPlanetImage(planet)
                planetImage?.let {
                    Image(
                        bitmap = it,
                        contentDescription = stringResource(planet.nameRes),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(0.45f).aspectRatio(1f),
                    )
                }
                Text(
                    text = stringResource(planet.nameRes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = stringResource(R.string.history_unknown_planet),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                text = formatter.format(Instant.ofEpochMilli(current.capturedAtEpochMs)),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
            Text(
                text = if (current.latitude != null && current.longitude != null) {
                    stringResource(
                        R.string.result_found_at,
                        "%.4f, %.4f".format(current.latitude, current.longitude),
                    )
                } else {
                    stringResource(R.string.history_no_place)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )

            if (planet != null) {
                Button(
                    onClick = {
                        session.replay(current)
                        onReplay()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.history_replay))
                }
            }

            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.history_delete))
            }
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Text(stringResource(R.string.cd_back), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (confirmDelete) {
        // Deleting a child's keepsake is not something to do on one stray tap.
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.history_delete)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        session.deleteEntry(current.id)
                        onBack()
                    }
                }) { Text(stringResource(R.string.research_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.research_no))
                }
            },
        )
    }
}

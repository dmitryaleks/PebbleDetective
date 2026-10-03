package com.pebbledetective.ui.history

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebbledetective.R
import com.pebbledetective.data.AppLanguage
import com.pebbledetective.data.PebbleEntry
import com.pebbledetective.data.locale
import com.pebbledetective.domain.Planet
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.common.MapLink
import com.pebbledetective.ui.common.TopControls
import com.pebbledetective.ui.result.nameRes
import com.pebbledetective.ui.result.rememberPlanetImage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The collection, browsed one pebble at a time.
 *
 * Swipe left or right to move between stones without going back to the list,
 * which is how anyone actually wants to look through a collection. Arrows and
 * a position counter sit above the page so the gesture is discoverable and so
 * it still works for someone who cannot swipe.
 */
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

    // Deleting the last pebble, or arriving before the logbook has loaded,
    // leaves nothing to show.
    if (entries.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // Only the entry tapped in the list decides where the pager opens; it must
    // not jump when the list updates underneath, which a late location fix
    // does.
    val startPage = remember(entryId) {
        entries.indexOfFirst { it.id == entryId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = startPage) { entries.size }

    var confirmDelete by remember { mutableStateOf<PebbleEntry?>(null) }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        TopControls(
            language = language,
            soundEnabled = soundEnabled,
            onCycleLanguage = session::cycleLanguage,
            onToggleSound = session::toggleSound,
            onOpenHistory = null,
        )

        PagerControls(
            position = pagerState.currentPage,
            total = entries.size,
            onPrevious = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            },
            onNext = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            // The list can shrink mid-swipe when a pebble is deleted.
            val entry = entries.getOrNull(page) ?: return@HorizontalPager
            PebblePage(
                session = session,
                entry = entry,
                language = language,
                onReplay = {
                    session.replay(entry)
                    onReplay()
                },
                onDelete = { confirmDelete = entry },
                onBack = onBack,
            )
        }
    }

    confirmDelete?.let { doomed ->
        // Deleting a child's keepsake is not something to do on one stray tap.
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.history_delete)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        session.deleteEntry(doomed.id)
                        // Stay in the gallery unless that was the last one.
                        if (session.entries.value.isEmpty()) onBack()
                    }
                }) { Text(stringResource(R.string.research_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.research_no))
                }
            },
        )
    }
}

/** Position counter and arrows, so swiping is discoverable and not the only way. */
@Composable
private fun PagerControls(
    position: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = position > 0,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.gallery_previous),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Dots for a small collection, a counter once they would not fit.
            if (total in 2..8) {
                repeat(total) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == position) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == position) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.gallery_position, position + 1, total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        IconButton(
            onClick = onNext,
            enabled = position < total - 1,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.gallery_next),
            )
        }
    }
}

/** One pebble: the photograph, its planet, and when and where it was found. */
@Composable
private fun PebblePage(
    session: SessionViewModel,
    entry: PebbleEntry,
    language: AppLanguage,
    onReplay: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var photo by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.id) { photo = session.photo(entry.id) }

    val planet = Planet.fromId(entry.planetId)
    val formatter = remember(language, entry.timeZoneId) {
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.FULL)
            .withLocale(language.locale)
            .withZone(ZoneId.of(entry.timeZoneId))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .semantics {
                    contentDescription = entry.planetId ?: ""
                },
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
                    modifier = Modifier.fillMaxWidth(0.4f).aspectRatio(1f),
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
            text = formatter.format(Instant.ofEpochMilli(entry.capturedAtEpochMs)),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )

        val lat = entry.latitude
        val lon = entry.longitude
        if (lat != null && lon != null) {
            MapLink(latitude = lat, longitude = lon)
        } else {
            Text(
                text = stringResource(R.string.history_no_place),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }

        if (planet != null) {
            Button(onClick = onReplay, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.history_replay))
            }
        }

        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.history_delete))
        }
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text(stringResource(R.string.cd_back), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

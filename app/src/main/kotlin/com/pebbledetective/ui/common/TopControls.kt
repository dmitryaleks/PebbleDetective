package com.pebbledetective.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pebbledetective.R
import com.pebbledetective.data.AppLanguage

/**
 * Language, sound and logbook controls.
 *
 * Shown on every screen, because the spec requires the language to be
 * switchable at any stage of the flow.
 */
@Composable
fun TopControls(
    language: AppLanguage,
    soundEnabled: Boolean,
    onCycleLanguage: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenHistory: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        // statusBarsPadding is load-bearing: the app draws edge to edge, and
        // without it these controls sit under the system status bar, where it
        // swallows their taps.
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = language.tag.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = onCycleLanguage, modifier = Modifier.size(TOUCH_TARGET)) {
            Icon(Icons.Filled.Language, contentDescription = stringResource(R.string.cd_language))
        }
        IconButton(onClick = onToggleSound, modifier = Modifier.size(TOUCH_TARGET)) {
            Icon(
                imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = stringResource(
                    if (soundEnabled) R.string.cd_sound_on else R.string.cd_sound_off
                ),
            )
        }
        if (onOpenHistory != null) {
            IconButton(onClick = onOpenHistory, modifier = Modifier.size(TOUCH_TARGET)) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = stringResource(R.string.cd_history))
            }
        }
    }
}

/** Comfortably above the 48dp minimum, because the users are children. */
private val TOUCH_TARGET = 56.dp

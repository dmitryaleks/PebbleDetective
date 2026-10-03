package com.pebbledetective.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pebbledetective.R
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.common.TopControls

/** Where the bundled photographs and sounds came from. Full detail lives in ASSETS.md. */
@Composable
fun CreditsScreen(session: SessionViewModel, onBack: () -> Unit) {
    val language by session.language.collectAsStateWithLifecycle()
    val soundEnabled by session.soundEnabled.collectAsStateWithLifecycle()

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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.credits_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.credits_photos), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.credits_sounds), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.credits_nasa_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Text(stringResource(R.string.cd_back), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

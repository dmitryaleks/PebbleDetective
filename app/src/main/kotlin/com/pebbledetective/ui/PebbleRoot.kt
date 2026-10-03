package com.pebbledetective.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pebbledetective.core.AppContainer
import com.pebbledetective.ui.nav.PebbleNavHost
import com.pebbledetective.ui.theme.PebbleTheme

@Composable
fun PebbleRoot(container: AppContainer) {
    val session: SessionViewModel = viewModel(factory = SessionViewModel.factory(container))
    val language by session.language.collectAsStateWithLifecycle()

    // Language is applied here, around the whole app, so switching it never
    // recreates the Activity and never interrupts the camera or an animation.
    Localized(language) {
        PebbleTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                PebbleNavHost(session)
            }
        }
    }
}

package com.pebbledetective.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.pebbledetective.core.AppContainer
import com.pebbledetective.data.AppLanguage
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns everything that must outlive a screen.
 *
 * Durable session state lives here rather than in `remember`, so an unexpected
 * Activity recreation lands the child back where they were instead of at the
 * start of the flow.
 */
class SessionViewModel(private val container: AppContainer) : ViewModel() {

    val language: StateFlow<AppLanguage> = container.settings.language
    val soundEnabled: StateFlow<Boolean> = container.settings.soundEnabled

    fun cycleLanguage() = container.settings.cycleLanguage()

    fun toggleSound() {
        val enabled = !container.settings.soundEnabled.value
        container.settings.setSoundEnabled(enabled)
        if (!enabled) container.sound.stopAll()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    SessionViewModel(container) as T
            }
    }
}

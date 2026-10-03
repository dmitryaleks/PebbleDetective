package com.pebbledetective.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.pebbledetective.core.AppContainer
import com.pebbledetective.audio.SoundCue
import com.pebbledetective.audio.SoundPlayer
import com.pebbledetective.data.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    val sound: SoundPlayer get() = container.sound

    /**
     * The frozen capture. Held here rather than in `remember` so that an
     * unexpected Activity recreation cannot lose a child's photograph.
     */
    private val _captured = MutableStateFlow<CapturedPebble?>(null)
    val captured: StateFlow<CapturedPebble?> = _captured.asStateFlow()

    fun onCaptured(bitmap: Bitmap, normalisedTap: Offset) {
        _captured.value = CapturedPebble(bitmap, normalisedTap)
    }

    fun discardCapture() {
        _captured.value = null
    }

    fun cycleLanguage() {
        container.settings.cycleLanguage()
        container.sound.play(SoundCue.UI_TAP)
    }

    fun toggleSound() {
        val enabled = !container.settings.soundEnabled.value
        container.settings.setSoundEnabled(enabled)
        // Confirm the new state audibly when switching on; go quiet otherwise.
        if (enabled) container.sound.play(SoundCue.SIGNAL_ACQUIRED) else container.sound.stopAll()
    }

    /** A photograph plus where in it the child pointed, both normalised 0..1. */
    data class CapturedPebble(val bitmap: Bitmap, val tap: Offset)

    override fun onCleared() {
        container.sound.release()
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

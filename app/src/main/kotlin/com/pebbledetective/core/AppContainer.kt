package com.pebbledetective.core

import android.content.Context
import com.pebbledetective.audio.NoopSoundPlayer
import com.pebbledetective.audio.SoundPlayer
import com.pebbledetective.data.SettingsStore

/**
 * Manual dependency injection.
 *
 * One container, built by the Application and read by view models. Deliberately
 * not Hilt: the app is small and this keeps annotation processing out of the
 * build entirely.
 */
class AppContainer(context: Context) {
    val settings: SettingsStore = SettingsStore(context)

    // Replaced by the real SoundPool-backed player once the audio files land.
    val sound: SoundPlayer = NoopSoundPlayer
}

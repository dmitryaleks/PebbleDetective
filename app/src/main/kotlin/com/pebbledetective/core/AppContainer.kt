package com.pebbledetective.core

import android.content.Context
import com.pebbledetective.audio.AndroidSoundPlayer
import com.pebbledetective.audio.SoundPlayer
import com.pebbledetective.data.LocationProvider
import com.pebbledetective.data.PebbleRepository
import com.pebbledetective.data.PhotoStore
import com.pebbledetective.data.SettingsStore
import java.io.File

/**
 * Manual dependency injection.
 *
 * One container, built by the Application and read by view models.
 * Deliberately not Hilt: the app is small and this keeps annotation
 * processing out of the build entirely.
 */
class AppContainer(context: Context) {
    val settings: SettingsStore = SettingsStore(context)
    val sound: SoundPlayer = AndroidSoundPlayer(context, settings)
    val photos: PhotoStore = PhotoStore(context)
    val pebbles: PebbleRepository =
        PebbleRepository(File(context.filesDir, "pebbles")) { id -> photos.delete(id) }
    val location: LocationProvider = LocationProvider(context)
}

package com.pebbledetective.audio

/**
 * Every sound the app can make, one per moment in the flow.
 *
 * Declared up front so screens can reference cues before the audio files
 * themselves are bundled.
 */
enum class SoundCue {
    UI_TAP,
    RETICLE_LOCK,
    SHUTTER,
    POPUP_OPEN,
    SATELLITE_PING,
    SIGNAL_ACQUIRED,
    MATRIX_HUM,
    PROGRESS_COMPLETE,
    LAUNCH_WHOOSH,
    SPACE_DRONE,
    ENTRY_RUMBLE,
    ARRIVAL_CHIME,
}

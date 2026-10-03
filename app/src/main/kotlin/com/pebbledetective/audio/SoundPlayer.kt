package com.pebbledetective.audio

/**
 * Plays the app's sound cues.
 *
 * Call sites never check whether sound is enabled: when it is off, the player
 * is simply a no-op. Real playback arrives with the bundled audio files.
 */
interface SoundPlayer {
    fun play(cue: SoundCue)
    fun loop(cue: SoundCue)
    fun stop(cue: SoundCue)
    fun stopAll()
    fun release()
}

object NoopSoundPlayer : SoundPlayer {
    override fun play(cue: SoundCue) = Unit
    override fun loop(cue: SoundCue) = Unit
    override fun stop(cue: SoundCue) = Unit
    override fun stopAll() = Unit
    override fun release() = Unit
}

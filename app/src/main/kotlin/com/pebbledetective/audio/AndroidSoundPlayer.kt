package com.pebbledetective.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import com.pebbledetective.R
import com.pebbledetective.data.SettingsStore

/**
 * [SoundPool]-backed playback for the bundled cues.
 *
 * Three deliberate choices:
 *
 *  * The pool is built lazily, on the first cue played while sound is on.
 *    Sound is off by default, so a child who never enables it never costs an
 *    audio session.
 *  * [SoundPool.load] is asynchronous. A cue asked for before its sample has
 *    finished loading is remembered and played the moment it is ready, rather
 *    than silently dropped - which is what you would otherwise hear for the
 *    first second or two of the app's life.
 *  * Every entry point checks [SettingsStore.soundEnabled], so call sites never
 *    have to.
 */
class AndroidSoundPlayer(
    context: Context,
    private val settings: SettingsStore,
) : SoundPlayer {

    private val appContext = context.applicationContext

    private var pool: SoundPool? = null

    /** Cue -> sample id handed back by SoundPool.load. */
    private val sampleIds = mutableMapOf<SoundCue, Int>()

    /** Samples that have finished decoding and are safe to play. */
    private val ready = mutableSetOf<Int>()

    /** Cues asked for while their sample was still decoding. */
    private val pending = mutableMapOf<SoundCue, Boolean>()

    /** Currently playing streams, so a cue can be stopped by name. */
    private val streams = mutableMapOf<SoundCue, Int>()

    private val enabled: Boolean get() = settings.soundEnabled.value

    @Synchronized
    private fun ensurePool(): SoundPool {
        pool?.let { return it }
        val created = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()

        created.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            synchronized(this) {
                ready += sampleId
                // Anything requested while this sample was still decoding.
                val cue = sampleIds.entries.firstOrNull { it.value == sampleId }?.key
                val looping = cue?.let { pending.remove(it) }
                if (cue != null && looping != null && enabled) {
                    startStream(created, cue, sampleId, looping)
                }
            }
        }

        SoundCue.entries.forEach { cue ->
            sampleIds[cue] = created.load(appContext, cue.resId, 1)
        }
        pool = created
        return created
    }

    private fun startStream(pool: SoundPool, cue: SoundCue, sampleId: Int, looping: Boolean) {
        val id = pool.play(sampleId, VOLUME, VOLUME, 1, if (looping) -1 else 0, 1f)
        if (id != 0) streams[cue] = id
    }

    @Synchronized
    private fun trigger(cue: SoundCue, looping: Boolean) {
        if (!enabled) return
        val pool = ensurePool()
        val sampleId = sampleIds[cue] ?: return
        if (sampleId in ready) {
            startStream(pool, cue, sampleId, looping)
        } else {
            pending[cue] = looping
        }
    }

    override fun play(cue: SoundCue) = trigger(cue, looping = false)

    override fun loop(cue: SoundCue) = trigger(cue, looping = true)

    @Synchronized
    override fun stop(cue: SoundCue) {
        pending -= cue
        streams.remove(cue)?.let { pool?.stop(it) }
    }

    @Synchronized
    override fun stopAll() {
        pending.clear()
        streams.values.forEach { pool?.stop(it) }
        streams.clear()
    }

    @Synchronized
    override fun release() {
        stopAll()
        pool?.release()
        pool = null
        sampleIds.clear()
        ready.clear()
    }

    private companion object {
        const val MAX_STREAMS = 6
        const val VOLUME = 0.85f
    }
}

/** The bundled OGG backing each cue. See ASSETS.md for sources and licences. */
@get:RawRes
private val SoundCue.resId: Int
    get() = when (this) {
        SoundCue.UI_TAP -> R.raw.cue_ui_tap
        SoundCue.RETICLE_LOCK -> R.raw.cue_reticle_lock
        SoundCue.SHUTTER -> R.raw.cue_shutter
        SoundCue.POPUP_OPEN -> R.raw.cue_popup_open
        SoundCue.SATELLITE_PING -> R.raw.cue_satellite_ping
        SoundCue.SIGNAL_ACQUIRED -> R.raw.cue_signal_acquired
        SoundCue.MATRIX_HUM -> R.raw.cue_matrix_hum
        SoundCue.PROGRESS_COMPLETE -> R.raw.cue_progress_complete
        SoundCue.LAUNCH_WHOOSH -> R.raw.cue_launch_whoosh
        SoundCue.SPACE_DRONE -> R.raw.cue_space_drone
        SoundCue.ENTRY_RUMBLE -> R.raw.cue_entry_rumble
        SoundCue.ARRIVAL_CHIME -> R.raw.cue_arrival_chime
    }

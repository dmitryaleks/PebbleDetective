package com.pebbledetective.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.pebbledetective.audio.SoundCue
import com.pebbledetective.audio.SoundPlayer
import com.pebbledetective.core.AppContainer
import com.pebbledetective.data.AppLanguage
import com.pebbledetective.data.PebbleEntry
import com.pebbledetective.data.PebbleStatus
import com.pebbledetective.domain.Planet
import com.pebbledetective.domain.ResearchTimeline
import com.pebbledetective.domain.dominantColour
import com.pebbledetective.domain.pickPlanet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone
import java.util.UUID
import kotlin.math.min
import kotlin.random.Random

/**
 * Owns everything that must outlive a screen.
 *
 * Durable state lives here rather than in `remember`, so an unexpected
 * Activity recreation lands the child back where they were. Timed sequences
 * are driven from a wall-clock start time held here, not from a remembered
 * Animatable, so a language switch mid-sequence resumes on the same frame.
 */
class SessionViewModel(private val container: AppContainer) : ViewModel() {

    val language: StateFlow<AppLanguage> = container.settings.language
    val soundEnabled: StateFlow<Boolean> = container.settings.soundEnabled
    val sound: SoundPlayer get() = container.sound
    val entries: StateFlow<List<PebbleEntry>> = container.pebbles.entries

    /** A photograph plus where in it the child pointed, normalised to 0..1. */
    data class CapturedPebble(val bitmap: Bitmap, val tap: Offset)

    private val _captured = MutableStateFlow<CapturedPebble?>(null)
    val captured: StateFlow<CapturedPebble?> = _captured.asStateFlow()

    private val _researchStartedAt = MutableStateFlow<Long?>(null)
    val researchStartedAt: StateFlow<Long?> = _researchStartedAt.asStateFlow()

    private val _result = MutableStateFlow<PebbleEntry?>(null)
    val result: StateFlow<PebbleEntry?> = _result.asStateFlow()

    private var analysisJob: Job? = null
    private var cueJob: Job? = null

    init {
        viewModelScope.launch { container.pebbles.load() }
    }

    // ---- settings -------------------------------------------------------

    fun cycleLanguage() {
        container.settings.cycleLanguage()
        container.sound.play(SoundCue.UI_TAP)
    }

    fun toggleSound() {
        val enabled = !container.settings.soundEnabled.value
        container.settings.setSoundEnabled(enabled)
        if (enabled) container.sound.play(SoundCue.SIGNAL_ACQUIRED) else container.sound.stopAll()
    }

    // ---- capture --------------------------------------------------------

    fun onCaptured(bitmap: Bitmap, normalisedTap: Offset) {
        _captured.value = CapturedPebble(bitmap, normalisedTap)
        _result.value = null
        _researchStartedAt.value = null
        container.sound.play(SoundCue.POPUP_OPEN)
    }

    fun discardCapture() {
        analysisJob?.cancel()
        cueJob?.cancel()
        container.sound.stopAll()
        _captured.value = null
        _researchStartedAt.value = null
        _result.value = null
    }

    // ---- deep research --------------------------------------------------

    fun researchElapsedMs(): Long =
        _researchStartedAt.value?.let { SystemClock.elapsedRealtime() - it } ?: 0L

    private var askedForLocation = false

    /**
     * Whether to show the location prompt. Asked at most once per session,
     * and the answer never gates anything - a refusal just means the pebble
     * is logged without a place.
     */
    fun needsLocationPermission(): Boolean {
        if (askedForLocation || container.location.hasPermission()) return false
        askedForLocation = true
        return true
    }

    fun beginResearch() {
        if (_researchStartedAt.value != null) return
        val capture = _captured.value ?: return
        _researchStartedAt.value = SystemClock.elapsedRealtime()
        scheduleCues()
        analysisJob = viewModelScope.launch { analyse(capture) }
    }

    /** Jump to the end. A sequence a child cannot skip becomes hostile fast. */
    fun skipResearch() {
        val started = _researchStartedAt.value ?: return
        val target = SystemClock.elapsedRealtime() - ResearchTimeline.TOTAL_MS
        if (target < started) return
        cueJob?.cancel()
        container.sound.stopAll()
        _researchStartedAt.value = target
    }

    fun finishResearch() {
        cueJob?.cancel()
        container.sound.stop(SoundCue.MATRIX_HUM)
        container.sound.play(SoundCue.PROGRESS_COMPLETE)
    }

    /**
     * Sound cues run on their own schedule rather than off the frame loop:
     * frames can drop, and audio should not drift with them.
     */
    private fun scheduleCues() {
        cueJob?.cancel()
        cueJob = viewModelScope.launch {
            container.sound.loop(SoundCue.MATRIX_HUM)
            repeat(ResearchTimeline.PING_TOTAL) {
                container.sound.play(SoundCue.SATELLITE_PING)
                delay(400)
            }
            container.sound.play(SoundCue.SIGNAL_ACQUIRED)
        }
    }

    /**
     * The real work behind the theatre: look at the pebble, choose a planet,
     * note where we are, and write it all down.
     *
     * The record is written as soon as the photograph is on disk, before the
     * planet is known, so a process death mid-research can never lose a
     * child's pebble.
     */
    private suspend fun analyse(capture: CapturedPebble) {
        val id = UUID.randomUUID().toString()
        container.photos.save(id, capture.bitmap)

        val entry = PebbleEntry(
            id = id,
            capturedAtEpochMs = System.currentTimeMillis(),
            timeZoneId = TimeZone.getDefault().id,
            status = PebbleStatus.CAPTURED,
        )
        container.pebbles.append(entry)

        val colour = withContext(Dispatchers.Default) {
            val bitmap = capture.bitmap
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            dominantColour(
                pixels = pixels,
                width = bitmap.width,
                height = bitmap.height,
                centreX = (capture.tap.x * bitmap.width).toInt(),
                centreY = (capture.tap.y * bitmap.height).toInt(),
                radius = (min(bitmap.width, bitmap.height) * SAMPLE_FRACTION).toInt(),
            )
        }

        // Seed from the id so a grey pebble's random planet is stable, and
        // store the answer rather than ever recomputing it.
        val planet: Planet = pickPlanet(colour, Random(id.hashCode().toLong()))

        val researched = entry.copy(
            status = PebbleStatus.RESEARCHED,
            planetId = planet.id,
            dominantColourArgb = colour.toArgb(),
        )
        container.pebbles.update(researched)
        _result.value = researched

        // Location is deliberately *not* on the critical path. Waiting for a
        // fix before revealing the planet left the child watching a spinner
        // for the full timeout whenever no fix was available. The planet is
        // what they are waiting for; where they were standing is incidental
        // and can arrive late.
        attachLocation(researched)
    }

    /** Adds coordinates to an already-published result, if a fix turns up. */
    private suspend fun attachLocation(entry: PebbleEntry) {
        val location = container.location.currentLocation() ?: return
        val located = entry.copy(latitude = location.latitude, longitude = location.longitude)
        container.pebbles.update(located)
        // Only update the visible result if it is still this pebble.
        if (_result.value?.id == located.id) _result.value = located
    }

    override fun onCleared() {
        container.sound.release()
    }

    companion object {
        /** Sampling disc radius as a fraction of the short edge. */
        private const val SAMPLE_FRACTION = 0.12f

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    SessionViewModel(container) as T
            }
    }
}

/** HSV back to a packed ARGB colour, for the logbook swatch. */
private fun com.pebbledetective.domain.Hsv.toArgb(): Int {
    val c = value * saturation
    val x = c * (1 - kotlin.math.abs((hue / 60f) % 2 - 1))
    val m = value - c
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(v: Float) = (((v + m) * 255f).toInt()).coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

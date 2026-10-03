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
import com.pebbledetective.data.ShareCardText
import com.pebbledetective.domain.Geo
import com.pebbledetective.domain.GeoPoint
import com.pebbledetective.domain.Planet
import com.pebbledetective.domain.RadarTimeline
import com.pebbledetective.domain.JourneyTimeline
import com.pebbledetective.domain.ResearchTimeline
import com.pebbledetective.domain.dominantColour
import com.pebbledetective.domain.pickPlanet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

    private val _radar = MutableStateFlow(RadarState())
    val radar: StateFlow<RadarState> = _radar.asStateFlow()

    private var radarJob: Job? = null
    private var radarSoundJob: Job? = null
    private var detectionSoundJob: Job? = null

    private val _journeyStartedAt = MutableStateFlow<Long?>(null)
    val journeyStartedAt: StateFlow<Long?> = _journeyStartedAt.asStateFlow()

    private var analysisJob: Job? = null
    private var journeyCueJob: Job? = null
    private var cueJob: Job? = null

    /**
     * Outstanding location lookups. Kept apart from analysisJob so moving on
     * to the next pebble cannot cancel them; they are abandoned only when the
     * view model itself goes away.
     */
    private val locationJobs = mutableListOf<Job>()

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
        journeyCueJob?.cancel()
        _journeyStartedAt.value = null
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
        val target = ResearchTimeline.skippedStart(started, SystemClock.elapsedRealtime())
            ?: return
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

        // Kick the location request off now, in parallel with the colour
        // analysis, and on a job of its own. See attachLocation for why it
        // must not live inside analysisJob.
        locationJobs.removeAll { it.isCompleted }
        locationJobs += viewModelScope.launch { attachLocation(id) }

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

        // Transform whatever is current rather than the snapshot taken
        // before the analysis began. A cached location fix returns instantly,
        // so by now the location job may already have written coordinates -
        // and writing the old copy back would erase them.
        container.pebbles.updateWhere(id) { current ->
            current.copy(
                status = PebbleStatus.RESEARCHED,
                planetId = planet.id,
                dominantColourArgb = colour.toArgb(),
            )
        }
        _result.value = container.pebbles.find(id)
    }

    /**
     * Adds coordinates to a pebble once a fix turns up.
     *
     * Runs on its own job, deliberately *not* inside analysisJob. It used to
     * be the last statement of analyse(), which meant discardCapture() -
     * which "Find another pebble" calls - cancelled it mid-wait. Anyone
     * moving briskly from one stone to the next therefore lost the location
     * on almost every pebble; only the ones they happened to linger over
     * recorded a place.
     *
     * The entry is re-read and transformed under the repository lock, so a
     * fix arriving after the planet was decided cannot write the planet back
     * out again.
     */
    private suspend fun attachLocation(id: String) {
        val location = container.location.currentLocation() ?: return
        container.pebbles.updateWhere(id) { current ->
            current.copy(latitude = location.latitude, longitude = location.longitude)
        }
        // Refresh the visible result only if it is still this pebble.
        if (_result.value?.id == id) _result.value = container.pebbles.find(id)
    }

    // ---- radar ----------------------------------------------------------

    fun hasPreciseLocation(): Boolean = container.location.hasPreciseLocation()

    /** Called after the radar permission dialog closes, whatever the answer. */
    fun onRadarPermissionResult() {
        if (container.location.hasPreciseLocation()) {
            startRadar()
        } else {
            _radar.value = RadarState(preciseLocation = false)
        }
    }

    /**
     * Hides a fresh pebble and starts tracking.
     *
     * Pressing Radar again lands here, which is what re-rolls the target -
     * the brief asks for a new hiding place each time rather than resuming
     * the old hunt.
     */
    fun startRadar() {
        radarJob?.cancel()
        if (!container.location.hasPreciseLocation()) {
            _radar.value = RadarState(preciseLocation = false)
            return
        }
        _radar.value = RadarState(preciseLocation = true, startedAtElapsedMs = SystemClock.elapsedRealtime())
        container.sound.play(SoundCue.SATELLITE_PING)
        startRadarSounds()

        radarJob = viewModelScope.launch {
            container.location.locationUpdates().collectLatest { location ->
                val here = GeoPoint(location.latitude, location.longitude)
                val state = _radar.value

                // The first fix fixes the hiding place; later ones only move
                // the player. Re-rolling on every update would make the
                // pebble run away as you walked toward it.
                val target = state.target ?: Geo.randomTargetNear(here, Random.Default)

                val distance = Geo.distanceMetres(here, target)
                val found = Geo.isFound(distance)
                if (found && !state.found) container.sound.play(SoundCue.ARRIVAL_CHIME)

                _radar.value = state.copy(
                    target = target,
                    here = here,
                    distanceMetres = distance,
                    bearingDegrees = Geo.bearingDegrees(here, target),
                    found = found,
                )
            }
        }
    }

    fun stopRadar() {
        radarJob?.cancel()
        radarJob = null
        radarSoundJob?.cancel()
        radarSoundJob = null
        container.sound.stop(SoundCue.RADAR_AMBIENT)
    }

    /**
     * The radar's soundtrack: a room tone, a ping as the sweep comes round,
     * and a proximity beep that quickens as the pebble gets closer.
     *
     * The ping shares its period with the drawn sweep via RadarTimeline, and
     * both clocks start together, so the beep lands with the sweep crossing
     * the top of the scope rather than drifting against it.
     */
    private fun startRadarSounds() {
        radarSoundJob?.cancel()
        radarSoundJob = viewModelScope.launch {
            launch { holdAmbience(SoundCue.RADAR_AMBIENT) }

            launch {
                while (true) {
                    delay(RadarTimeline.SWEEP_PERIOD_MS)
                    container.sound.play(SoundCue.RADAR_PING)
                }
            }

            launch {
                while (true) {
                    val distance = _radar.value.distanceMetres
                    if (distance == null) {
                        delay(400)
                        continue
                    }
                    // Silent once found; the chime already said so, and an
                    // unending chatter while standing on the target is nasty.
                    if (!_radar.value.found) container.sound.play(SoundCue.RADAR_CLOSE)
                    delay(RadarTimeline.proximityIntervalMs(distance, _radar.value.rangeMetres))
                }
            }
        }
    }

    // ---- detection ambience ---------------------------------------------

    /**
     * The scanner idling on the camera screen, before anything is tapped.
     *
     * Started and stopped by the capture screen itself rather than tied to a
     * session state, because it belongs to being on that screen.
     */
    fun startDetectionAmbience() {
        detectionSoundJob?.cancel()
        detectionSoundJob = viewModelScope.launch {
            launch { holdAmbience(SoundCue.SCANNER_AMBIENT) }
            launch {
                // Irregular on purpose: a metronome reads as a fault tone
                // rather than a machine thinking.
                var servoNext = true
                while (true) {
                    delay(Random.nextLong(1_400, 3_600))
                    container.sound.play(
                        if (servoNext) SoundCue.SERVO else SoundCue.SCANNER_BLIP
                    )
                    servoNext = !servoNext
                }
            }
        }
    }

    fun stopDetectionAmbience() {
        detectionSoundJob?.cancel()
        detectionSoundJob = null
        container.sound.stop(SoundCue.SCANNER_AMBIENT)
    }

    /**
     * Keeps a looping bed playing for as long as this coroutine lives, and
     * follows the sound toggle.
     *
     * Without watching the setting, turning sound on midway through a screen
     * would leave it silent until the screen was re-entered.
     */
    private suspend fun holdAmbience(cue: SoundCue) {
        try {
            container.settings.soundEnabled.collectLatest { enabled ->
                if (enabled) container.sound.loop(cue) else container.sound.stop(cue)
            }
        } finally {
            container.sound.stop(cue)
        }
    }

    // ---- logbook --------------------------------------------------------

    /** The logbook list only ever decodes thumbnails, never full frames. */
    suspend fun thumbnail(id: String): Bitmap? = container.photos.loadThumb(id)

    suspend fun photo(id: String): Bitmap? = container.photos.loadPhoto(id)

    fun entry(id: String): PebbleEntry? = container.pebbles.find(id)

    suspend fun deleteEntry(id: String) {
        container.pebbles.delete(id)
    }

    suspend fun storageBytes(): Long = container.photos.bytesUsed()

    /**
     * Renders a shareable summary card and returns a URI for the share sheet.
     *
     * The planet photograph is read from assets here rather than taken from
     * the screen, so the card is the same whatever the device was showing.
     */
    suspend fun shareCard(entry: PebbleEntry, text: ShareCardText): android.net.Uri? {
        val photo = container.photos.loadPhoto(entry.id) ?: return null
        val planet = Planet.fromId(entry.planetId)?.let { loadPlanetAsset(it) }
        return container.shareCards.render(entry.id, photo, planet, text)
    }

    private suspend fun loadPlanetAsset(planet: Planet): android.graphics.Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                container.context.assets.open(planet.assetPath).use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
        }

    /** The original camera JPEG, for saving out of the app. */
    suspend fun rawPhotoBytes(id: String): ByteArray? = withContext(Dispatchers.IO) {
        container.photos.photoFile(id).takeIf { it.exists() }?.readBytes()
    }

    /** Re-runs the flight for an entry opened from the logbook. */
    fun replay(entry: PebbleEntry) {
        _result.value = entry
        resetJourney()
    }

    // ---- journey --------------------------------------------------------

    fun journeyElapsedMs(): Long =
        _journeyStartedAt.value?.let { SystemClock.elapsedRealtime() - it } ?: 0L

    fun beginJourney() {
        if (_journeyStartedAt.value != null) return
        _journeyStartedAt.value = SystemClock.elapsedRealtime()
        journeyCueJob?.cancel()
        journeyCueJob = viewModelScope.launch {
            // Cues follow their own clock rather than the frame loop, so a
            // dropped frame cannot nudge the soundtrack out of step.
            delay(1_400)
            container.sound.play(SoundCue.LAUNCH_WHOOSH)
            container.sound.loop(SoundCue.SPACE_DRONE)
            delay(7_600)
            container.sound.stop(SoundCue.SPACE_DRONE)
            container.sound.play(SoundCue.ENTRY_RUMBLE)
            // Falling through the map: an altimeter ping every second,
            // quickening as the ground comes up.
            delay(2_500)
            container.sound.play(SoundCue.SERVO)
            // Eight pings over the seven seconds of the descent, each gap a
            // little shorter than the last, landing on touchdown.
            repeat(8) {
                container.sound.play(SoundCue.SCANNER_BLIP)
                delay(1_050 - it * 50L)
            }
            container.sound.play(SoundCue.ARRIVAL_CHIME)
        }
    }

    /** By the third pebble an unskippable twenty-second film is hostile. */
    fun skipJourney() {
        val started = _journeyStartedAt.value ?: return
        val target = JourneyTimeline.skippedStart(started, SystemClock.elapsedRealtime())
            ?: return
        journeyCueJob?.cancel()
        container.sound.stopAll()
        container.sound.play(SoundCue.ARRIVAL_CHIME)
        _journeyStartedAt.value = target
    }

    fun finishJourney() {
        journeyCueJob?.cancel()
        container.sound.stop(SoundCue.SPACE_DRONE)
    }

    fun resetJourney() {
        journeyCueJob?.cancel()
        container.sound.stopAll()
        _journeyStartedAt.value = null
    }

    override fun onCleared() {
        radarJob?.cancel()
        radarSoundJob?.cancel()
        detectionSoundJob?.cancel()
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

/** What the radar screen needs to draw itself. */
data class RadarState(
    val preciseLocation: Boolean = true,
    /** When the hunt began, for the sweep and the ping to share a clock. */
    val startedAtElapsedMs: Long = 0L,
    /** Where the pebble is hidden, once the first fix has arrived. */
    val target: GeoPoint? = null,
    val here: GeoPoint? = null,
    val distanceMetres: Double? = null,
    /** Bearing to the target from true position, degrees clockwise from north. */
    val bearingDegrees: Double? = null,
    val found: Boolean = false,
) {
    /** The outer ring. Targets are hidden within 30m, so this always contains one. */
    val rangeMetres: Double get() = 30.0
}

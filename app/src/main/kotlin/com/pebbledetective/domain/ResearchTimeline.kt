package com.pebbledetective.domain

/** The beats of the Deep Research sequence. */
enum class ResearchPhase { ACQUIRING, SIGNAL, ANALYSING, MATCH }

/**
 * The five-second Deep Research sequence, as a pure function of elapsed time.
 *
 * Nothing here knows about Compose or Android, so "at 4800ms the phase is
 * MATCH and progress is at least 0.95" is an ordinary JVM test.
 *
 * The UI reads this from a wall-clock elapsed time held in the view model
 * rather than from a remembered Animatable. That means a language switch
 * mid-research resumes at the same frame instead of restarting, and the
 * sequence is immune to ANIMATOR_DURATION_SCALE being 0, which would
 * otherwise make the whole thing complete instantly.
 */
object ResearchTimeline {

    const val TOTAL_MS = 5_000L

    private const val ACQUIRING_END = 1_300L
    private const val SIGNAL_END = 1_700L
    private const val ANALYSING_END = 4_300L

    fun phaseAt(elapsedMs: Long): ResearchPhase = when {
        elapsedMs < ACQUIRING_END -> ResearchPhase.ACQUIRING
        elapsedMs < SIGNAL_END -> ResearchPhase.SIGNAL
        elapsedMs < ANALYSING_END -> ResearchPhase.ANALYSING
        else -> ResearchPhase.MATCH
    }

    /** Overall progress 0..1, spanning the whole sequence as the brief asks. */
    fun progressAt(elapsedMs: Long): Float =
        (elapsedMs.toFloat() / TOTAL_MS).coerceIn(0f, 1f)

    fun isComplete(elapsedMs: Long): Boolean = elapsedMs >= TOTAL_MS

    /**
     * The start time that jumps the sequence to its end, or null when there
     * is nothing left to skip.
     *
     * Skipping works by moving the *start* backwards so the elapsed time
     * lands on [TOTAL_MS]. The returned value is therefore earlier than
     * [startedAt], which is easy to mistake for a rewind and guard against
     * by accident - doing so disables the skip button entirely.
     */
    fun skippedStart(startedAt: Long, nowMs: Long): Long? =
        if (isComplete(nowMs - startedAt)) null else nowMs - TOTAL_MS

    /**
     * How many analysis lines should have streamed in by now, so the readout
     * fills steadily instead of appearing all at once.
     */
    fun visibleLines(elapsedMs: Long, totalLines: Int): Int {
        if (elapsedMs < SIGNAL_END) return 0
        val span = (ANALYSING_END - SIGNAL_END).toFloat()
        val through = ((elapsedMs - SIGNAL_END) / span).coerceIn(0f, 1f)
        return (through * totalLines).toInt().coerceIn(0, totalLines)
    }

    /** Satellite pings while the signal is being acquired, about every 400ms. */
    fun pingCount(elapsedMs: Long): Int =
        if (elapsedMs >= ACQUIRING_END) PING_TOTAL
        else (elapsedMs / 400L).toInt().coerceAtMost(PING_TOTAL)

    const val PING_TOTAL = 3
}

/** The beats of the pebble's flight home. */
enum class JourneyPhase {
    SYSTEM, CLOSING, DEPARTURE, LAUNCH, CRUISE, ENTRY, APPROACH, DESCENT, TOUCHDOWN
}

/**
 * The journey, as a pure function of elapsed time.
 *
 * Longer than the brief's original ten to fifteen seconds, in both
 * directions. It opens on the whole solar system as it actually stands on
 * the day the pebble was found and closes in on the two worlds that matter;
 * and at the far end it carries on past atmospheric entry, down through a
 * schematic Japan and in over Tokyo to the spot where the pebble lands.
 * Both ends were added because the middle was the only part anyone
 * remembered.
 */
object JourneyTimeline {

    const val TOTAL_MS = 25_000L

    private const val SYSTEM_END = 4_000L
    private const val CLOSING_END = 7_000L
    private const val DEPARTURE_END = 8_200L
    private const val LAUNCH_END = 9_200L
    private const val CRUISE_END = 15_000L
    private const val ENTRY_END = 17_500L
    private const val APPROACH_END = 20_500L
    private const val DESCENT_END = 23_500L

    fun phaseAt(elapsedMs: Long): JourneyPhase = when {
        elapsedMs < SYSTEM_END -> JourneyPhase.SYSTEM
        elapsedMs < CLOSING_END -> JourneyPhase.CLOSING
        elapsedMs < DEPARTURE_END -> JourneyPhase.DEPARTURE
        elapsedMs < LAUNCH_END -> JourneyPhase.LAUNCH
        elapsedMs < CRUISE_END -> JourneyPhase.CRUISE
        elapsedMs < ENTRY_END -> JourneyPhase.ENTRY
        elapsedMs < APPROACH_END -> JourneyPhase.APPROACH
        elapsedMs < DESCENT_END -> JourneyPhase.DESCENT
        else -> JourneyPhase.TOUCHDOWN
    }

    fun progressAt(elapsedMs: Long): Float =
        (elapsedMs.toFloat() / TOTAL_MS).coerceIn(0f, 1f)

    fun isComplete(elapsedMs: Long): Boolean = elapsedMs >= TOTAL_MS

    /**
     * The start time that jumps the sequence to its end, or null when there
     * is nothing left to skip.
     *
     * Skipping works by moving the *start* backwards so the elapsed time
     * lands on [TOTAL_MS]. The returned value is therefore earlier than
     * [startedAt], which is easy to mistake for a rewind and guard against
     * by accident - doing so disables the skip button entirely.
     */
    fun skippedStart(startedAt: Long, nowMs: Long): Long? =
        if (isComplete(nowMs - startedAt)) null else nowMs - TOTAL_MS

    /**
     * How far along its arc the pebble is between the worlds, 0 at the source
     * planet and 1 on arrival at Earth. It only starts moving once it has
     * launched, and is finished once the atmosphere is reached.
     */
    fun travel(elapsedMs: Long): Float {
        if (elapsedMs <= LAUNCH_END) return 0f
        val span = (ENTRY_END - LAUNCH_END).toFloat()
        return ((elapsedMs - LAUNCH_END) / span).coerceIn(0f, 1f)
    }

    /**
     * The opening shot: 0 as the solar system appears, 1 by the time the
     * camera has settled on the two worlds that matter.
     *
     * Eased hard at the end so the move lands rather than stopping dead,
     * and so the first second or so is almost still - long enough to read
     * the thing as a map of where everything actually is.
     */
    fun closing(elapsedMs: Long): Float {
        if (elapsedMs <= SYSTEM_END) return 0f
        val span = (CLOSING_END - SYSTEM_END).toFloat()
        val t = ((elapsedMs - SYSTEM_END) / span).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * How far the opening view has drifted while it is being looked at.
     * A still picture of the solar system reads as a diagram; a slowly
     * turning one reads as somewhere you are.
     */
    fun systemDrift(elapsedMs: Long): Float =
        (elapsedMs.coerceAtMost(CLOSING_END).toFloat() / CLOSING_END)

    /**
     * The pebble tearing itself off the source planet: 0 still buried, 1
     * clear of the surface and on its way.
     */
    fun breakout(elapsedMs: Long): Float {
        if (elapsedMs <= CLOSING_END) return 0f
        val span = (LAUNCH_END - CLOSING_END).toFloat()
        return ((elapsedMs - CLOSING_END) / span).coerceIn(0f, 1f)
    }

    /**
     * How hard the pebble itself is burning. Nothing for most of the
     * crossing, then a sheath of fire through the last of it as Earth's
     * air starts to bite.
     */
    fun pebbleFire(elapsedMs: Long): Float {
        val travel = travel(elapsedMs)
        if (travel < FIRE_FROM) return 0f
        return ((travel - FIRE_FROM) / (1f - FIRE_FROM)).coerceIn(0f, 1f)
    }

    private const val FIRE_FROM = 0.62f

    /**
     * The descent zoom, 0 looking at the whole of Japan and 1 down on the
     * part of Tokyo where the pebble lands.
     *
     * Eased at both ends rather than linear. The map scale is interpolated
     * geometrically, so a linear parameter whips through the country view
     * in the first half-second and then crawls; smoothstep holds the wide
     * shot long enough to recognise Japan and settles gently over Tokyo.
     */
    fun descentZoom(elapsedMs: Long): Float {
        if (elapsedMs <= ENTRY_END) return 0f
        val span = (DESCENT_END - ENTRY_END).toFloat()
        val t = ((elapsedMs - ENTRY_END) / span).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * How far the schematic map of Japan has faded in under the fireball,
     * 0 while still in space and 1 once the descent view has taken over.
     *
     * The swap happens late in the entry phase on purpose, and quickly. The
     * heat glow is at its brightest then, and Earth has grown to most of
     * the screen, so there is the least possible difference in scale
     * between the photograph being left and the map being arrived at.
     */
    fun mapReveal(elapsedMs: Long): Float {
        if (elapsedMs <= MAP_REVEAL_START) return 0f
        val span = (ENTRY_END - MAP_REVEAL_START).toFloat()
        return ((elapsedMs - MAP_REVEAL_START) / span).coerceIn(0f, 1f)
    }

    private const val MAP_REVEAL_START = 16_900L

    /**
     * How far into the landing itself, 0 the moment before touchdown and 1
     * once the impact rings have finished spreading.
     */
    fun landedFraction(elapsedMs: Long): Float {
        if (elapsedMs <= DESCENT_END) return 0f
        val span = (TOTAL_MS - DESCENT_END).toFloat()
        return ((elapsedMs - DESCENT_END) / span).coerceIn(0f, 1f)
    }

    /**
     * Altitude in kilometres for the HUD, from the top of the atmosphere
     * down to the ground. Theatre, not physics - it falls linearly so the
     * number counts down at a readable pace.
     */
    fun altitudeKm(elapsedMs: Long): Int {
        if (elapsedMs <= CRUISE_END) return TOP_OF_ATMOSPHERE_KM
        val span = (DESCENT_END - CRUISE_END).toFloat()
        val through = ((elapsedMs - CRUISE_END) / span).coerceIn(0f, 1f)
        return ((1f - through) * TOP_OF_ATMOSPHERE_KM).toInt()
    }

    private const val TOP_OF_ATMOSPHERE_KM = 120

    /**
     * How hard the air is glowing: a slow swell through entry, then out
     * quickly once the map is showing. The glow has to cover the cut from
     * starfield to map, but it tints the whole country orange while it
     * lasts, so it does not outstay its welcome.
     */
    fun entryHeat(elapsedMs: Long): Float = when {
        elapsedMs < CRUISE_END -> 0f
        elapsedMs < ENTRY_END -> ((elapsedMs - CRUISE_END).toFloat() / (ENTRY_END - CRUISE_END))
        elapsedMs < HEAT_END -> 1f - ((elapsedMs - ENTRY_END).toFloat() / (HEAT_END - ENTRY_END))
        else -> 0f
    }.coerceIn(0f, 1f)

    private const val HEAT_END = 19_000L
}

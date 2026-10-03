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
enum class JourneyPhase { DEPARTURE, LAUNCH, CRUISE, ENTRY, APPROACH, DESCENT, TOUCHDOWN }

/**
 * The journey, as a pure function of elapsed time.
 *
 * Longer than the brief's original ten to fifteen seconds, deliberately: the
 * flight now carries on past atmospheric entry, down through a schematic
 * Japan and in over Tokyo to the spot where the pebble lands. The arrival is
 * the payoff, and it was previously over in a second.
 */
object JourneyTimeline {

    const val TOTAL_MS = 20_000L

    private const val DEPARTURE_END = 1_500L
    private const val LAUNCH_END = 2_500L
    private const val CRUISE_END = 9_000L
    private const val ENTRY_END = 11_500L
    private const val APPROACH_END = 15_000L
    private const val DESCENT_END = 18_500L

    fun phaseAt(elapsedMs: Long): JourneyPhase = when {
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
     * The swap happens inside the entry phase on purpose: the heat glow is
     * at its brightest then, so it covers the cut between the starfield and
     * the map rather than the two being seen to replace each other.
     */
    fun mapReveal(elapsedMs: Long): Float {
        if (elapsedMs <= MAP_REVEAL_START) return 0f
        val span = (ENTRY_END - MAP_REVEAL_START).toFloat()
        return ((elapsedMs - MAP_REVEAL_START) / span).coerceIn(0f, 1f)
    }

    private const val MAP_REVEAL_START = 10_300L

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

    private const val HEAT_END = 13_000L
}

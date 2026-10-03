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
enum class JourneyPhase { DEPARTURE, LAUNCH, CRUISE, ENTRY, LANDING }

/**
 * The twelve-second journey, again as a pure function of elapsed time, and
 * within the 10-15s the brief asks for.
 */
object JourneyTimeline {

    const val TOTAL_MS = 12_000L

    private const val DEPARTURE_END = 1_500L
    private const val LAUNCH_END = 2_500L
    private const val CRUISE_END = 9_500L
    private const val ENTRY_END = 11_000L

    fun phaseAt(elapsedMs: Long): JourneyPhase = when {
        elapsedMs < DEPARTURE_END -> JourneyPhase.DEPARTURE
        elapsedMs < LAUNCH_END -> JourneyPhase.LAUNCH
        elapsedMs < CRUISE_END -> JourneyPhase.CRUISE
        elapsedMs < ENTRY_END -> JourneyPhase.ENTRY
        else -> JourneyPhase.LANDING
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
     * How far along its arc the pebble is, 0 at the source planet and 1 at
     * Earth. It only starts moving once it has launched.
     */
    fun travel(elapsedMs: Long): Float {
        if (elapsedMs <= LAUNCH_END) return 0f
        val span = (ENTRY_END - LAUNCH_END).toFloat()
        return ((elapsedMs - LAUNCH_END) / span).coerceIn(0f, 1f)
    }
}

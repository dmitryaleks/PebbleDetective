package com.pebbledetective.domain

/**
 * The radar's rhythms, shared by the drawing and the sound.
 *
 * Both live here so the ping actually lands with the sweep crossing the top
 * of the scope; two separate constants drift apart the moment one is tuned.
 */
object RadarTimeline {

    /** One revolution of the sweep. Slow on purpose: a fast strobe is a seizure risk. */
    const val SWEEP_PERIOD_MS = 2_400L

    /** The target blip's expanding ring. */
    const val PULSE_PERIOD_MS = 1_400L

    private const val FASTEST_BEEP_MS = 150L
    private const val SLOWEST_BEEP_MS = 1_100L

    /**
     * How long to wait before the next proximity beep.
     *
     * The classic detector feel: a lazy tick far out, accelerating to an
     * urgent chatter as the pebble gets close. Squared so the speed-up is
     * felt over the last few metres rather than spread flatly across the
     * whole range.
     */
    fun proximityIntervalMs(distanceMetres: Double, rangeMetres: Double = 30.0): Long {
        if (rangeMetres <= 0.0) return FASTEST_BEEP_MS
        val t = (distanceMetres / rangeMetres).coerceIn(0.0, 1.0)
        val eased = t * t
        return (FASTEST_BEEP_MS + (SLOWEST_BEEP_MS - FASTEST_BEEP_MS) * eased).toLong()
    }

    /** Sweep angle in degrees for a given elapsed time. */
    fun sweepDegrees(elapsedMs: Long): Float =
        (elapsedMs % SWEEP_PERIOD_MS).toFloat() / SWEEP_PERIOD_MS * 360f

    /** Pulse phase 0..1 for the blip's expanding ring. */
    fun pulsePhase(elapsedMs: Long): Float =
        (elapsedMs % PULSE_PERIOD_MS).toFloat() / PULSE_PERIOD_MS
}

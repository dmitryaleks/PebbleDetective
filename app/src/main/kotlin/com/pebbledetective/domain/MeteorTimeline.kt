package com.pebbledetective.domain

/** The beats of a meteor falling out of the sky. */
enum class MeteorPhase { IGNITION, FALL, IMPACT, SETTLING }

/**
 * A piece of a planet coming down nearby, as a pure function of time.
 *
 * Same pattern as the research, journey and title sequences: a wall-clock
 * elapsed time in, a scene out, so the beats are an ordinary JVM test and
 * the sound cues can be scheduled against the same numbers the drawing
 * uses rather than against the frame loop.
 */
object MeteorTimeline {

    const val TOTAL_MS = 5_200L

    private const val IGNITION_END = 700L
    private const val FALL_END = 3_000L
    private const val IMPACT_END = 3_500L

    fun phaseAt(elapsedMs: Long): MeteorPhase = when {
        elapsedMs < IGNITION_END -> MeteorPhase.IGNITION
        elapsedMs < FALL_END -> MeteorPhase.FALL
        elapsedMs < IMPACT_END -> MeteorPhase.IMPACT
        else -> MeteorPhase.SETTLING
    }

    fun isComplete(elapsedMs: Long): Boolean = elapsedMs >= TOTAL_MS

    /**
     * How far the meteor has come, 0 at the planet and 1 on the ground.
     *
     * Accelerating rather than linear: it is falling, and a constant speed
     * reads as a thrown ball rather than as something arriving from space.
     */
    fun travel(elapsedMs: Long): Float {
        if (elapsedMs <= IGNITION_END) return 0f
        val t = ((elapsedMs - IGNITION_END).toFloat() / (FALL_END - IGNITION_END)).coerceIn(0f, 1f)
        return t * t * (2f - t * 0.6f) / 1.4f
    }

    /** The glow around the body as it tears into thicker air. */
    fun heat(elapsedMs: Long): Float = when {
        elapsedMs < IGNITION_END -> (elapsedMs.toFloat() / IGNITION_END).coerceIn(0f, 1f) * 0.45f
        elapsedMs < FALL_END -> 0.45f + 0.55f * travel(elapsedMs)
        else -> 0f
    }

    /**
     * The flash on landing. Brief and single, not a strobe: a repeated
     * full-screen flicker in a children's app is a seizure risk, and one
     * hard flash reads as an impact far better anyway.
     */
    fun flash(elapsedMs: Long): Float {
        if (elapsedMs < FALL_END || elapsedMs >= IMPACT_END) return 0f
        val t = (elapsedMs - FALL_END).toFloat() / (IMPACT_END - FALL_END)
        // Instant on, quick decay.
        return (1f - t) * (1f - t)
    }

    /** The dust ring spreading from the landing point, 0 until touchdown. */
    fun shockwave(elapsedMs: Long): Float {
        if (elapsedMs < FALL_END) return 0f
        return ((elapsedMs - FALL_END).toFloat() / (TOTAL_MS - FALL_END)).coerceIn(0f, 1f)
    }

    /** How brightly the crater is still glowing. */
    fun emberGlow(elapsedMs: Long): Float {
        if (elapsedMs < FALL_END) return 0f
        val t = ((elapsedMs - FALL_END).toFloat() / (TOTAL_MS - FALL_END)).coerceIn(0f, 1f)
        return 1f - t * t
    }

    /** When each sound lands, in milliseconds from the tap. */
    const val CUE_IGNITE_MS = 120L
    const val CUE_ENTRY_MS = 1_300L
    const val CUE_IMPACT_MS = FALL_END
    const val CUE_SETTLED_MS = 4_100L
}

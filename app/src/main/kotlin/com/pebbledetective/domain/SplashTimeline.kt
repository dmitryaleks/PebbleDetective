package com.pebbledetective.domain

/**
 * The opening title sequence, as a pure function of elapsed time.
 *
 * Same shape as the other two timelines in the app: wall-clock driven and
 * free of Compose, so the timings are an ordinary JVM test and a language
 * switch or a recomposition cannot restart it.
 *
 * Four seconds, and every beat is stretched rather than the last frame
 * being held: the sky washes in more slowly, the planets arrive further
 * apart, and the wordmark has time to land. A splash screen is still a toll
 * the player pays on every launch, so it stays skippable with a tap.
 */
object SplashTimeline {

    const val TOTAL_MS = 4_200L

    /** The sky itself, which has to be there before anything can be on it. */
    fun sky(elapsedMs: Long): Float = ramp(elapsedMs, 0L, 750L)

    /**
     * One body of the chain, staggered left to right so the solar system
     * assembles rather than appearing all at once.
     */
    fun body(index: Int, elapsedMs: Long): Float {
        val start = BODIES_START + index * BODY_STAGGER
        return ramp(elapsedMs, start, start + BODY_FADE)
    }

    /** How much of the pebble's trail has been drawn in. */
    fun trail(elapsedMs: Long): Float = ramp(elapsedMs, 1_050L, 2_175L)

    fun pebble(elapsedMs: Long): Float = ramp(elapsedMs, 1_350L, 1_950L)

    /**
     * The targeting brackets, which fly in from outside the frame. 0 is wide
     * open, 1 is locked on - the same gesture the capture screen makes.
     */
    fun reticle(elapsedMs: Long): Float = ramp(elapsedMs, 1_725L, 2_475L)

    fun title(elapsedMs: Long): Float = ramp(elapsedMs, 2_025L, 3_075L)

    fun isComplete(elapsedMs: Long): Boolean = elapsedMs >= TOTAL_MS

    /** Eased, so nothing in the sequence arrives at a constant rate. */
    private fun ramp(elapsedMs: Long, from: Long, to: Long): Float {
        val t = ((elapsedMs - from).toFloat() / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Everything the sequence brings in one at a time. */
    const val BODY_COUNT = 10
    private const val BODIES_START = 225L
    private const val BODY_STAGGER = 105L
    private const val BODY_FADE = 780L
}

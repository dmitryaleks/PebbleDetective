package com.pebbledetective.domain

/**
 * The opening title sequence, as a pure function of elapsed time.
 *
 * Same shape as the other two timelines in the app: wall-clock driven and
 * free of Compose, so the timings are an ordinary JVM test and a language
 * switch or a recomposition cannot restart it.
 *
 * Short on purpose. A splash screen is a toll the player pays on every
 * launch, and this one earns its place only by being over before they have
 * finished looking at it - and by being skippable with a tap.
 */
object SplashTimeline {

    const val TOTAL_MS = 2_800L

    /** The sky itself, which has to be there before anything can be on it. */
    fun sky(elapsedMs: Long): Float = ramp(elapsedMs, 0L, 500L)

    /**
     * One body of the chain, staggered left to right so the solar system
     * assembles rather than appearing all at once.
     */
    fun body(index: Int, elapsedMs: Long): Float {
        val start = BODIES_START + index * BODY_STAGGER
        return ramp(elapsedMs, start, start + BODY_FADE)
    }

    /** How much of the pebble's trail has been drawn in. */
    fun trail(elapsedMs: Long): Float = ramp(elapsedMs, 700L, 1_450L)

    fun pebble(elapsedMs: Long): Float = ramp(elapsedMs, 900L, 1_300L)

    /**
     * The targeting brackets, which fly in from outside the frame. 0 is wide
     * open, 1 is locked on - the same gesture the capture screen makes.
     */
    fun reticle(elapsedMs: Long): Float = ramp(elapsedMs, 1_150L, 1_650L)

    fun title(elapsedMs: Long): Float = ramp(elapsedMs, 1_350L, 2_050L)

    fun isComplete(elapsedMs: Long): Boolean = elapsedMs >= TOTAL_MS

    /** Eased, so nothing in the sequence arrives at a constant rate. */
    private fun ramp(elapsedMs: Long, from: Long, to: Long): Float {
        val t = ((elapsedMs - from).toFloat() / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    const val BODY_COUNT = 8
    private const val BODIES_START = 150L
    private const val BODY_STAGGER = 70L
    private const val BODY_FADE = 520L
}

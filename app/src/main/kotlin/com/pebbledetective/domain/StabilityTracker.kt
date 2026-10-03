package com.pebbledetective.domain

/**
 * Decides when the phone is being held still enough to take the shot.
 *
 * Pure logic over angular-rate samples, with no Android types, so it can be
 * unit tested on the JVM.
 *
 * Three things make this work in a child's hands rather than just in theory:
 *
 *  * The decision uses the **90th percentile** over a short window, not the
 *    mean. A mean happily hides a single jolt right at the shutter.
 *  * **Hysteresis** - a higher bar to become steady than to stop being steady -
 *    stops the reticle strobing between states.
 *  * **Relaxation**: the threshold loosens the longer the child has been
 *    trying, and after [forceAfterMs] it gives up and declares success. A
 *    stability gate that can never be satisfied is worse than no gate at all.
 */
class StabilityTracker(
    private val windowMs: Long = 500L,
    private val enterThreshold: Float = 0.20f,
    private val exitThreshold: Float = 0.40f,
    private val forceAfterMs: Long = 7_000L,
) {
    private val times = ArrayDeque<Long>()
    private val rates = ArrayDeque<Float>()
    private var steady = false

    /** Feed one angular-rate magnitude, in rad/s. */
    fun sample(timeMs: Long, radiansPerSecond: Float) {
        times.addLast(timeMs)
        rates.addLast(radiansPerSecond)
        while (times.isNotEmpty() && timeMs - times.first() > windowMs) {
            times.removeFirst()
            rates.removeFirst()
        }
    }

    /** True once the window has enough data to judge anything. */
    fun hasEnoughData(): Boolean = rates.size >= MIN_SAMPLES

    /**
     * The 90th-percentile angular rate over the window, or [Float.MAX_VALUE]
     * when there is not yet enough data to say.
     */
    fun percentile90(): Float {
        if (!hasEnoughData()) return Float.MAX_VALUE
        val sorted = rates.sorted()
        val index = ((sorted.size - 1) * 0.9f).toInt()
        return sorted[index]
    }

    /**
     * Whether the phone counts as steady.
     *
     * @param elapsedSinceArmedMs how long the child has been trying to hold
     *   still. Larger values loosen the threshold and eventually force success.
     */
    fun isSteady(elapsedSinceArmedMs: Long): Boolean {
        if (elapsedSinceArmedMs >= forceAfterMs) return true
        if (!hasEnoughData()) return steady

        val relaxation = relaxationFor(elapsedSinceArmedMs)
        val p90 = percentile90()
        steady = if (steady) {
            p90 <= exitThreshold * relaxation
        } else {
            p90 <= enterThreshold * relaxation
        }
        return steady
    }

    /**
     * How close the child is to holding still, from 0 to 1, for the reticle's
     * progress ring. A silent gate with no feedback is the worst outcome, so
     * this always has something to show.
     */
    fun progress(elapsedSinceArmedMs: Long): Float {
        if (elapsedSinceArmedMs >= forceAfterMs) return 1f
        if (!hasEnoughData()) return 0f
        val target = enterThreshold * relaxationFor(elapsedSinceArmedMs)
        val p90 = percentile90()
        if (p90 <= target) return 1f
        // Full ring at the threshold, empty at four times it.
        return ((4f * target - p90) / (3f * target)).coerceIn(0f, 1f)
    }

    /** Thresholds loosen in steps the longer this is taking. */
    private fun relaxationFor(elapsedMs: Long): Float = when {
        elapsedMs < 2_500L -> 1.0f
        elapsedMs < 5_000L -> 1.6f
        else -> 2.5f
    }

    fun reset() {
        times.clear()
        rates.clear()
        steady = false
    }

    private companion object {
        /** Roughly 100ms of samples at SENSOR_DELAY_GAME. */
        const val MIN_SAMPLES = 5
    }
}

package com.pebbledetective.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The clock the planetarium runs on.
 *
 * The planetarium shows the solar system at a moment that the child
 * chooses, so the moment is the state: an offset in days from the day they
 * opened the screen. Everything else - the slider, the date on the label,
 * the positions of nine bodies - is a function of it.
 *
 * Kept here, away from the drawing, because the awkward parts are all
 * arithmetic: that time runs at a rate rather than a step, that a dropped
 * frame must not slow the sky down, and that running off the end of the
 * range has to stop rather than wrap.
 */
object Orrery {

    /**
     * The speeds offered, in days of sky per second of wall clock.
     *
     * Chosen so that something is visibly happening at every one of them.
     * At one day a second the Moon goes round in under half a minute and
     * the planets hold still; at twenty, Mercury laps the Sun every four
     * seconds and Mars crawls. Faster than twenty and the inner planets
     * strobe rather than move.
     */
    val SPEEDS = listOf(0, 1, 5, 10, 20)

    /**
     * How far either side of today the scrubber reaches, in days.
     *
     * Ten years each way. The orbital elements behind this are the JPL
     * approximations for 1800 to 2050, so the limit is a choice about what
     * is interesting rather than about what is possible: twenty years is
     * most of a Jupiter orbit and two of Saturn's seasons, and at twenty
     * days a second it takes six minutes to cross.
     */
    const val RANGE_DAYS = 3_652.0

    const val MILLIS_PER_DAY = 86_400_000L

    /** Nothing outside the scrubber's reach. */
    fun clamp(offsetDays: Double): Double = offsetDays.coerceIn(-RANGE_DAYS, RANGE_DAYS)

    /** The moment an offset stands for. */
    fun epochAt(openedAtMillis: Long, offsetDays: Double): Long =
        openedAtMillis + (clamp(offsetDays) * MILLIS_PER_DAY).roundToLong()

    /**
     * One frame of running time.
     *
     * Takes the frame's own length rather than assuming sixtieths of a
     * second, so a stutter costs a longer step instead of a slower sky.
     */
    fun advance(
        offsetDays: Double,
        speedDaysPerSecond: Int,
        backwards: Boolean,
        frameSeconds: Double,
    ): Double {
        val direction = if (backwards) -1 else 1
        return clamp(offsetDays + speedDaysPerSecond * direction * frameSeconds)
    }

    /**
     * Whether time has run as far as it goes.
     *
     * Within a day of the end rather than exactly at it: the last frame
     * lands wherever it lands, and a sky that stops a few hours short is
     * indistinguishable from one that stops on the mark.
     */
    fun atLimit(offsetDays: Double): Boolean = abs(offsetDays) >= RANGE_DAYS - 1.0
}

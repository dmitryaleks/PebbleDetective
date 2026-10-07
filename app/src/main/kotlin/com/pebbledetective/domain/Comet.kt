package com.pebbledetective.domain

/**
 * The invented comets, for putting a meteor where a child can go and look.
 *
 * The sky mode can only offer what is actually up there, and on a given
 * evening that may be two planets, both behind a building. A grown-up who
 * wants the hunt to end in the park rather than in a car park needs to
 * choose the direction, and these are how: tap the control once and a
 * comet hangs due south, again and it moves to the south-west, again and
 * it is in the west, and so on round the compass. Tap the comet itself and
 * the meteor falls that way.
 *
 * They are not real comets and do not pretend to be. Nothing in the app
 * claims a stone came from one; what a comet decides is *where* a stone
 * will be, which is the one thing the real sky cannot be asked for.
 *
 * Eight of them, because eight is the number of points on a compass rose a
 * child can be sent towards without a map.
 */
enum class Comet(
    /** What the grown-up is counting: the first tap gives number one. */
    val number: Int,
    /** Where it hangs, clockwise from due south. */
    val azimuthDegrees: Double,
    /**
     * How far above the horizon.
     *
     * All of them high: the point of a comet is to be findable over the
     * roofs and the trees from wherever you happen to be standing. Varied
     * a little from one to the next so that two in a row do not look like
     * the same light moved sideways.
     */
    val altitudeDegrees: Double,
) {
    FIRST(1, 180.0, 62.0),
    SECOND(2, 225.0, 57.0),
    THIRD(3, 270.0, 66.0),
    FOURTH(4, 315.0, 53.0),
    FIFTH(5, 0.0, 69.0),
    SIXTH(6, 45.0, 58.0),
    SEVENTH(7, 90.0, 64.0),
    EIGHTH(8, 135.0, 51.0);

    companion object {
        /** How many there are, which is also how many taps go round. */
        const val COUNT = 8

        fun ofNumber(number: Int): Comet? = entries.firstOrNull { it.number == number }

        /**
         * One tap: nothing becomes the first, the eighth becomes nothing.
         *
         * One at a time, deliberately. Eight comets at once is a light
         * show; one that the grown-up walks round the compass is a way of
         * saying "go and look over there".
         */
        fun next(current: Comet?): Comet? = when (current) {
            null -> FIRST
            EIGHTH -> null
            else -> ofNumber(current.number + 1)
        }
    }
}

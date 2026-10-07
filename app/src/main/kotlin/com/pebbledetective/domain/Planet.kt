package com.pebbledetective.domain

/**
 * The bodies a pebble can be found to have come from.
 *
 * Earth is present because it is the *destination* of the journey, but it is
 * never a source - a pebble that flew from Earth to Earth is not a story.
 * [sources] is the set the picker draws from.
 *
 * The Moon is a source like any other. It is the one of these a child has
 * actually seen with their own eyes, and lunar meteorites are real: a few
 * hundred of them have been picked up on Earth.
 */
enum class Planet(val id: String) {
    SUN("sun"),
    MERCURY("mercury"),
    VENUS("venus"),
    EARTH("earth"),
    MOON("moon"),
    MARS("mars"),
    JUPITER("jupiter"),
    SATURN("saturn"),
    URANUS("uranus"),
    NEPTUNE("neptune"),

    /**
     * Halley's Comet, which is here because of the comets in the sky mode.
     *
     * The one body on this list a colour can never choose. A stone is
     * only ever from Halley because a grown-up called a comet down and
     * the child watched it fall, which is why it is kept out of
     * [sources] - the bag the random pick draws from.
     *
     * Halley of all of them because it is the comet everyone has heard
     * of, and because the attribution is for once not a fiction at all:
     * the Earth passes through its dust twice a year, and the Orionids
     * in October and the Eta Aquariids in May are both Halley burning
     * up in the air.
     */
    HALLEY("halley");

    /** Photograph bundled in assets. See ASSETS.md for provenance. */
    val assetPath: String get() = "planets/$id.jpg"

    companion object {
        /**
         * Everything a *colour* can arrive from.
         *
         * All of them bar Earth, which is the destination, and bar
         * Halley, which is never guessed - only claimed.
         */
        val sources: List<Planet> = entries.filter { it != EARTH && it != HALLEY }

        fun fromId(id: String?): Planet? = entries.firstOrNull { it.id == id }
    }
}

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
    NEPTUNE("neptune");

    /** Photograph bundled in assets. See ASSETS.md for provenance. */
    val assetPath: String get() = "planets/$id.jpg"

    companion object {
        /** Everything a pebble can arrive from, i.e. all of them bar Earth. */
        val sources: List<Planet> = entries.filter { it != EARTH }

        fun fromId(id: String?): Planet? = entries.firstOrNull { it.id == id }
    }
}

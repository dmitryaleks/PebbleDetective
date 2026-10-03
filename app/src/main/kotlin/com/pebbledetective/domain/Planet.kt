package com.pebbledetective.domain

/**
 * The bodies a pebble can be found to have come from.
 *
 * Earth is present because it is the *destination* of the journey, but it is
 * never a source - a pebble that flew from Earth to Earth is not a story.
 * [sources] is the set the picker draws from.
 */
enum class Planet(val id: String) {
    SUN("sun"),
    MERCURY("mercury"),
    VENUS("venus"),
    EARTH("earth"),
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

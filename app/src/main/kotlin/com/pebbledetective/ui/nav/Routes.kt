package com.pebbledetective.ui.nav

import com.pebbledetective.domain.Planet

/** Navigation destinations. The flow screens are driven by session state; these are the shells. */
object Routes {
    const val SPLASH = "splash"
    const val CAPTURE = "capture"
    const val RESEARCH = "research"
    const val RESULT = "result"
    const val JOURNEY = "journey"
    const val HISTORY = "history"
    const val RADAR = "radar"
    const val SKY = "sky"
    const val CREDITS = "credits"
    const val HISTORY_DETAIL = "history/{id}"

    /**
     * The planetarium, optionally opened on one world.
     *
     * The focus is part of the route rather than session state because it
     * is a property of *this* visit: going back to the logbook and opening
     * a different stone should aim the model somewhere else, and the back
     * stack remembers which was which.
     */
    const val PLANETARIUM = "planetarium?planet={planet}"

    fun historyDetail(id: String) = "history/$id"

    fun planetarium(planet: Planet? = null) =
        "planetarium?planet=" + (planet?.id.orEmpty())
}

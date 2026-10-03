package com.pebbledetective.ui.nav

/** Navigation destinations. The flow screens are driven by session state; these are the shells. */
object Routes {
    const val CAPTURE = "capture"
    const val RESEARCH = "research"
    const val RESULT = "result"
    const val JOURNEY = "journey"
    const val HISTORY = "history"
    const val RADAR = "radar"
    const val CREDITS = "credits"
    const val HISTORY_DETAIL = "history/{id}"

    fun historyDetail(id: String) = "history/$id"
}

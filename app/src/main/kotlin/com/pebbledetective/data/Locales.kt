package com.pebbledetective.data

import java.util.Locale

/** The [Locale] for this language, for date and number formatting. */
val AppLanguage.locale: Locale
    get() = Locale.forLanguageTag(tag)

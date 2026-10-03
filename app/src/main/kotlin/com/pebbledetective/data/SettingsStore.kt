package com.pebbledetective.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** The three languages the app ships with. */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    RUSSIAN("ru"),
    JAPANESE("ja");

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        /** Best match for the device's own language, falling back to English. */
        fun systemDefault(): AppLanguage =
            fromTag(Locale.getDefault().language) ?: ENGLISH
    }
}

/**
 * Sound and language preferences.
 *
 * Sound is off by default, and that default is load-bearing: the app must be
 * silent until a child deliberately turns it on.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pebble_settings", Context.MODE_PRIVATE)

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND, false))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _language = MutableStateFlow(
        AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null)) ?: AppLanguage.systemDefault()
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SOUND, enabled) }
        _soundEnabled.value = enabled
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit { putString(KEY_LANGUAGE, language.tag) }
        _language.value = language
    }

    /** Advances to the next language, for the single-tap globe button. */
    fun cycleLanguage() {
        val all = AppLanguage.entries
        setLanguage(all[(all.indexOf(_language.value) + 1) % all.size])
    }

    private companion object {
        const val KEY_SOUND = "sound_enabled"
        const val KEY_LANGUAGE = "language_tag"
    }
}

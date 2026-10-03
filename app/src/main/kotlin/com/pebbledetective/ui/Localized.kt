package com.pebbledetective.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.pebbledetective.data.AppLanguage

/**
 * Applies [language] to everything inside [content] without recreating the Activity.
 *
 * The usual route, AppCompatDelegate.setApplicationLocales, restarts the Activity.
 * That would unbind the camera and tear down any animation in flight - unacceptable
 * when the spec requires switching language at *any* stage, including mid-journey.
 * Overriding the composition locals instead keeps the camera bound and coroutines
 * running; only the text re-reads.
 *
 * Note that Locale.getDefault() is deliberately left alone, so anything formatting
 * a date must be handed the locale explicitly - see [AppLanguage.locale].
 */
@Composable
fun Localized(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current

    val configuration = remember(language, context) {
        Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(language.tag))
        }
    }
    val localizedContext = remember(configuration) {
        context.createConfigurationContext(configuration)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        // Which of these stringResource reads has moved between Compose
        // versions, so both are overridden.
        LocalResources provides localizedContext.resources,
        LocalConfiguration provides configuration,
        content = content,
    )
}

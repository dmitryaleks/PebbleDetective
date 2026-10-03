package com.pebbledetective.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
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
 * The usual route, AppCompatDelegate.setApplicationLocales, restarts the
 * Activity. That would unbind the camera and tear down any animation in
 * flight - unacceptable when the spec requires switching language at *any*
 * stage, including mid-journey. Overriding the composition locals instead
 * keeps the camera bound and coroutines running; only the text re-reads.
 *
 * Note that Locale.getDefault() is deliberately left alone, so anything
 * formatting a date must be handed the locale explicitly - see
 * [com.pebbledetective.data.locale].
 */
@Composable
fun Localized(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Taken from LocalConfiguration rather than LocalContext.resources: the
    // latter is not configuration-aware and can hand back a stale value.
    val base = LocalConfiguration.current

    val configuration = remember(language, base) {
        Configuration(base).apply {
            setLocales(LocaleList.forLanguageTags(language.tag))
        }
    }
    val localizedContext = remember(configuration, context) {
        LocalizedContext(context, configuration)
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

/**
 * A context that reports localised resources while staying in the Activity's
 * ContextWrapper chain.
 *
 * The obvious implementation - handing `createConfigurationContext()` straight
 * to `LocalContext` - crashes the app. That call returns a context detached
 * from the Activity, and anything that recovers the Activity by walking the
 * wrapper chain then fails: `rememberLauncherForActivityResult` dies with
 * "No ActivityResultRegistryOwner was provided", which takes out the camera
 * permission request and the photo picker. Wrapping the real context and
 * overriding only the resource accessors keeps that chain intact.
 */
private class LocalizedContext(
    base: Context,
    configuration: Configuration,
) : ContextWrapper(base) {

    private val localizedResources: Resources =
        base.createConfigurationContext(configuration).resources

    override fun getResources(): Resources = localizedResources

    override fun getAssets(): AssetManager = localizedResources.assets

    override fun getTheme(): Resources.Theme =
        localizedResources.newTheme().apply { setTo(super.getTheme()) }
}

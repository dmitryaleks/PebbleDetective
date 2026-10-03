package com.pebbledetective.ui.common

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether this device has animations turned off.
 *
 * Both timed sequences run off a wall clock rather than Compose animations,
 * which is what keeps them immune to the duration scale collapsing them to
 * nothing. The flip side is that someone who has deliberately switched
 * animations off - for motion sensitivity, or on a low-end device - would
 * otherwise be made to sit through all seventeen seconds of them anyway.
 *
 * So the setting is read explicitly and the sequences skip themselves.
 */
@Composable
fun animationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.animatorDurationScale() == 0f }
}

private fun Context.animatorDurationScale(): Float = runCatching {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}.getOrDefault(1f)

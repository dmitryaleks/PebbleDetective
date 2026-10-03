package com.pebbledetective.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SpaceBlack = Color(0xFF05070D)
val DeepNavy = Color(0xFF0B1022)
val ScannerGreen = Color(0xFF4DFFA6)
val SignalAmber = Color(0xFFFFC65C)
val PebbleGrey = Color(0xFF8A93A8)

private val PebbleColors = darkColorScheme(
    primary = ScannerGreen,
    onPrimary = SpaceBlack,
    secondary = SignalAmber,
    onSecondary = SpaceBlack,
    background = SpaceBlack,
    onBackground = Color(0xFFE6ECF5),
    surface = DeepNavy,
    onSurface = Color(0xFFE6ECF5),
)

/** The app is always dark: it is a night-sky / scanner aesthetic. */
@Composable
fun PebbleTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = PebbleColors, content = content)
}

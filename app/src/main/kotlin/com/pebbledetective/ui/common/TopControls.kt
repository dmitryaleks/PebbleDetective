package com.pebbledetective.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pebbledetective.R
import com.pebbledetective.data.AppLanguage

/** Which screen the toolbar is sitting on, so its own button can say so. */
enum class TopMode { SKY, PLANETARIUM, RADAR, DETECTION, HISTORY }

/**
 * Every mode, as one parameter.
 *
 * The screens that are not modes themselves - the result, the logbook,
 * the credits - have no opinion about any of these and would otherwise
 * each carry five identical lambdas down from the navigation graph.
 */
data class ModeLinks(
    val sky: () -> Unit,
    val planetarium: () -> Unit,
    val radar: () -> Unit,
    val detection: () -> Unit,
    val history: () -> Unit,
)

/** The whole toolbar, for a screen with nothing special to say about it. */
@Composable
fun TopControls(
    language: AppLanguage,
    soundEnabled: Boolean,
    onCycleLanguage: () -> Unit,
    onToggleSound: () -> Unit,
    modes: ModeLinks,
    modifier: Modifier = Modifier,
    current: TopMode? = null,
) {
    TopControls(
        language = language,
        soundEnabled = soundEnabled,
        onCycleLanguage = onCycleLanguage,
        onToggleSound = onToggleSound,
        onOpenHistory = modes.history,
        modifier = modifier,
        onSky = modes.sky,
        onPlanetarium = modes.planetarium,
        onRadar = modes.radar,
        onDetection = modes.detection,
        current = current,
    )
}

/**
 * Language, sound, and a way into every mode the app has.
 *
 * On *every* screen, which the spec asks for in the case of the language
 * and which turned out to matter just as much for the modes: the four
 * mode screens are not a sequence, they are four ways of looking at the
 * same hunt, and a child who is in the logbook should not have to work
 * out which way is back to the sky.
 *
 * The row sizes itself. Seven controls at the old fifty-two density
 * independent pixels came to four hundred, which is wider than a small
 * phone, and a toolbar that runs off the edge loses whichever button is
 * last - the logbook, as it happens. Each control now takes an equal
 * share of whatever width there is, between forty and fifty-two.
 */
@Composable
fun TopControls(
    language: AppLanguage,
    soundEnabled: Boolean,
    onCycleLanguage: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenHistory: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onSky: (() -> Unit)? = null,
    onPlanetarium: (() -> Unit)? = null,
    onRadar: (() -> Unit)? = null,
    onDetection: (() -> Unit)? = null,
    /** The screen this toolbar is on; its button is tinted and ringed. */
    current: TopMode? = null,
) {
    BoxWithConstraints(
        // statusBarsPadding is load-bearing: the app draws edge to edge, and
        // without it these controls sit under the system status bar, where it
        // swallows their taps.
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val count = 2 + listOfNotNull(onSky, onPlanetarium, onRadar, onDetection, onOpenHistory).size
        val target = (maxWidth / count).coerceIn(MIN_TARGET, MAX_TARGET)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageButton(language = language, size = target, onClick = onCycleLanguage)

            ToolButton(
                icon = if (soundEnabled) {
                    Icons.AutoMirrored.Filled.VolumeUp
                } else {
                    Icons.AutoMirrored.Filled.VolumeOff
                },
                description = stringResource(
                    if (soundEnabled) R.string.cd_sound_on else R.string.cd_sound_off
                ),
                size = target,
                onClick = onToggleSound,
            )

            if (onSky != null) {
                ToolButton(
                    icon = Icons.Filled.Public,
                    description = stringResource(
                        if (current == TopMode.SKY) R.string.cd_sky_all else R.string.cd_sky
                    ),
                    size = target,
                    active = current == TopMode.SKY,
                    onClick = onSky,
                )
            }
            if (onPlanetarium != null) {
                ToolButton(
                    icon = Icons.Filled.WbSunny,
                    description = stringResource(R.string.cd_planetarium),
                    size = target,
                    active = current == TopMode.PLANETARIUM,
                    onClick = onPlanetarium,
                )
            }
            if (onRadar != null) {
                ToolButton(
                    icon = Icons.Filled.Radar,
                    description = stringResource(
                        if (current == TopMode.RADAR) R.string.cd_radar_again else R.string.cd_radar
                    ),
                    size = target,
                    active = current == TopMode.RADAR,
                    onClick = onRadar,
                )
            }
            if (onDetection != null) {
                ToolButton(
                    icon = Icons.Filled.CameraAlt,
                    description = stringResource(R.string.cd_detection),
                    size = target,
                    active = current == TopMode.DETECTION,
                    onClick = onDetection,
                )
            }
            if (onOpenHistory != null) {
                ToolButton(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    description = stringResource(R.string.cd_history),
                    size = target,
                    active = current == TopMode.HISTORY,
                    onClick = onOpenHistory,
                )
            }
        }
    }
}

/**
 * The language, as one control rather than two.
 *
 * It used to be a globe button with the code printed beside it, which
 * cost a whole slot in a row that has run out of them. The code now sits
 * under the globe inside the button, which keeps both the "this changes
 * the language" affordance and the answer to "which one am I in".
 */
@Composable
private fun LanguageButton(language: AppLanguage, size: Dp, onClick: () -> Unit) {
    val description = stringResource(R.string.cd_language)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // The globe sits on the same line as every other icon in the row
        // and the code hangs below it. Stacked in a column instead, the
        // pair centres as a block and the globe rides visibly high.
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            modifier = Modifier.size(size * 0.46f),
        )
        Text(
            text = language.tag.uppercase(),
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * One round target with an icon in it.
 *
 * Not an `IconButton`: Material enforces a forty-eight point minimum on
 * those whatever size they are given, and seven of those will not fit
 * across a small phone.
 */
@Composable
private fun ToolButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (active) {
                    Modifier.border(
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        CircleShape,
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/**
 * Generous for a child's finger where there is room, and never below the
 * point where one button becomes two.
 */
private val MIN_TARGET = 40.dp
private val MAX_TARGET = 52.dp

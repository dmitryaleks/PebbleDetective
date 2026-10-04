package com.pebbledetective.ui.result

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.pebbledetective.R
import com.pebbledetective.domain.Planet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The localised name of each body. */
@get:StringRes
val Planet.nameRes: Int
    get() = when (this) {
        Planet.SUN -> R.string.planet_sun_name
        Planet.MERCURY -> R.string.planet_mercury_name
        Planet.VENUS -> R.string.planet_venus_name
        Planet.EARTH -> R.string.planet_earth_name
        Planet.MOON -> R.string.planet_moon_name
        Planet.MARS -> R.string.planet_mars_name
        Planet.JUPITER -> R.string.planet_jupiter_name
        Planet.SATURN -> R.string.planet_saturn_name
        Planet.URANUS -> R.string.planet_uranus_name
        Planet.NEPTUNE -> R.string.planet_neptune_name
    }

/**
 * The name in the form that follows "leaving", which is not always the name.
 *
 * Russian inflects: a pebble leaves с Марса, not с Марс, and the ending
 * differs by word - Венеры, Юпитера, Солнца. There is no rule a
 * format string can apply, so the genitive is a resource of its own and
 * the languages that do not inflect simply repeat the plain name.
 */
@get:StringRes
val Planet.fromNameRes: Int
    get() = when (this) {
        Planet.SUN -> R.string.planet_sun_from
        Planet.MERCURY -> R.string.planet_mercury_from
        Planet.VENUS -> R.string.planet_venus_from
        Planet.EARTH -> R.string.planet_earth_from
        Planet.MOON -> R.string.planet_moon_from
        Planet.MARS -> R.string.planet_mars_from
        Planet.JUPITER -> R.string.planet_jupiter_from
        Planet.SATURN -> R.string.planet_saturn_from
        Planet.URANUS -> R.string.planet_uranus_from
        Planet.NEPTUNE -> R.string.planet_neptune_from
    }

/** One kid-sized fact per body. */
@get:StringRes
val Planet.factRes: Int
    get() = when (this) {
        Planet.SUN -> R.string.planet_sun_fact
        Planet.MERCURY -> R.string.planet_mercury_fact
        Planet.VENUS -> R.string.planet_venus_fact
        Planet.EARTH -> R.string.planet_earth_fact
        Planet.MOON -> R.string.planet_moon_fact
        Planet.MARS -> R.string.planet_mars_fact
        Planet.JUPITER -> R.string.planet_jupiter_fact
        Planet.SATURN -> R.string.planet_saturn_fact
        Planet.URANUS -> R.string.planet_uranus_fact
        Planet.NEPTUNE -> R.string.planet_neptune_fact
    }

/**
 * Decodes a bundled NASA photograph off the main thread.
 *
 * These are ~1280px JPEGs in assets; decoding one synchronously during
 * composition would drop frames on the way into the screen.
 */
@Composable
fun rememberPlanetImage(planet: Planet): State<ImageBitmap?> {
    val context = LocalContext.current
    val image = remember(planet) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(planet) {
        image.value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(planet.assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return image
}

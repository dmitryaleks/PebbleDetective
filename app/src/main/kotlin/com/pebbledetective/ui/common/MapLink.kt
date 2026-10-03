package com.pebbledetective.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pebbledetective.R
import java.util.Locale

/**
 * The place a pebble was found, as a tap target that opens a map.
 *
 * Note this is the one place the app reaches outside itself. It holds no
 * network permission and makes no requests; it hands the coordinates to
 * whatever maps app the device has, and that app does the talking. Worth
 * knowing, since the coordinates are where a child was standing.
 */
@Composable
fun MapLink(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val label = stringResource(R.string.map_open)
    val coordinates = formatCoordinates(latitude, longitude)

    Row(
        modifier = modifier
            // Comfortably past the 48dp minimum, since the users are children.
            .heightIn(min = 48.dp)
            .clickable { context.openInMaps(latitude, longitude) }
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = "$label, $coordinates" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = coordinates,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/** Always dot-decimal: a geo: URI is not localised, whatever the UI language. */
fun formatCoordinates(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

/**
 * Opens the coordinates in a map.
 *
 * Tries the `geo:` scheme first, which any installed maps app will take, and
 * falls back to a Google Maps web link when the device has none - a bare
 * phone with only a browser still gets somewhere useful.
 */
fun Context.openInMaps(latitude: Double, longitude: Double) {
    val point = String.format(Locale.US, "%f,%f", latitude, longitude)
    val label = Uri.encode(getString(R.string.map_pin_label))

    val geo = Intent(Intent.ACTION_VIEW, "geo:$point?q=$point($label)".toUri())
    val web = Intent(
        Intent.ACTION_VIEW,
        "https://www.google.com/maps/search/?api=1&query=$point".toUri(),
    )

    for (intent in listOf(geo, web)) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next one; a device with neither is possible but rare.
        }
    }
}

private fun String.toUri(): Uri = Uri.parse(this)

package com.pebbledetective.ui.research

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.pebbledetective.ui.theme.ScannerGreen
import kotlin.random.Random

private const val GLYPHS = "01アイウエオカキクケコサシスセソタチツテトナニヌネノ#@%&*+=<>"

/** One falling column of glyphs. */
private class Column(
    var x: Float,
    var head: Float,
    var speed: Float,
    val length: Int,
    val chars: CharArray,
)

/**
 * The "thinking mode" glyph rain.
 *
 * Driven by [elapsedMs] from the session's wall clock rather than its own
 * animation, so a language switch mid-research resumes at the same frame.
 *
 * Deliberately gentle: columns fade rather than flash, and nothing changes
 * the whole screen's brightness quickly. Rapid full-area flashing is a real
 * seizure risk, and this is a children's app.
 */
@Composable
fun MatrixRain(
    elapsedMs: Long,
    modifier: Modifier = Modifier,
    columnWidthPx: Float = 38f,
) {
    val measurer = rememberTextMeasurer()
    val random = remember { Random(0x9E3779B9) }
    val columns = remember { mutableListOf<Column>() }

    Canvas(modifier = modifier.fillMaxSize()) {
        ensureColumns(columns, random, size.width, size.height, columnWidthPx)
        drawRain(columns, elapsedMs, size.height, measurer)
    }
}

private fun ensureColumns(
    columns: MutableList<Column>,
    random: Random,
    width: Float,
    height: Float,
    columnWidth: Float,
) {
    val wanted = (width / columnWidth).toInt().coerceAtLeast(1)
    if (columns.size == wanted) return
    columns.clear()
    repeat(wanted) { index ->
        val length = random.nextInt(6, 18)
        columns += Column(
            x = index * columnWidth + columnWidth / 2f,
            head = random.nextFloat() * height,
            speed = random.nextFloat() * 220f + 120f,
            length = length,
            chars = CharArray(length) { GLYPHS[random.nextInt(GLYPHS.length)] },
        )
    }
}

private fun DrawScope.drawRain(
    columns: List<Column>,
    elapsedMs: Long,
    height: Float,
    measurer: TextMeasurer,
) {
    val seconds = elapsedMs / 1000f
    val cell = 26f
    val style = TextStyle(fontSize = 15.sp, color = ScannerGreen)

    for (column in columns) {
        // Position is a function of elapsed time, so there is no per-frame
        // state to lose when the composition restarts.
        val y = (column.head + column.speed * seconds) % (height + column.length * cell)
        for (i in 0 until column.length) {
            val charY = y - i * cell
            if (charY < -cell || charY > height) continue
            val headFade = 1f - i.toFloat() / column.length
            drawText(
                textMeasurer = measurer,
                text = column.chars[(i + (seconds * 6).toInt()) % column.length].toString(),
                topLeft = Offset(column.x, charY),
                style = style.copy(
                    color = ScannerGreen.copy(alpha = (headFade * 0.85f).coerceIn(0.05f, 0.9f)),
                ),
            )
        }
    }
}

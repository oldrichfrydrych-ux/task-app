package cz.olda.tasks.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private val hexPattern = Regex("^#?([0-9a-fA-F]{6})$")

/** Parses `#RRGGBB` or `RRGGBB` (either case); returns null for anything else. */
fun parseHex(input: String): Color? {
    val digits = hexPattern.matchEntire(input)?.groupValues?.get(1) ?: return null
    return Color(0xFF000000L or digits.toLong(16))
}

/** Always uppercase `#RRGGBB`; alpha is dropped. */
fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

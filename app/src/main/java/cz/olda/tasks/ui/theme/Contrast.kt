package cz.olda.tasks.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The WCAG contrast ratio between two opaque colours, from 1f (identical) to 21f
 * (black on white). Body text needs at least 4.5f, large text and icons 3f.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val first = a.luminance()
    val second = b.luminance()
    val lighter = maxOf(first, second)
    val darker = minOf(first, second)
    return (lighter + 0.05f) / (darker + 0.05f)
}

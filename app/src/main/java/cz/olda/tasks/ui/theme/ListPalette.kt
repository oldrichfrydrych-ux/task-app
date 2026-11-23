package cz.olda.tasks.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One preset list colour: the light-mode value and its dark-mode shade.
 *
 * [name] is an English identifier, not text for the screen. The colour picker in
 * Phase 1 shows translated names from `strings.xml`.
 */
data class ListColor(val name: String, val light: Color, val dark: Color)

/**
 * The 12 preset colours a list or tag can take, in the order of docs/design.md,
 * section Look. Any of them can be swapped for a custom hex colour in Phase 1.
 */
val ListPalette: List<ListColor> = listOf(
    ListColor("Blue", Color(0xFF3D6BE0), Color(0xFF7EA2FF)),
    ListColor("Orange", Color(0xFFE07B39), Color(0xFFFF9F5A)),
    ListColor("Green", Color(0xFF23865A), Color(0xFF5ED29A)),
    ListColor("Purple", Color(0xFF7C4FE0), Color(0xFFB794FF)),
    ListColor("Teal", Color(0xFF157D89), Color(0xFF5CC8D2)),
    ListColor("Red", Color(0xFFD43D3D), Color(0xFFFF7B7B)),
    ListColor("Pink", Color(0xFFC2418A), Color(0xFFF28BC4)),
    ListColor("Amber", Color(0xFFB7791F), Color(0xFFF2C155)),
    ListColor("Olive", Color(0xFF6E7F1E), Color(0xFFB7CC5A)),
    ListColor("Brown", Color(0xFF8A5A3C), Color(0xFFD1A07E)),
    ListColor("Slate", Color(0xFF5B6575), Color(0xFFA9B3C2)),
    ListColor("Cyan", Color(0xFF1E88B8), Color(0xFF6EC6EE)),
)

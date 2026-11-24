package cz.olda.tasks.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Body text needs this much contrast against its background (WCAG AA). */
const val MinTextContrast = 4.5f

/** The lightest and darkest card text in a list colour is drawn on. Used to judge the shades. */
internal val TextBackgroundLight = WarmBackgroundLight
internal val TextBackgroundDark = WarmSurfaceDark

private const val MaxShift = 0.5f
private const val Step = 0.05f

/**
 * The colour to draw text in for a list colour. A palette or custom colour is the list's identity
 * (dots, bars, swatches); text in it uses a shade that keeps 4.5:1 contrast (docs/design.md, Look).
 *
 * Light mode: the colour is mixed towards black in 5% steps, up to 50%, until it clears 4.5:1
 * against [TextBackgroundLight]. Dark mode: a preset uses its own `dark` value; any other colour
 * is mixed towards white the same way against [TextBackgroundDark]. If even the limit does not
 * clear 4.5:1, the limit is returned and [isReadableAsText] says false: the colour is unusable as text.
 */
fun textShade(color: Color, darkTheme: Boolean): Color {
    if (!darkTheme) return shift(color, Color.Black, TextBackgroundLight)
    ListPalette.firstOrNull { it.light.toHex() == color.toHex() }?.let { return it.dark }
    return shift(color, Color.White, TextBackgroundDark)
}

/** True when [textShade] of [color] reaches 4.5:1 on the background it is drawn on. */
fun isReadableAsText(color: Color, darkTheme: Boolean): Boolean =
    contrastRatio(textShade(color, darkTheme), if (darkTheme) TextBackgroundDark else TextBackgroundLight) >= MinTextContrast

private fun shift(color: Color, towards: Color, background: Color): Color {
    var amount = 0f
    while (amount < MaxShift && contrastRatio(lerp(color, towards, amount), background) < MinTextContrast) {
        amount += Step
    }
    return lerp(color, towards, amount.coerceAtMost(MaxShift))
}

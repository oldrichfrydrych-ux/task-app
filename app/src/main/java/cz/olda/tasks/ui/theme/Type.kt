package cz.olda.tasks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import cz.olda.tasks.R

/**
 * Manrope, the typeface from docs/design.md, section Look. Google Fonts ships it as a
 * single variable font, so one file carries every weight the app uses (500 to 800).
 * The licence is in `app/licenses/manrope-OFL.txt`.
 */
@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int, fontWeight: FontWeight) = Font(
    resId = R.font.manrope_variable,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(
    manrope(500, FontWeight.Medium),
    manrope(600, FontWeight.SemiBold),
    manrope(700, FontWeight.Bold),
    manrope(800, FontWeight.ExtraBold),
)

private val default = Typography()

/** Every style is Manrope, so Czech letters never fall back to another font. */
val Typography = Typography(
    displayLarge = default.displayLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold),
    displayMedium = default.displayMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold),
    displaySmall = default.displaySmall.copy(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold),
    headlineLarge = default.headlineLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    headlineMedium = default.headlineMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    headlineSmall = default.headlineSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleLarge = default.titleLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleMedium = default.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    titleSmall = default.titleSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    bodyLarge = default.bodyLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    bodyMedium = default.bodyMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    bodySmall = default.bodySmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    labelLarge = default.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    labelMedium = default.labelMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    labelSmall = default.labelSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
)

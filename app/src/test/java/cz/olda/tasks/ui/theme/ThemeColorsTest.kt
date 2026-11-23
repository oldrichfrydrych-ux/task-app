package cz.olda.tasks.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorsTest {

    @Test
    fun listPaletteHasTwelveDistinctColours() {
        assertEquals(12, ListPalette.size)
        assertEquals(12, ListPalette.map { it.light }.toSet().size)
        assertEquals(12, ListPalette.map { it.dark }.toSet().size)
        assertEquals(Color(0xFF3D6BE0), ListPalette.first().light)
    }

    @Test
    fun contrastOfBlackOnWhiteIs21() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
    }

    @Test
    fun lightTextIsReadable() {
        assertTrue(contrastRatio(LightColorScheme.onBackground, LightColorScheme.background) >= 4.5f)
        assertTrue(contrastRatio(LightColorScheme.onSurface, LightColorScheme.surface) >= 4.5f)
    }

    /**
     * Secondary text is a real text role, so it has to clear 4.5:1 on every surface the
     * app can put it on, not just on the background.
     */
    @Test
    fun secondaryTextIsReadable() {
        assertTrue(contrastRatio(LightColorScheme.onSurfaceVariant, LightColorScheme.surfaceVariant) >= 4.5f)
        assertTrue(contrastRatio(LightColorScheme.onSurfaceVariant, LightColorScheme.background) >= 4.5f)
        assertTrue(contrastRatio(DarkColorScheme.onSurfaceVariant, DarkColorScheme.surfaceVariant) >= 4.5f)
        assertTrue(contrastRatio(DarkColorScheme.onSurfaceVariant, DarkColorScheme.background) >= 4.5f)
    }

    @Test
    fun darkTextIsReadable() {
        assertTrue(contrastRatio(DarkColorScheme.onBackground, DarkColorScheme.background) >= 4.5f)
        assertTrue(contrastRatio(DarkColorScheme.onSurface, DarkColorScheme.surface) >= 4.5f)
    }
}

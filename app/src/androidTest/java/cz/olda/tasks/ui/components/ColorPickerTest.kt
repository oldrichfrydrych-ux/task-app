package cz.olda.tasks.ui.components

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import cz.olda.tasks.ui.theme.AppTheme
import cz.olda.tasks.ui.theme.ListPalette
import cz.olda.tasks.ui.theme.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ColorPickerTest {
    @get:Rule
    val rule = createComposeRule()

    private val picked = mutableListOf<String>()

    private fun show(selected: String = "#3D6BE0") {
        picked.clear()
        rule.setContent { AppTheme { ColorPicker(selected = selected, onPick = { picked += it }) } }
        rule.waitUntil(10_000) { rule.onAllNodes(isSwatch).fetchSemanticsNodes().isNotEmpty() }
    }

    private val isSwatch = SemanticsMatcher("is a colour swatch") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("swatch-") == true
    }

    private fun typeHex(text: String) {
        rule.onNodeWithTag("hex-field").performTextClearance()
        if (text.isNotEmpty()) rule.onNodeWithTag("hex-field").performTextInput(text)
    }

    private fun exists(tag: String) =
        rule.onAllNodes(useUnmergedTree = true, matcher = SemanticsMatcher("tag $tag") {
            it.config.getOrNull(SemanticsProperties.TestTag) == tag
        }).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun showsTwelvePresets() {
        show()
        assertEquals(12, rule.onAllNodes(isSwatch).fetchSemanticsNodes().size)
    }

    @Test
    fun pickingAPresetReportsItsHex() {
        show()
        val orange = ListPalette[1].light.toHex()
        rule.onNodeWithTag("swatch-$orange").performClick()
        assertEquals(listOf(orange), picked)
    }

    @Test
    fun badHexShowsAnErrorAndSavesNothing() {
        show()
        for (bad in listOf("#GGGGGG", "fff", "")) {
            typeHex(bad)
            rule.waitUntil(10_000) { exists("hex-error") }
            rule.onNodeWithTag("hex-error", useUnmergedTree = true).assertIsDisplayed()
                .assertTextEquals("Enter a colour like #3D6BE0")
        }
        assertTrue("nothing may be reported for a bad hex, got $picked", picked.isEmpty())
        // and a good one afterwards clears the error and is reported
        typeHex("#112233")
        rule.waitUntil(10_000) { !exists("hex-error") }
        assertEquals(listOf("#112233"), picked.distinct())
    }

    @Test
    fun lowContrastWarnsButStillSaves() {
        show()
        typeHex("#FFFF00")
        rule.waitUntil(10_000) { exists("contrast-warning") }
        rule.onNodeWithTag("contrast-warning").assertIsDisplayed()
        assertEquals("#FFFF00", picked.last())
        assertTrue(!exists("hex-error"))
    }

    @Test
    fun aPresetShowsNoWarning() {
        show("#3D6BE0")
        rule.onNodeWithTag("hex-field").assertIsDisplayed()
        assertTrue(!exists("contrast-warning"))
    }
}

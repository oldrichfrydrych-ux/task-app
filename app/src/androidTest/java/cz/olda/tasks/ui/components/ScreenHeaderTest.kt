package cz.olda.tasks.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import cz.olda.tasks.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScreenHeaderTest {
    @get:Rule
    val rule = createComposeRule()

    @Test fun showsTheTitleWithNoBackArrowOrActionByDefault() {
        rule.setContent { AppTheme { ScreenHeader(title = "Inbox", titleModifier = Modifier.testTag("t")) } }
        rule.onNodeWithTag("t").assertTextEquals("Inbox")
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test fun backArrowAppearsAndReportsTheTap() {
        var backs = 0
        rule.setContent {
            AppTheme { ScreenHeader(title = "Work", onBack = { backs++ }, backModifier = Modifier.testTag("b")) }
        }
        rule.onNodeWithTag("b").assertIsDisplayed()
        assertEquals(0, backs)
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test fun trailingActionIsShown() {
        rule.setContent {
            AppTheme { ScreenHeader(title = "Lists", trailing = { Text("Act", Modifier.testTag("act")) }) }
        }
        rule.onNodeWithTag("act").assertIsDisplayed()
    }

    @Test fun aNullTitleDrawsNoTitleAtAll() {
        rule.setContent { AppTheme { ScreenHeader(title = null, titleModifier = Modifier.testTag("t")) } }
        rule.onNodeWithTag("t").assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExist() = assertDoesNotExist()
}

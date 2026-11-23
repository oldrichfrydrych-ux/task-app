package cz.olda.tasks.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import cz.olda.tasks.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test

class AppShellTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsFourTabs() {
        rule.setContent { AppTheme { AppShell() } }
        listOf("Today", "Upcoming", "Inbox", "Lists").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun todayIsSelectedAtStart() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNodeWithText("Today").assertIsSelected()
    }

    @Test
    fun tappingInboxShowsInbox() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNodeWithText("Inbox").performClick()
        rule.onNodeWithTag("screen-Inbox").assertIsDisplayed()
    }

    @Test
    fun addButtonOpensFocusedQuickAdd() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNodeWithContentDescription("Add task").performClick()
        rule.onNodeWithTag("quick-add-field").assertIsFocused()
    }

    @Test
    fun backClosesQuickAdd() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNodeWithContentDescription("Add task").performClick()
        rule.onNodeWithTag("quick-add-field").assertIsFocused()
        // The focused field brings up the soft keyboard, and on Android the first Back
        // always hides the keyboard. The second Back is the one the sheet sees.
        Espresso.pressBack()
        Espresso.pressBack()
        rule.onNodeWithTag("quick-add-field").assertDoesNotExist()
        // The app itself stays open, with the shell where it was.
        rule.onNodeWithText("Today").assertIsSelected()
    }

    @Test
    fun selectedTabSurvivesRecreation() {
        val restore = StateRestorationTester(rule)
        restore.setContent { AppTheme { AppShell() } }
        rule.onNodeWithText("Inbox").performClick()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Inbox").assertIsSelected()
    }
}

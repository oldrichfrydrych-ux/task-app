package cz.olda.tasks.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import cz.olda.tasks.ui.nav.Routes
import cz.olda.tasks.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppShellTest {

    @get:Rule
    val rule = createComposeRule()

    /** The tab, not the Today screen header that carries the same word. */
    private fun todayTab() = rule.onNode(hasText("Today") and hasAnyAncestor(hasTestTag("bottom-bar")))

    @Test
    fun showsFourTabs() {
        rule.setContent { AppTheme { AppShell() } }
        todayTab().assertIsDisplayed()
        listOf("Upcoming", "Inbox", "Lists").forEach {
            rule.onNode(hasText(it) and hasAnyAncestor(hasTestTag("bottom-bar"))).assertIsDisplayed()
        }
    }

    @Test
    fun todayIsSelectedAtStart() {
        rule.setContent { AppTheme { AppShell() } }
        todayTab().assertIsSelected()
    }

    @Test
    fun tappingInboxShowsInbox() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNode(hasText("Inbox") and hasAnyAncestor(hasTestTag("bottom-bar"))).performClick()
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
        todayTab().assertIsSelected()
    }

    @Test
    fun selectedTabSurvivesRecreation() {
        val restore = StateRestorationTester(rule)
        restore.setContent { AppTheme { AppShell() } }
        rule.onNode(hasText("Inbox") and hasAnyAncestor(hasTestTag("bottom-bar"))).performClick()
        restore.emulateSavedInstanceStateRestore()
        rule.onNode(hasText("Inbox") and hasAnyAncestor(hasTestTag("bottom-bar"))).assertIsSelected()
    }

    /** Typing a task and losing it to a process restart would be worse than no sheet. */
    @Test
    fun quickAddKeepsWhatYouTyped() {
        val restore = StateRestorationTester(rule)
        restore.setContent { AppTheme { AppShell() } }
        rule.onNodeWithContentDescription("Add task").performClick()
        rule.onNodeWithTag("quick-add-field").performTextInput("zitra zavolat na urad")
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("quick-add-field").assertTextContains("zitra zavolat na urad")
    }

    /**
     * Detail screens belong to no tab, and the saved name used to change only when a tab was
     * tapped, so a detail opened after Back highlighted whichever tab was tapped last.
     */
    @Test
    fun aDetailKeepsTheTabItWasOpenedFrom() {
        lateinit var navController: NavHostController
        rule.setContent {
            navController = rememberNavController()
            AppTheme { AppShell(navController = navController) }
        }
        rule.onNode(hasText("Lists") and hasAnyAncestor(hasTestTag("bottom-bar"))).performClick()
        Espresso.pressBack()
        rule.onNodeWithTag("screen-Today").assertIsDisplayed()
        rule.runOnUiThread { navController.navigate(Routes.task(1)) }
        rule.onNodeWithTag("screen-TaskDetail").assertIsDisplayed()
        todayTab().assertIsSelected()
    }

    /**
     * The app draws edge to edge, so the floating bar has to keep itself out of the
     * phone's gesture bar - otherwise the add button sits under it.
     */
    @Test
    fun bottomBarStaysClearOfTheSystemBar() {
        var systemBar = 0.dp
        rule.setContent {
            val density = LocalDensity.current
            systemBar = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
            AppTheme { AppShell() }
        }
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        val bar = rule.onNodeWithTag("bottom-bar").getUnclippedBoundsInRoot()
        assertTrue(
            "the system bar is $systemBar tall, but the floating bar is drawn down to " +
                "${root.height - bar.bottom} above the bottom edge",
            bar.bottom <= root.height - systemBar,
        )
    }
}

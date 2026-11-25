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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import cz.olda.tasks.ui.nav.Routes
import cz.olda.tasks.ui.theme.AppTheme
import org.junit.Assert.assertEquals
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

    /** One pill travels to the tapped tab instead of each tab drawing its own background. */
    @Test
    fun theSelectedPillMovesToTheTappedTab() {
        rule.setContent { AppTheme { AppShell() } }
        val before = rule.onNodeWithTag("bottom-bar-pill").getUnclippedBoundsInRoot().left
        rule.onNode(hasText("Lists") and hasAnyAncestor(hasTestTag("bottom-bar"))).performClick()
        rule.waitForIdle()
        val after = rule.onNodeWithTag("bottom-bar-pill").getUnclippedBoundsInRoot().left
        assertTrue("pill did not move: $before -> $after", after > before)
    }

    /**
     * Review Focus 1. Someone who turned animations off still has to see which tab is
     * selected, so the pill must land rather than never travel.
     */
    @Test
    fun thePillStillLandsOnTheTabWithAnimationsOff() {
        rule.setContent { AppTheme(animationsEnabled = false) { AppShell() } }
        val inbox = hasText("Inbox") and hasAnyAncestor(hasTestTag("bottom-bar"))
        rule.onNode(inbox).performClick()
        rule.onNode(inbox).assertIsSelected()
        val pill = rule.onNodeWithTag("bottom-bar-pill").getUnclippedBoundsInRoot()
        val tab = rule.onNode(inbox).getUnclippedBoundsInRoot()
        assertTrue(
            "pill at ${pill.left}..${pill.right} is not over the Inbox tab at ${tab.left}..${tab.right}",
            pill.left <= tab.left && pill.right >= tab.right,
        )
    }

    @Test
    fun theBarSlimsOnTheWayDownAndReturnsOnTheWayUp() {
        rule.setContent { AppTheme { AppShell() } }
        val tall = rule.onNodeWithTag("bottom-bar").getUnclippedBoundsInRoot().height
        rule.onNodeWithTag("screen-Today").performTouchInput { swipeUp() }
        rule.waitForIdle()
        val slim = rule.onNodeWithTag("bottom-bar").getUnclippedBoundsInRoot().height
        assertTrue("bar did not slim: $tall -> $slim", slim < tall)
        rule.onNodeWithTag("screen-Today").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(tall, rule.onNodeWithTag("bottom-bar").getUnclippedBoundsInRoot().height)
    }

    /**
     * The design reserves the bar's tall height precisely so the scrolling content never
     * relayouts while it shrinks - 31 non-lazy rows reflowing every frame, with a feedback
     * path back into the scroll deltas driving the animation, is how this janks.
     */
    @Test
    fun theContentDoesNotMoveWhenTheBarSlims() {
        rule.setContent { AppTheme { AppShell() } }
        val slot = rule.onNodeWithTag("bottom-bar-slot").getUnclippedBoundsInRoot()
        rule.onNodeWithTag("screen-Today").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertEquals(slot, rule.onNodeWithTag("bottom-bar-slot").getUnclippedBoundsInRoot())
    }

    /** Capture must never get harder, whatever the bar is doing. */
    @Test
    fun theAddButtonSurvivesTheSlimming() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNodeWithTag("screen-Today").performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Add task").assertIsDisplayed().performClick()
        rule.onNodeWithTag("quick-add-field").assertIsFocused()
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

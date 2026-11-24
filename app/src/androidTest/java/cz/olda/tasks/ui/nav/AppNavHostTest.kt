package cz.olda.tasks.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import cz.olda.tasks.ui.LocalSnackbarHostState
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import cz.olda.tasks.ui.AppShell
import cz.olda.tasks.ui.Destination
import cz.olda.tasks.ui.theme.AppTheme
import cz.olda.tasks.ui.theme.LocalAnimationsEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppNavHostTest {

    @get:Rule
    val rule = createComposeRule()

    /** The tab, not the Today screen header that carries the same word. */
    private fun todayTab() = rule.onNode(hasText("Today") and hasAnyAncestor(hasTestTag("bottom-bar")))

    @Test
    fun opensOnToday() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNodeWithTag("screen-Today").assertIsDisplayed()
    }

    @Test
    fun aTabTakesYouToItsScreen() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNode(hasText("Lists") and hasAnyAncestor(hasTestTag("bottom-bar"))).performClick()
        rule.onNodeWithTag("screen-Lists").assertIsDisplayed()
    }

    @Test
    fun tabsDoNotPileUpOnTheBackStack() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNode(hasText("Lists") and hasAnyAncestor(hasTestTag("bottom-bar"))).performClick()
        todayTab().performClick()
        rule.onNode(hasText("Lists") and hasAnyAncestor(hasTestTag("bottom-bar"))).performClick()
        Espresso.pressBack()
        rule.onNodeWithTag("screen-Today").assertIsDisplayed()
        todayTab().assertIsSelected()
    }

    /** Builds the graph alone, so the measurements are of the screens and not of the shell. */
    private fun graph(animate: Boolean = true, onReady: (NavHostController) -> Unit) {
        lateinit var navController: NavHostController
        rule.setContent {
            AppTheme {
                navController = rememberNavController()
                CompositionLocalProvider(
                    LocalSnackbarHostState provides remember { SnackbarHostState() },
                    LocalAnimationsEnabled provides animate,
                ) {
                    AppNavHost(navController)
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        onReady(navController)
    }

    /** Forward navigation travels: the detail arrives from the trailing edge, it does not cut. */
    @Test
    fun aDetailSlidesInBeforeItLands() {
        rule.mainClock.autoAdvance = false
        graph { navController ->
            rule.runOnUiThread { navController.navigate(Routes.task(1)) }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val travelling = rule.onNodeWithTag("screen-TaskDetail").getUnclippedBoundsInRoot()
        assertTrue("the detail did not travel; left was ${travelling.left}", travelling.left > 1.dp)

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val landed = rule.onNodeWithTag("screen-TaskDetail").getUnclippedBoundsInRoot()
        assertEquals("the detail never settled", 0.dp, landed.left)
    }

    /** Review Focus 1: with animations off the destination is simply there. */
    @Test
    fun aDetailIsInPlaceAtOnceWithAnimationsOff() {
        rule.mainClock.autoAdvance = false
        graph(animate = false) { navController ->
            rule.runOnUiThread { navController.navigate(Routes.task(1)) }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val bounds = rule.onNodeWithTag("screen-TaskDetail").getUnclippedBoundsInRoot()
        assertEquals("the detail slid although animations are off", 0.dp, bounds.left)
    }

    /** The start destination used to follow the restored tab, which broke popping afterwards. */
    @Test
    fun tabsDoNotPileUpAfterRecreation() {
        val restore = StateRestorationTester(rule)
        restore.setContent { AppTheme { AppShell() } }
        rule.onNode(hasText("Lists") and hasAnyAncestor(hasTestTag("bottom-bar"))).performClick()
        restore.emulateSavedInstanceStateRestore()
        todayTab().performClick()
        rule.onNode(hasText("Inbox") and hasAnyAncestor(hasTestTag("bottom-bar"))).performClick()
        Espresso.pressBack()
        rule.onNodeWithTag("screen-Today").assertIsDisplayed()
        todayTab().assertIsSelected()
    }

    @Test
    fun backFromADetailReturnsToItsTab() {
        lateinit var navController: NavHostController
        rule.setContent {
            AppTheme {
                navController = rememberNavController()
                CompositionLocalProvider(LocalSnackbarHostState provides remember { SnackbarHostState() }) {
                    AppNavHost(navController)
                }
            }
        }
        rule.runOnUiThread {
            navController.navigate(Destination.Lists.route)
            navController.navigate(Routes.list(1))
        }
        rule.onNodeWithTag("screen-ListDetail").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithTag("screen-Lists").assertIsDisplayed()
    }
}

package cz.olda.tasks.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import cz.olda.tasks.ui.AppShell
import cz.olda.tasks.ui.Destination
import cz.olda.tasks.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test

class AppNavHostTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun opensOnToday() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNodeWithTag("screen-Today").assertIsDisplayed()
    }

    @Test
    fun aTabTakesYouToItsScreen() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNodeWithText("Lists").performClick()
        rule.onNodeWithTag("screen-Lists").assertIsDisplayed()
    }

    @Test
    fun tabsDoNotPileUpOnTheBackStack() {
        rule.setContent { AppTheme { AppShell() } }
        rule.onNodeWithText("Lists").performClick()
        rule.onNodeWithText("Today").performClick()
        rule.onNodeWithText("Lists").performClick()
        Espresso.pressBack()
        rule.onNodeWithTag("screen-Today").assertIsDisplayed()
        rule.onNodeWithText("Today").assertIsSelected()
    }

    /** The start destination used to follow the restored tab, which broke popping afterwards. */
    @Test
    fun tabsDoNotPileUpAfterRecreation() {
        val restore = StateRestorationTester(rule)
        restore.setContent { AppTheme { AppShell() } }
        rule.onNodeWithText("Lists").performClick()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Today").performClick()
        rule.onNodeWithText("Inbox").performClick()
        Espresso.pressBack()
        rule.onNodeWithTag("screen-Today").assertIsDisplayed()
        rule.onNodeWithText("Today").assertIsSelected()
    }

    @Test
    fun backFromADetailReturnsToItsTab() {
        lateinit var navController: NavHostController
        rule.setContent {
            AppTheme {
                navController = rememberNavController()
                AppNavHost(navController)
            }
        }
        rule.runOnUiThread {
            navController.navigate(Destination.Lists.route)
            navController.navigate(Routes.list(1))
        }
        rule.onNodeWithTag("screen-list").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithTag("screen-Lists").assertIsDisplayed()
    }
}

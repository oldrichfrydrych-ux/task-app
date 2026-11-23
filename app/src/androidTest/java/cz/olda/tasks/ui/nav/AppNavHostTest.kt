package cz.olda.tasks.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
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
    fun backFromADetailReturnsToItsTab() {
        lateinit var navController: NavHostController
        rule.setContent {
            AppTheme {
                navController = rememberNavController()
                AppNavHost(navController, selected = Destination.Today)
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

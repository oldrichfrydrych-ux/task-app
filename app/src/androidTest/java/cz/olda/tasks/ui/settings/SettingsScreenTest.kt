package cz.olda.tasks.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.olda.tasks.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one row Settings gains this phase: a line saying reminders are off, when they are.
 *
 * The screen is given the state rather than reading the system, so the test needs no way to turn
 * Android's own notification switch off — which, on Olda's phone, nothing programmatic can do.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun screen(asked: Boolean, enabled: Boolean) {
        compose.setContent {
            AppTheme {
                SettingsContent(state = SettingsState(asked = asked, notificationsEnabled = enabled), onOpenSystemSettings = {})
            }
        }
    }

    @Test
    fun theBlockedRowIsHiddenWhenNotificationsWork() {
        screen(asked = true, enabled = true)

        compose.onNodeWithTag("settings-reminders-blocked").assertDoesNotExist()
    }

    /** A fresh install must not explain a refusal it has not asked about yet. */
    @Test
    fun theBlockedRowIsHiddenBeforeTheQuestionHasBeenAsked() {
        screen(asked = false, enabled = false)

        compose.onNodeWithTag("settings-reminders-blocked").assertDoesNotExist()
    }

    @Test
    fun theBlockedRowShowsOnceAskedAndStillBlocked() {
        screen(asked = true, enabled = false)

        compose.onNodeWithTag("settings-reminders-blocked").assertIsDisplayed()
    }

    @Test
    fun theColourSectionIsStillThere() {
        screen(asked = true, enabled = true)

        compose.onNodeWithTag("screen-Settings").assertIsDisplayed()
        // Phase 1's colour picker, by the hex field it has always had.
        compose.onNodeWithTag("hex-field").assertIsDisplayed()
    }
}

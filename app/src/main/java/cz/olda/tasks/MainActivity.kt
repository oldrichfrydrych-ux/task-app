package cz.olda.tasks

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import cz.olda.tasks.data.SettingsRepository
import cz.olda.tasks.platform.Notifications
import cz.olda.tasks.ui.AppShell
import cz.olda.tasks.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * The task a tapped reminder asked for, until the shell has navigated to it. The activity is
     * `singleTop`, so a tap while the app is already open arrives at [onNewIntent] rather than
     * building a second copy of the app, and this is how that reaches the composition.
     */
    private val openTask = mutableStateOf<Long?>(null)

    private val settings: SettingsRepository
        get() = (application as TasksApplication).container.settingsRepository

    /** Whatever the answer, the question has been put; see [askAboutNotificationsOnce]. */
    private val askForNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { rememberWeAsked() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTask.value = taskIdOf(intent)
        askAboutNotificationsOnce()
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppShell(
                    openTaskId = openTask.value,
                    onTaskOpened = { openTask.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTask.value = taskIdOf(intent)
    }

    /**
     * Android stops showing the dialog itself after two refusals, so "once" has to be the app's own
     * memory, in the settings table. A refusal changes nothing else: the app works, and Settings
     * grows a line saying reminders are off.
     *
     * It lives on the activity and not inside `AppShell`, because a composable that launches a
     * system dialog puts that dialog over every test of the shell — which is exactly what happened
     * when it was in there: five of `AppShellTest`'s cases died with "No compose hierarchies found".
     */
    private fun askAboutNotificationsOnce() {
        lifecycleScope.launch {
            val asked = settings
                .observeBoolean(SettingsRepository.NOTIFICATIONS_ASKED, default = false)
                .first()
            if (asked) return@launch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                rememberWeAsked()
            }
        }
    }

    private fun rememberWeAsked() {
        lifecycleScope.launch {
            settings.setBoolean(SettingsRepository.NOTIFICATIONS_ASKED, true)
        }
    }

    /** 0 is Android's own default for a missing long, and no task ever has that id. */
    private fun taskIdOf(intent: Intent?): Long? =
        intent?.getLongExtra(Notifications.EXTRA_TASK_ID, 0L)?.takeIf { it > 0L }
}

package cz.olda.tasks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import cz.olda.tasks.platform.Notifications
import cz.olda.tasks.ui.AppShell
import cz.olda.tasks.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    /**
     * The task a tapped reminder asked for, until the shell has navigated to it. The activity is
     * `singleTop`, so a tap while the app is already open arrives at [onNewIntent] rather than
     * building a second copy of the app, and this is how that reaches the composition.
     */
    private val openTask = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTask.value = taskIdOf(intent)
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

    /** 0 is Android's own default for a missing long, and no task ever has that id. */
    private fun taskIdOf(intent: Intent?): Long? =
        intent?.getLongExtra(Notifications.EXTRA_TASK_ID, 0L)?.takeIf { it > 0L }
}

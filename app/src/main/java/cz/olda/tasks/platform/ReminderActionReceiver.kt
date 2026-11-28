package cz.olda.tasks.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cz.olda.tasks.TasksApplication
import cz.olda.tasks.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Clock

/** How long Snooze puts a reminder off for. Olda's choice on 28 Nov. */
const val SNOOZE_MINUTES = 60

/**
 * What the notification's two buttons do. Split from the receiver so a test can press them.
 *
 * Both go through the ordinary repository, which is the point: **Done** is
 * [TaskRepository.setDone], the one door every completion in the app goes through, so a repeating
 * task creates its next occurrence from the notification with no app open and nothing special
 * written here. **Snooze** moves the ring and never the task.
 */
internal class ReminderActions(
    private val repository: TaskRepository,
    private val notifier: ReminderNotifier,
    private val sync: ReminderSync,
    private val clock: Clock,
) {
    suspend fun done(taskId: Long) {
        repository.setDone(taskId, true)
        finish(taskId)
    }

    suspend fun snooze(taskId: Long) {
        repository.snooze(taskId, clock.millis() + SNOOZE_MINUTES * 60_000L)
        finish(taskId)
    }

    /**
     * The notification has been dealt with either way, so it goes; and the alarm is re-booked,
     * because what the app owes Olda next has just changed. A task that is already gone simply
     * writes nothing — `setDone` and `snooze` both no-op on a missing row — and the re-arm still
     * runs, which is what keeps a stale notification from leaving the alarm wrong.
     */
    private suspend fun finish(taskId: Long) {
        notifier.cancel(taskId)
        sync.rearmNow()
    }
}

/**
 * Done and Snooze arriving from the notification shade. The app process may not be running, so
 * everything it needs comes from the container and the broadcast is held open while it works.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (taskId <= 0L) return
        val container = (context.applicationContext as TasksApplication).container
        val actions = ReminderActions(
            container.taskRepository,
            SystemNotifier(context.applicationContext),
            container.reminderSync,
            container.clock,
        )
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> actions.done(taskId)
                    ACTION_SNOOZE -> actions.snooze(taskId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "cz.olda.tasks.action.DONE"
        const val ACTION_SNOOZE = "cz.olda.tasks.action.SNOOZE"
        const val EXTRA_TASK_ID = "cz.olda.tasks.actionTaskId"
    }
}

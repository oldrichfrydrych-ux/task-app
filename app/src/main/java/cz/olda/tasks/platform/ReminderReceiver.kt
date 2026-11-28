package cz.olda.tasks.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cz.olda.tasks.TasksApplication
import cz.olda.tasks.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What happens when the alarm fires: post every task in the ring, then book the one after it.
 *
 * Split from the receiver so it can be driven directly in a test — waiting for a real alarm would
 * mean waiting on the clock. The receiver itself is four lines of plumbing.
 */
internal class ReminderDelivery(
    private val repository: TaskRepository,
    private val notifier: ReminderNotifier,
    private val sync: ReminderSync,
) {
    /**
     * A task that was completed or deleted between the booking and the alarm is skipped, and does
     * not stop the others: the ring was worked out when the alarm was booked, and minutes can pass
     * before it fires. The summary is posted only when more than one reminder really went out, so
     * Android has something to collapse them under.
     */
    suspend fun fire(taskIds: List<Long>) {
        var posted = 0
        for (id in taskIds) {
            val task = repository.getTaskWithList(id) ?: continue
            if (task.task.done) continue
            notifier.post(id, task.task.title, task.listName)
            posted++
        }
        if (posted > 1) notifier.postSummary(posted)
        // Always, even for an empty ring: whatever woke us, the next alarm still has to exist.
        sync.rearmNow()
    }
}

/**
 * The alarm's landing point. It can run with no app process alive, which is why it re-arms through
 * [ReminderSync.rearmNow] rather than relying on anything to be collecting.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ids = intent.getLongArrayExtra(EXTRA_TASK_IDS)?.toList() ?: emptyList()
        val container = (context.applicationContext as TasksApplication).container
        val delivery = ReminderDelivery(
            container.taskRepository,
            SystemNotifier(context.applicationContext),
            container.reminderSync,
        )
        // A receiver cannot wait for a coroutine on its own, so the broadcast is held open until
        // the posting and the re-arm are done.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                delivery.fire(ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_AT_MILLIS = "cz.olda.tasks.ringAt"
        const val EXTRA_TASK_IDS = "cz.olda.tasks.ringTaskIds"
    }
}

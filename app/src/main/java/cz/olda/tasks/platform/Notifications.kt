package cz.olda.tasks.platform

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cz.olda.tasks.MainActivity
import cz.olda.tasks.R

/**
 * The reminder notification: one per task, so Done and Snooze always mean one specific thing.
 *
 * Android-only, and so outside `data/`, which Phase 8 lifts to Windows. Everything in
 * `cz.olda.tasks.platform` is what that phase replaces, and nothing outside it is.
 *
 * Several reminders in the same morning are posted into one group, because every task dated with
 * no time rings at 08:00 and a day with six of them should not be six separate buzzes: Android
 * collapses a group under its summary and lets Olda expand it.
 *
 * Building a notification and posting it are separate here on purpose; [ReminderNotifier] says why.
 */
object Notifications {

    const val CHANNEL_ID = "reminders"

    /** Room's ids start at 1, so 0 is free for the group summary and can never be a task. */
    const val SUMMARY_ID = 0

    const val GROUP_KEY = "cz.olda.tasks.reminders"

    /** Which task a tapped notification should open; read by [MainActivity]. */
    const val EXTRA_TASK_ID = "cz.olda.tasks.taskId"

    /**
     * Creates the channel if it is not there. Creating one twice is a no-op in Android, so this can
     * be called on every start and nothing has to remember whether it has run.
     */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.reminder_channel_name))
            .setDescription(context.getString(R.string.reminder_channel_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * One task's reminder: its title, the name of its list, and nothing else — it is a reminder,
     * not a second copy of the task row. Tapping it opens that task's detail screen.
     */
    fun buildReminder(context: Context, taskId: Long, title: String, listName: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(listName)
            .setContentIntent(tapPendingIntent(context, taskId))
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

    /**
     * The row Android shows when several reminders are collapsed together. It carries no actions:
     * Done and Snooze belong to one task, and the summary stands for several.
     */
    fun buildSummary(context: Context, count: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.resources.getQuantityString(R.plurals.reminder_summary, count, count))
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

    /** The notification's id is the task's own, so a second post replaces the first. */
    @SuppressLint("MissingPermission") // Without the permission Android drops the post; it does not throw.
    fun post(context: Context, notificationId: Long, notification: Notification) =
        NotificationManagerCompat.from(context).notify(notificationId.toInt(), notification)

    fun cancelReminder(context: Context, taskId: Long) =
        NotificationManagerCompat.from(context).cancel(taskId.toInt())

    /**
     * The intent behind a tap. The task id is in the intent's **data** as well as its extras,
     * because `PendingIntent`s are matched without looking at extras: without distinct data, two
     * reminders would share one and both would open whichever task was posted last.
     */
    internal fun tapIntent(context: Context, taskId: Long): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("tasks://reminder/$taskId"))
            .putExtra(EXTRA_TASK_ID, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun tapPendingIntent(context: Context, taskId: Long) = PendingIntent.getActivity(
        context,
        0,
        tapIntent(context, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

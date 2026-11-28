package cz.olda.tasks.platform

import android.content.Context

/**
 * What puts a reminder in front of Olda, as a seam the receivers can be tested through.
 *
 * The same shape, and the same reason, as [AlarmBooking]: a test can record what would have been
 * posted instead of waiting for the system to accept it. That matters more here than it looks —
 * Olda's phone refuses every programmatic grant of `POST_NOTIFICATIONS` (both the instrumentation's
 * own `grantRuntimePermission` and `adb shell pm grant` fail: its shell user has had
 * `GRANT_RUNTIME_PERMISSIONS` taken away), and an ungranted post is dropped in silence, so a test
 * reading the notification shade on that device could only ever pass by seeing nothing. What this
 * app builds is tested in `NotificationsTest`; that it reaches the shade is checked by hand on his
 * phone, with the permission granted the ordinary way.
 */
interface ReminderNotifier {
    fun post(taskId: Long, title: String, listName: String)

    /** The row Android shows when several reminders collapse together. */
    fun postSummary(count: Int)

    fun cancel(taskId: Long)
}

/** The real one: hands the notifications Android asked for to Android. */
class SystemNotifier(private val context: Context) : ReminderNotifier {
    override fun post(taskId: Long, title: String, listName: String) =
        Notifications.post(context, taskId, Notifications.buildReminder(context, taskId, title, listName))

    override fun postSummary(count: Int) =
        Notifications.post(context, Notifications.SUMMARY_ID.toLong(), Notifications.buildSummary(context, count))

    override fun cancel(taskId: Long) = Notifications.cancelReminder(context, taskId)
}

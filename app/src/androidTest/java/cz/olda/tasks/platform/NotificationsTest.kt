package cz.olda.tasks.platform

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.olda.tasks.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a reminder notification says, and that its channel exists.
 *
 * It asserts on the notification this app **builds**, not on what reaches the notification shade:
 * Olda's phone refuses every programmatic grant of `POST_NOTIFICATIONS`, an ungranted post is
 * dropped in silence, and `connectedDebugAndroidTest` uninstalls the app, which would clear a grant
 * he made by hand anyway. [ReminderNotifier] carries the full reasoning. That a reminder really
 * arrives is checked on his phone, by hand, with the permission granted the ordinary way.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun theChannelExistsAfterEnsureChannel() {
        Notifications.ensureChannel(context)

        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(Notifications.CHANNEL_ID)
        assertNotNull("the reminder channel must exist", channel)
        assertEquals(context.getString(R.string.reminder_channel_name), channel!!.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun aReminderSaysTheTitleAndTheList() {
        val notification = Notifications.buildReminder(context, 42, "Zavolat na úřad", "🌊Práce")

        assertEquals("Zavolat na úřad", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("🌊Práce", notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun aReminderIsInTheGroupAndIsNotTheSummary() {
        val notification = Notifications.buildReminder(context, 42, "Zavolat", "Inbox")

        assertEquals(Notifications.GROUP_KEY, notification.group)
        assertEquals(0, notification.flags and Notification.FLAG_GROUP_SUMMARY)
    }

    @Test
    fun aReminderGoesAwayWhenItIsTapped() {
        val notification = Notifications.buildReminder(context, 42, "Zavolat", "Inbox")

        assertNotNull("a reminder must open its task", notification.contentIntent)
        assertTrue(
            "a tapped reminder must not stay in the shade",
            notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
        )
    }

    /**
     * The task id has to be in the intent's data, not only its extras: `PendingIntent`s are matched
     * without looking at extras, so two reminders sharing one would both open the same task.
     */
    @Test
    fun eachReminderCarriesItsOwnTaskInTheIntentData() {
        val first = Notifications.tapIntent(context, 42)
        val second = Notifications.tapIntent(context, 7)

        assertEquals("tasks://reminder/42", first.data.toString())
        assertEquals("tasks://reminder/7", second.data.toString())
        assertEquals(42L, first.getLongExtra(Notifications.EXTRA_TASK_ID, 0L))
        assertTrue("two tasks must not share a pending intent", !first.filterEquals(second))
    }

    @Test
    fun theSummaryIsMarkedAsOneAndCountsTheTasks() {
        val summary = Notifications.buildSummary(context, 3)

        assertEquals(Notifications.GROUP_KEY, summary.group)
        assertTrue(
            "the summary must be marked as a group summary",
            summary.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
        assertEquals(
            context.resources.getQuantityString(R.plurals.reminder_summary, 3, 3),
            summary.extras.getString(Notification.EXTRA_TITLE),
        )
    }
}

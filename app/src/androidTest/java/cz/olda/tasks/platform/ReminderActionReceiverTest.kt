package cz.olda.tasks.platform

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.olda.tasks.R
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Done and Snooze, as they arrive from the notification. Driven directly, like the alarm itself:
 * what matters is what each button does to the task, and that the alarm is re-booked afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ReminderActionReceiverTest {
    private lateinit var db: AppDatabase
    private lateinit var posts: Posts
    private lateinit var bookings: Bookings
    private lateinit var scope: CoroutineScope
    private var inbox = 0L

    private val zone = ZoneId.of("Europe/Prague")
    private val today = LocalDate.of(2026, 12, 2)

    private fun at(hour: Int) = today.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    /** The app as it is at [hour] o'clock: a reminder's buttons are pressed when it fires. */
    private fun appAt(hour: Int): Pair<TaskRepository, ReminderActions> {
        val clock = Clock.fixed(today.atTime(hour, 0).atZone(zone).toInstant(), zone)
        val repo = TaskRepository(db.taskDao(), clock)
        return repo to ReminderActions(repo, posts, ReminderSync(scope, repo, bookings), clock)
    }

    @Before
    fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        inbox = ListRepository(db.listDao(), db.groupDao(), db.headingDao()).inboxId()
        posts = Posts()
        bookings = Bookings()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun close() {
        scope.cancel()
        db.close()
    }

    @Test
    fun doneCompletesTheTaskAndTakesTheNotificationAway() = runBlocking {
        val (repo, actions) = appAt(15)
        val id = repo.add("zavolat", inbox, today, 15 * 60, 4)

        actions.done(id)

        assertTrue("the task must be done", repo.getTask(id)!!.done)
        assertEquals(listOf(id), posts.cancelled)
    }

    /** The whole reason completion has one door: Done from a notification rolls a repeat too. */
    @Test
    fun doneOnARecurringTaskCreatesTheNextOccurrence() = runBlocking {
        val (repo, actions) = appAt(15)
        val id = repo.add("vynest kose", inbox, today, 15 * 60, 4, recurrence = "FREQ=DAILY")

        actions.done(id)

        val next = repo.observeUpcoming().first().flatMap { it.tasks }
        assertEquals("tomorrow's copy must exist", 1, next.size)
        assertEquals(today.plusDays(1).toEpochDay(), next.single().task.dueDate)
        assertEquals(15 * 60, next.single().task.dueMinute)
    }

    @Test
    fun snoozeMovesTheRingAnHourOnAndLeavesTheDueTimeAlone() = runBlocking {
        val (repo, actions) = appAt(15)
        val id = repo.add("zavolat", inbox, today, 15 * 60, 4)

        actions.snooze(id)

        val task = repo.getTask(id)!!
        assertEquals("the ring moves an hour on", at(16), task.snoozedUntil)
        assertEquals("the task is still due when it was due", today.toEpochDay(), task.dueDate)
        assertEquals(15 * 60, task.dueMinute)
        assertFalse("snoozing must not complete it", task.done)
        assertEquals(listOf(id), posts.cancelled)
    }

    @Test
    fun snoozeAgainAnHourLaterAddsAnotherHour() = runBlocking {
        val (repo, actions) = appAt(15)
        val id = repo.add("zavolat", inbox, today, 15 * 60, 4)
        actions.snooze(id)

        val (_, laterActions) = appAt(16)
        laterActions.snooze(id)

        assertEquals(at(17), repo.getTask(id)!!.snoozedUntil)
    }

    @Test
    fun snoozeRebooksTheAlarmForTheNewTime() = runBlocking {
        val (repo, actions) = appAt(15)
        val id = repo.add("zavolat", inbox, today, 15 * 60, 4)

        actions.snooze(id)

        assertEquals(at(16), bookings.last?.atMillis)
        assertEquals(listOf(id), bookings.last?.taskIds)
    }

    @Test
    fun doneRebooksTheAlarmForWhateverIsLeft() = runBlocking {
        val (repo, actions) = appAt(15)
        val id = repo.add("zavolat", inbox, today, 15 * 60, 4)
        val later = repo.add("vecer", inbox, today, 19 * 60, 4)

        actions.done(id)

        assertEquals(at(19), bookings.last?.atMillis)
        assertEquals(listOf(later), bookings.last?.taskIds)
    }

    @Test
    fun anActionForATaskThatIsGoneChangesNothingAndDoesNotThrow() = runBlocking {
        val (repo, actions) = appAt(15)
        val id = repo.add("zavolat", inbox, today, 15 * 60, 4)
        repo.softDelete(id)

        actions.done(id)
        actions.snooze(id)

        assertNull(repo.getTask(id))
        assertNotNull("the alarm is still re-armed", bookings.cancelled > 0 || bookings.last != null)
    }
}

package cz.olda.tasks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.olda.tasks.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The one ring to book, read out of the tasks table. The clock is fixed to Wednesday 2 December
 * 2026 at 10:00, so a task at 15:00 today is ahead of "now" and one at 08:00 today is behind it.
 */
@RunWith(AndroidJUnit4::class)
class NextReminderTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private var inbox = 0L

    private val zone = ZoneId.of("Europe/Prague")
    private val today = LocalDate.of(2026, 12, 2)
    private val tomorrow = today.plusDays(1)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        repo = TaskRepository(db.taskDao(), clock)
        inbox = ListRepository(db.listDao(), db.groupDao(), db.headingDao()).inboxId()
    }

    @After
    fun close() {
        db.close()
    }

    private suspend fun add(title: String, due: LocalDate? = null, minute: Int? = null) =
        repo.add(title, inbox, due, minute, 4)

    @Test
    fun aTimedTaskAheadOfNowIsTheNextRing() = runBlocking {
        val id = add("zavolat", today, 15 * 60)

        val ring = repo.observeNextReminder().first()!!

        assertEquals(at(today, 15), ring.atMillis)
        assertEquals(listOf(id), ring.taskIds)
    }

    @Test
    fun aDoneTaskNeverRings() = runBlocking {
        val id = add("zavolat", today, 15 * 60)
        repo.setDone(id, true)

        assertNull(repo.observeNextReminder().first())
    }

    @Test
    fun aSoftDeletedTaskNeverRings() = runBlocking {
        val id = add("zavolat", today, 15 * 60)
        repo.softDelete(id)

        assertNull(repo.observeNextReminder().first())
    }

    @Test
    fun anUndatedTaskNeverRings() = runBlocking {
        add("nekdy")

        assertNull(repo.observeNextReminder().first())
    }

    @Test
    fun twoUntimedTasksOnTheSameDayShareOneRing() = runBlocking {
        val first = add("kabat", tomorrow)
        val second = add("ekonomka", tomorrow)

        val ring = repo.observeNextReminder().first()!!

        assertEquals("both ring at 08:00", at(tomorrow, 8), ring.atMillis)
        assertEquals(listOf(first, second), ring.taskIds)
    }

    @Test
    fun snoozingMovesTheRingAndLeavesTheDueTimeAlone() = runBlocking {
        val id = add("zavolat", today, 15 * 60)

        repo.snooze(id, at(today, 16))

        assertEquals(at(today, 16), repo.observeNextReminder().first()!!.atMillis)
        val task = repo.getTask(id)!!
        assertEquals("the task is still due when it was due", today.toEpochDay(), task.dueDate)
        assertEquals(15 * 60, task.dueMinute)
    }

    @Test
    fun clearingASnoozePutsTheRingBack() = runBlocking {
        val id = add("zavolat", today, 15 * 60)
        repo.snooze(id, at(today, 16))

        repo.snooze(id, null)

        assertEquals(at(today, 15), repo.observeNextReminder().first()!!.atMillis)
    }

    @Test
    fun completingTheOnlyDatedTaskLeavesNoRing() = runBlocking {
        val id = add("zavolat", today, 15 * 60)
        assertEquals(at(today, 15), repo.observeNextReminder().first()!!.atMillis)

        repo.setDone(id, true)

        assertNull(repo.observeNextReminder().first())
    }

    @Test
    fun anOrdinaryEditDoesNotCancelASnooze() = runBlocking {
        val id = add("zavolat", today, 15 * 60)
        repo.snooze(id, at(today, 16))

        repo.update(repo.getTask(id)!!.copy(title = "zavolat na urad"))

        assertEquals(at(today, 16), repo.observeNextReminder().first()!!.atMillis)
    }
}

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
 * Ticking a repeating task creates the next one. The clock is fixed to Wednesday 2 December 2026
 * at 10:00, so "due Monday, ticked today" is the late case from the design document's table.
 */
@RunWith(AndroidJUnit4::class)
class RecurringCompletionTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var tags: TagRepository
    private var inbox = 0L
    private var other = 0L

    private val zone = ZoneId.of("Europe/Prague")
    private val wed = LocalDate.of(2026, 12, 2)
    private val mon = LocalDate.of(2026, 11, 30)
    private val clock = Clock.fixed(wed.atTime(10, 0).atZone(zone).toInstant(), zone)

    @Before
    fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        repo = TaskRepository(db.taskDao(), clock)
        tags = TagRepository(db.tagDao())
        val lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
        inbox = lists.inboxId()
        other = lists.createList("Práce")
    }

    @After
    fun close() {
        db.close()
    }

    private suspend fun add(
        title: String,
        due: LocalDate? = null,
        minute: Int? = null,
        repeat: String? = null,
        listId: Long = inbox,
        parent: Long? = null,
    ) = repo.add(title, listId, due, minute, 4, parentTaskId = parent, recurrence = repeat)

    /**
     * How many live tasks the database holds, done ones included. Read with raw SQL rather than
     * through a DAO method, so the production DAO gains nothing that only a test wants.
     */
    private fun liveTasks(): Int =
        db.query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL", null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    /** Rows with this id, soft-deleted ones included: what tells a hard delete from a soft one. */
    private fun rowsWithId(id: Long): Int =
        db.query("SELECT COUNT(*) FROM tasks WHERE id = ?", arrayOf<Any>(id)).use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun tickingADailyTaskCreatesTomorrow() = runBlocking {
        val id = add("vynest kose", due = wed, repeat = "FREQ=DAILY")
        val created = repo.setDone(id, true)

        assertNotNull("a repeat must create its next occurrence", created)
        val next = repo.getTask(created!!)!!
        assertEquals(LocalDate.of(2026, 12, 3).toEpochDay(), next.dueDate)
        assertFalse(next.done)
    }

    @Test
    fun tickingALateDailyTaskSkipsTheMissedDays() = runBlocking {
        val id = add("vynest kose", due = mon, repeat = "FREQ=DAILY")
        val created = repo.setDone(id, true)!!

        assertEquals(LocalDate.of(2026, 12, 3).toEpochDay(), repo.getTask(created)!!.dueDate)
    }

    @Test
    fun aWeeklyMondayRuleStaysOnMonday() = runBlocking {
        val id = add("tydenni reset", due = mon, repeat = "FREQ=WEEKLY;BYDAY=MO")
        val created = repo.setDone(id, true)!!

        assertEquals(LocalDate.of(2026, 12, 7).toEpochDay(), repo.getTask(created)!!.dueDate)
    }

    @Test
    fun theNextOccurrenceKeepsEverythingAboutTheTask() = runBlocking {
        val id = repo.add("zavolat na urad", other, wed, 15 * 60, 2, recurrence = "FREQ=DAILY")
        val before = repo.getTask(id)!!
        val next = repo.getTask(repo.setDone(id, true)!!)!!

        assertEquals(before.title, next.title)
        assertEquals(before.titleNormalised, next.titleNormalised)
        assertEquals(before.notes, next.notes)
        assertEquals(other, next.listId)
        assertEquals(15 * 60, next.dueMinute)
        assertEquals(2, next.priority)
        assertEquals("FREQ=DAILY", next.recurrence)
        assertFalse(next.done)
        assertNull(next.completedAt)
        assertNull(next.snoozedUntil)
        assertNull(next.googleId)
        assertNull(next.parentTaskId)
        assertTrue("the new task is a new row", next.id != before.id)
    }

    @Test
    fun theNextOccurrenceCarriesTheTags() = runBlocking {
        val id = add("zavolat", due = wed, repeat = "FREQ=DAILY")
        tags.attach(id, listOf("telefon", "urad"))

        val created = repo.setDone(id, true)!!

        assertEquals(
            listOf("telefon", "urad"),
            tags.observeTagsOf(created).first().map { it.name }.sorted(),
        )
    }

    @Test
    fun theCompletedTaskKeepsItsRuleAndIsDone() = runBlocking {
        val id = add("vynest kose", due = wed, repeat = "FREQ=DAILY")
        repo.setDone(id, true)

        val old = repo.getTask(id)!!
        assertTrue(old.done)
        assertNotNull(old.completedAt)
        assertEquals("FREQ=DAILY", old.recurrence)
    }

    @Test
    fun theNextOccurrenceHasNoSubtasks() = runBlocking {
        val parent = add("velky nakup", due = wed, repeat = "FREQ=WEEKLY")
        add("mleko", parent = parent)
        add("chleba", parent = parent)

        val created = repo.setDone(parent, true)!!

        assertEquals(emptyList<String>(), repo.observeSubtasks(created).first().map { it.title })
        assertTrue(
            "the old subtasks are completed with their parent, as before",
            repo.observeSubtasks(parent).first().all { it.done },
        )
    }

    @Test
    fun aRepeatWithNoDateStartsFromToday() = runBlocking {
        val id = add("protahnout se", repeat = "FREQ=DAILY")
        val created = repo.setDone(id, true)!!

        assertEquals(LocalDate.of(2026, 12, 3).toEpochDay(), repo.getTask(created)!!.dueDate)
    }

    @Test
    fun anUnreadableRuleJustCompletesTheTask() = runBlocking {
        val id = add("neco", due = wed, repeat = "FREQ=HOURLY")

        assertNull("nothing may be created on a guessed day", repo.setDone(id, true))
        assertTrue(repo.getTask(id)!!.done)
        assertEquals(1, liveTasks())
    }

    @Test
    fun aTaskWithNoRuleJustCompletes() = runBlocking {
        val id = add("koupit kabat", due = wed)

        assertNull(repo.setDone(id, true))
        assertTrue(repo.getTask(id)!!.done)
        assertEquals(1, liveTasks())
    }

    @Test
    fun reopeningATaskCreatesNothing() = runBlocking {
        val id = add("vynest kose", due = wed, repeat = "FREQ=DAILY")
        repo.setDone(id, true)

        assertNull(repo.setDone(id, false))

        // Nothing new appears, and what the tick created goes back with it — the other half is
        // untickingARecurringTaskRemovesTheOccurrenceItCreated. This test used to assert the count
        // was unchanged, which is how it managed to encode the duplicate rather than catch it.
        assertEquals(1, liveTasks())
    }

    @Test
    fun undoRemovesTheOccurrenceItCreated() = runBlocking {
        val id = add("vynest kose", due = wed, repeat = "FREQ=DAILY")
        val created = repo.setDone(id, true)!!

        repo.undoCompletion(id)

        assertEquals("the occurrence must be gone for good, not soft-deleted", 0, rowsWithId(created))
        assertFalse("the original is open again", repo.getTask(id)!!.done)
        assertEquals(1, liveTasks())
    }

    /**
     * Ticking a repeat on task detail creates tomorrow's copy, and there is no undo snackbar
     * there — so unticking has to take the copy back with it. Without this, one tap and one
     * change of mind leave two open copies of the same task, both still repeating.
     */
    @Test
    fun untickingARecurringTaskRemovesTheOccurrenceItCreated() = runBlocking {
        val id = add("vynest kose", due = wed, repeat = "FREQ=DAILY")
        val created = repo.setDone(id, true)!!

        repo.setDone(id, false)

        assertEquals("the occurrence must go back with the tick", 0, rowsWithId(created))
        assertFalse(repo.getTask(id)!!.done)
        assertEquals(1, liveTasks())
    }

    @Test
    fun untickingAnOrdinaryTaskStillJustReopensIt() = runBlocking {
        val id = add("koupit kabat", due = wed)
        repo.setDone(id, true)

        repo.setDone(id, false)

        assertFalse(repo.getTask(id)!!.done)
        assertEquals(1, liveTasks())
    }

    @Test
    fun undoOfADifferentTaskLeavesTheOccurrenceAlone() = runBlocking {
        val repeating = add("vynest kose", due = wed, repeat = "FREQ=DAILY")
        val plain = add("koupit kabat", due = wed)
        val created = repo.setDone(repeating, true)!!
        repo.setDone(plain, true)

        repo.undoCompletion(plain)

        assertNotNull("another task's undo must not delete it", repo.getTask(created))
    }
}

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

@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var lists: ListRepository
    private var inbox = 0L

    private val zone = ZoneId.of("Europe/Prague")
    private val today = LocalDate.of(2026, 11, 23)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    @Before fun open() = runBlocking<Unit> {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        repo = TaskRepository(db.taskDao(), clock)
        lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
        inbox = lists.inboxId()
    }

    @After fun close() { db.close() }

    private suspend fun add(
        title: String,
        due: LocalDate? = null,
        minute: Int? = null,
        listId: Long = inbox,
        parent: Long? = null,
    ) = repo.add(title, listId, due, minute, 4, parentTaskId = parent)

    @Test fun dayPutsOverdueFirstThenTimedThenAnytime() = runBlocking {
        add("yesterday", today.minusDays(1))
        add("three", today, 15 * 60)
        add("nine", today, 9 * 60)
        add("whenever", today)
        add("tomorrow", today.plusDays(1))
        val day = repo.observeDay(today).first()
        assertEquals(listOf("yesterday"), day.overdue.map { it.task.title })
        assertEquals(listOf("nine", "three"), day.timed.map { it.task.title })
        assertEquals(listOf("whenever"), day.anytime.map { it.task.title })
        assertEquals(listOf("tomorrow"), day.tomorrow.map { it.task.title })
        assertEquals(context.getString(R.string.inbox), day.anytime.single().listName)
    }

    @Test fun overdueAndTomorrowAreEmptyForOtherDays() = runBlocking {
        add("yesterday", today.minusDays(1))
        add("tomorrow", today.plusDays(1))
        add("later", today.plusDays(3))
        val day = repo.observeDay(today.plusDays(3)).first()
        assertTrue(day.overdue.isEmpty())
        assertTrue(day.tomorrow.isEmpty())
        assertEquals(listOf("later"), day.anytime.map { it.task.title })
    }

    @Test fun upcomingGroupsByDayEarliestFirst() = runBlocking {
        add("today", today)
        add("c", today.plusDays(5))
        add("a1", today.plusDays(2), 8 * 60)
        add("a2", today.plusDays(2))
        val upcoming = repo.observeUpcoming().first()
        assertEquals(listOf(today.plusDays(2), today.plusDays(5)), upcoming.map { it.date })
        assertEquals(listOf("a1", "a2"), upcoming[0].tasks.map { it.task.title })
    }

    @Test fun completingAParentCompletesItsSubtasks() = runBlocking {
        val parent = add("p")
        val child = add("c", parent = parent)
        repo.setDone(parent, true)
        assertTrue(repo.observeTask(parent).first()!!.done)
        assertNotNull(repo.observeTask(parent).first()!!.completedAt)
        assertTrue(repo.observeSubtasks(parent).first().single { it.id == child }.done)
    }

    @Test fun reopeningAParentLeavesSubtasksDone() = runBlocking {
        val parent = add("p")
        val child = add("c", parent = parent)
        repo.setDone(parent, true)
        repo.setDone(parent, false)
        val reopened = repo.observeTask(parent).first()!!
        assertFalse(reopened.done)
        assertNull(reopened.completedAt)
        assertTrue(repo.observeSubtasks(parent).first().single { it.id == child }.done)
    }

    @Test fun softDeletedTasksLeaveEveryQuery() = runBlocking {
        val list = lists.createList("L", "#112233", null)
        val parent = add("parent zebra", today, 60, listId = list)
        val child = add("child zebra", today.plusDays(1), listId = list, parent = parent)
        val gone = add("gone zebra", today.minusDays(1), listId = list)
        val other = add("other zebra", today.plusDays(2), listId = list)
        listOf(gone, child, other).forEach { repo.softDelete(it) }

        val day = repo.observeDay(today).first()
        assertTrue(day.overdue.isEmpty())
        assertEquals(listOf(parent), day.timed.map { it.task.id })
        assertTrue(day.tomorrow.isEmpty())
        assertTrue(repo.observeUpcoming().first().isEmpty())
        assertEquals(listOf(parent), repo.observeInList(list).first().map { it.task.id })
        assertTrue(repo.observeSubtasks(parent).first().isEmpty())
        assertNull(repo.observeTask(gone).first())
        assertEquals(listOf(parent), repo.search("zebra").first().map { it.task.id })
        assertEquals(
            mapOf(today to listOf("#112233")),
            repo.observeDots(today.minusDays(5), today.plusDays(5)).first(),
        )
        assertEquals(mapOf(list to 1), repo.observeOpenCounts().first())

        // restore brings a row back; deleting the last live one empties the rest.
        repo.restore(gone)
        assertEquals(listOf(gone), repo.observeDay(today).first().overdue.map { it.task.id })
        repo.softDelete(gone)
        repo.softDelete(parent)
        assertTrue(repo.search("zebra").first().isEmpty())
        assertTrue(repo.observeOpenCounts().first().isEmpty())
        assertTrue(repo.observeDots(today.minusDays(5), today.plusDays(5)).first().isEmpty())
    }

    @Test fun searchIgnoresCaseAndDiacritics() = runBlocking {
        val id = add("Zítra zavolat na úřad")
        assertEquals(listOf(id), repo.search("ZITRA ZAVOLAT").first().map { it.task.id })
        assertEquals(listOf(id), repo.search("URAD").first().map { it.task.id })
        assertTrue(repo.search("nothing").first().isEmpty())
    }

    @Test fun dotsReportOneColourPerListPerDay() = runBlocking {
        val red = lists.createList("R", "#FF0000", null)
        val blue = lists.createList("B", "#0000FF", null)
        add("r1", today, listId = red)
        add("r2", today, 60, listId = red)
        add("b1", today, listId = blue)
        add("r3", today.plusDays(1), listId = red)
        add("outside", today.plusDays(9), listId = red)
        val dots = repo.observeDots(today, today.plusDays(6)).first()
        assertEquals(setOf(today, today.plusDays(1)), dots.keys)
        assertEquals(2, dots.getValue(today).size)
        assertEquals(setOf("#FF0000", "#0000FF"), dots.getValue(today).toSet())
        assertEquals(listOf("#FF0000"), dots.getValue(today.plusDays(1)))
    }

    @Test fun openCountsIgnoreDoneAndDeletedTasks() = runBlocking {
        val list = lists.createList("L", null, null)
        add("open", listId = list)
        val done = add("done", listId = list)
        val deleted = add("deleted", listId = list)
        add("inbox one")
        repo.setDone(done, true)
        repo.softDelete(deleted)
        assertEquals(mapOf(list to 1, inbox to 1), repo.observeOpenCounts().first())
    }
}

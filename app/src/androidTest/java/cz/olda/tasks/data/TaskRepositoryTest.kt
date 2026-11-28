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

    /** A clock the test moves by hand, so a rewritten row shows up as a new updatedAt. */
    private class SteppingClock : Clock() {
        var ms = 1_000L
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): java.time.Instant = java.time.Instant.ofEpochMilli(ms)
        override fun millis(): Long = ms
    }

    @Test fun editingAParentThatStaysPutLeavesItsSubtasksUntouched() = runBlocking {
        val stepping = SteppingClock()
        val r = TaskRepository(db.taskDao(), stepping)
        val parent = r.add("p", inbox, null, null, 4)
        val child = r.add("c", inbox, null, null, 4, parentTaskId = parent)
        val stamp = r.getTask(child)!!.updatedAt

        stepping.ms += 5_000
        r.update(r.getTask(parent)!!.copy(title = "renamed"))
        assertEquals("renamed", r.getTask(parent)!!.title)
        assertEquals("a subtask that is already in place is not rewritten", stamp, r.getTask(child)!!.updatedAt)

        // Control: when the parent really moves, the subtask follows and is stamped.
        val work = lists.createList("Work", "#112233", null)
        stepping.ms += 5_000
        r.update(r.getTask(parent)!!.copy(listId = work))
        assertEquals(work, r.getTask(child)!!.listId)
        assertEquals(stepping.ms, r.getTask(child)!!.updatedAt)
    }

    @Test fun getTaskReadsALiveTaskOnceAndNeverASoftDeletedOne() = runBlocking {
        val id = add("here")
        assertEquals("here", repo.getTask(id)!!.title)
        assertNull(repo.getTask(id + 999))
        repo.softDelete(id)
        assertNull(repo.getTask(id))
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

    @Test fun doneTasksAreAbsentFromSearchAndLists() = runBlocking {
        val open = add("alpaca open")
        val done = add("alpaca done")
        repo.setDone(done, true)
        assertEquals(listOf(open), repo.search("alpaca").first().map { it.task.id })
        assertEquals(listOf(open), repo.observeInList(inbox).first().map { it.task.id })
    }

    @Test fun openCountsIgnoreSubtasks() = runBlocking {
        val parent = add("p")
        add("c1", parent = parent)
        add("c2", parent = parent)
        assertEquals(mapOf(inbox to 1), repo.observeOpenCounts().first())
    }

    @Test fun deletingAParentDeletesItsSubtasksAndRestoreBringsThemBack() = runBlocking {
        val parent = add("p zebra")
        val child = add("c zebra", parent = parent)
        repo.softDelete(parent)
        assertTrue(repo.observeSubtasks(parent).first().isEmpty())
        assertNull(repo.observeTask(child).first())
        assertNull(repo.observeTask(parent).first())
        assertTrue(repo.search("zebra").first().isEmpty())
        repo.restore(parent)
        assertEquals(listOf(child), repo.observeSubtasks(parent).first().map { it.id })
        assertEquals(listOf(parent), repo.observeInList(inbox).first().map { it.task.id })
    }

    @Test fun updateCannotUndeleteOrRevertATask() = runBlocking {
        val id = add("original")
        val snapshot = repo.observeTask(id).first()!!
        repo.softDelete(id)
        repo.update(snapshot.copy(title = "edited"))
        assertNull(repo.observeTask(id).first())
        repo.restore(id)
        assertEquals("original", repo.observeTask(id).first()!!.title)
    }

    @Test fun updateLeavesCompletionAndPositionAlone() = runBlocking {
        val id = add("t")
        val stale = repo.observeTask(id).first()!!
        repo.setDone(id, true)
        repo.update(stale.copy(title = "Úřad", notes = "Poznámka", priority = 1))
        val now = repo.observeTask(id).first()!!
        assertTrue(now.done)
        assertNotNull(now.completedAt)
        assertEquals("Úřad", now.title)
        assertEquals("urad", now.titleNormalised)
        assertEquals("poznamka", now.notesNormalised)
        assertEquals(1, now.priority)
    }

    @Test fun undoCompletionReopensTheParentAndItsCascadedSubtasks() = runBlocking {
        val parent = add("p")
        val c1 = add("c1", parent = parent)
        val c2 = add("c2", parent = parent)
        repo.setDone(parent, true)
        repo.undoCompletion(parent)
        listOf(parent, c1, c2).forEach {
            val task = repo.observeTask(it).first()!!
            assertFalse(task.done)
            assertNull(task.completedAt)
        }
    }

    @Test fun completingADoneTaskKeepsItsCompletionTime() = runBlocking {
        var millis = 1_000L
        val ticking = object : Clock() {
            override fun getZone(): ZoneId = zone
            override fun withZone(zone: ZoneId?): Clock = this
            override fun instant(): java.time.Instant = java.time.Instant.ofEpochMilli(millis)
        }
        val ticker = TaskRepository(db.taskDao(), ticking)
        val id = ticker.add("t", inbox, null, null, 4)
        ticker.setDone(id, true)
        millis = 9_000L
        ticker.setDone(id, true)
        assertEquals(1_000L, ticker.observeTask(id).first()!!.completedAt)
    }

    @Test fun siblingsAreAppendedInOrder() = runBlocking {
        val heading = lists.createHeading(inbox, "H")
        val a = add("a")
        val b = add("b")
        val c = add("c")
        val h1 = repo.add("h1", inbox, null, null, 4, headingId = heading)
        val h2 = repo.add("h2", inbox, null, null, 4, headingId = heading)
        val positions = listOf(a, b, c, h1, h2).map { repo.observeTask(it).first()!!.position }
        assertEquals(listOf(0, 1, 2, 0, 1), positions)
    }

    @Test fun doneTasksAreAbsentFromDayUpcomingAndDots() = runBlocking {
        val ids = listOf(
            add("overdue", today.minusDays(1)),
            add("timed", today, 60),
            add("anytime", today),
            add("tomorrow", today.plusDays(1)),
            add("later", today.plusDays(4)),
        )
        ids.forEach { repo.setDone(it, true) }
        val day = repo.observeDay(today).first()
        assertTrue(day.overdue.isEmpty())
        assertTrue(day.timed.isEmpty())
        assertTrue(day.anytime.isEmpty())
        assertTrue(day.tomorrow.isEmpty())
        assertTrue(repo.observeUpcoming().first().isEmpty())
        assertTrue(repo.observeDots(today.minusDays(2), today.plusDays(6)).first().isEmpty())
    }

    /**
     * A repeat has to be removable, or a mistyped "kazdy den" could only be undone by deleting
     * the task. `updateEditableAndFollow` writes a fixed set of columns, so `recurrence` has to
     * be one of them.
     */
    @Test fun addStoresARepeatAndUpdateCanClearIt() = runBlocking {
        val id = repo.add("cvicit", inbox, today, 8 * 60, 4, recurrence = "FREQ=DAILY")
        assertEquals("FREQ=DAILY", repo.getTask(id)!!.recurrence)
        repo.update(repo.getTask(id)!!.copy(recurrence = null))
        assertNull(repo.getTask(id)!!.recurrence)
    }

    @Test fun searchTreatsPercentAndUnderscoreLiterally() = runBlocking {
        val percent = add("100% sure")
        val underscore = add("a_b")
        add("axb")
        add("plain")
        assertEquals(listOf(percent), repo.search("%").first().map { it.task.id })
        assertEquals(listOf(underscore), repo.search("a_b").first().map { it.task.id })
        assertEquals(listOf(underscore), repo.search("_").first().map { it.task.id })
    }

    // ---- Phase 3: hiding repeats ----

    private suspend fun addRepeating(
        title: String,
        due: LocalDate? = null,
        minute: Int? = null,
        rule: String = "FREQ=DAILY",
    ) = repo.add(title, inbox, due, minute, 4, recurrence = rule)

    @Test fun withRepeatsShownNothingMoves() = runBlocking {
        val repeating = addRepeating("vynest kose", today, 9 * 60)
        val plain = add("zavolat", today, 15 * 60)

        val day = repo.observeDay(today, hideRepeats = false).first()

        assertEquals(listOf(repeating, plain), day.timed.map { it.task.id })
        assertEquals(emptyList<Long>(), day.hiddenRepeats.map { it.task.id })
    }

    @Test fun aTimedRepeatMovesToHiddenRepeats() = runBlocking {
        val repeating = addRepeating("vynest kose", today, 9 * 60)
        val plain = add("zavolat", today, 15 * 60)

        val day = repo.observeDay(today, hideRepeats = true).first()

        assertEquals(listOf(plain), day.timed.map { it.task.id })
        assertEquals(listOf(repeating), day.hiddenRepeats.map { it.task.id })
    }

    @Test fun anAnytimeRepeatMovesToHiddenRepeats() = runBlocking {
        val repeating = addRepeating("protahnout se", today)
        val plain = add("koupit kabat", today)

        val day = repo.observeDay(today, hideRepeats = true).first()

        assertEquals(listOf(plain), day.anytime.map { it.task.id })
        assertEquals(listOf(repeating), day.hiddenRepeats.map { it.task.id })
    }

    /** From docs/design.md: something already missed is what hiding must not swallow. */
    @Test fun anOverdueRepeatStaysWhereItIs() = runBlocking {
        val overdue = addRepeating("napsat na ekonomku", today.minusDays(1), 9 * 60)

        val day = repo.observeDay(today, hideRepeats = true).first()

        assertEquals(listOf(overdue), day.overdue.map { it.task.id })
        assertEquals(emptyList<Long>(), day.hiddenRepeats.map { it.task.id })
    }

    /** One line standing for a whole day: filtering its count would disagree with the day itself. */
    @Test fun tomorrowKeepsCountingRepeats() = runBlocking {
        val repeating = addRepeating("vynest kose", today.plusDays(1), 9 * 60)

        val day = repo.observeDay(today, hideRepeats = true).first()

        assertEquals(listOf(repeating), day.tomorrow.map { it.task.id })
    }

    @Test fun hiddenRepeatsKeepTheOrderTheyHadOnTheDay() = runBlocking {
        val three = addRepeating("tri", today, 15 * 60)
        val nine = addRepeating("devet", today, 9 * 60)
        val whenever = addRepeating("kdykoli", today)

        val day = repo.observeDay(today, hideRepeats = true).first()

        assertEquals(listOf(nine, three, whenever), day.hiddenRepeats.map { it.task.id })
    }

    @Test fun upcomingMovesRepeatsPerDay() = runBlocking {
        val repeatTomorrow = addRepeating("vynest kose", today.plusDays(1), 9 * 60)
        val plainTomorrow = add("zavolat", today.plusDays(1), 15 * 60)
        val plainLater = add("koupit kabat", today.plusDays(3))

        val days = repo.observeUpcoming(hideRepeats = true).first()

        assertEquals(listOf(plainTomorrow), days.first().tasks.map { it.task.id })
        assertEquals(listOf(repeatTomorrow), days.first().hiddenRepeats.map { it.task.id })
        assertEquals(listOf(plainLater), days.last().tasks.map { it.task.id })
        assertEquals(emptyList<Long>(), days.last().hiddenRepeats.map { it.task.id })
    }

    /** Review Focus 4: the day must not look empty while the folded row says two are hidden. */
    @Test fun aDayWhoseOnlyTasksAreRepeatsHasEmptySectionsAndHiddenOnes() = runBlocking {
        addRepeating("vynest kose", today, 9 * 60)
        addRepeating("protahnout se", today)

        val day = repo.observeDay(today, hideRepeats = true).first()

        assertTrue(day.timed.isEmpty())
        assertTrue(day.anytime.isEmpty())
        assertEquals(2, day.hiddenRepeats.size)
    }
}

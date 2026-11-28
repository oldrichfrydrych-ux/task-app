package cz.olda.tasks.platform

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.olda.tasks.R
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.reminder.Ring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Records what would have been booked, instead of waiting on a real alarm. */
internal class Bookings : AlarmBooking {
    val booked = mutableListOf<Ring>()

    @Volatile
    var cancelled = 0

    override fun book(ring: Ring) {
        synchronized(booked) { booked += ring }
    }

    override fun cancel() {
        cancelled++
    }

    val last: Ring? get() = synchronized(booked) { booked.lastOrNull() }
    val count: Int get() = synchronized(booked) { booked.size }
}

/**
 * Waits for something another thread will do. Room's flows emit on Room's own executor, not on a
 * dispatcher a test can advance, so a test that collects one has to wait for it rather than drive
 * it — `StandardTestDispatcher` and `runCurrent` see none of those emissions.
 */
internal fun waitFor(what: String, timeoutMs: Long = 3_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(10)
    }
    fail("$what did not happen within ${timeoutMs}ms")
}

/**
 * The alarm follows the data: every write to the tasks table re-emits the ring worth booking, so
 * nothing has to tell the scheduler that a task was edited.
 */
@RunWith(AndroidJUnit4::class)
class ReminderSyncTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var bookings: Bookings
    private lateinit var scope: CoroutineScope
    private var inbox = 0L

    private val zone = ZoneId.of("Europe/Prague")
    private val today = LocalDate.of(2026, 12, 2)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    private fun at(hour: Int) = today.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        repo = TaskRepository(db.taskDao(), clock)
        inbox = ListRepository(db.listDao(), db.groupDao(), db.headingDao()).inboxId()
        bookings = Bookings()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun close() {
        scope.cancel()
        db.close()
    }

    private fun sync() = ReminderSync(scope, repo, bookings)

    private suspend fun add(title: String, hour: Int) = repo.add(title, inbox, today, hour * 60, 4)

    @Test
    fun startingBooksTheNextRing() = runBlocking {
        val id = add("zavolat", 15)

        sync().start()

        waitFor("the first booking") { bookings.last != null }
        assertEquals(Ring(at(15), listOf(id)), bookings.last)
    }

    @Test
    fun addingAnEarlierTaskRebooksTheAlarm() = runBlocking {
        add("zavolat", 15)
        sync().start()
        waitFor("the first booking") { bookings.last != null }

        val earlier = add("ekonomka", 12)

        waitFor("the re-booking") { bookings.last?.atMillis == at(12) }
        assertEquals(Ring(at(12), listOf(earlier)), bookings.last)
    }

    @Test
    fun completingTheLastDatedTaskCancelsTheAlarm() = runBlocking {
        val id = add("zavolat", 15)
        sync().start()
        waitFor("the first booking") { bookings.last != null }

        repo.setDone(id, true)

        waitFor("the cancel") { bookings.cancelled > 0 }
        assertTrue(bookings.cancelled > 0)
    }

    @Test
    fun rearmNowBooksWithoutCollecting() = runBlocking {
        val id = add("zavolat", 15)

        sync().rearmNow()

        assertEquals(Ring(at(15), listOf(id)), bookings.last)
    }

    @Test
    fun rearmNowCancelsWhenNothingIsComing() = runBlocking {
        sync().rearmNow()

        assertNull(bookings.last)
        assertTrue(bookings.cancelled > 0)
    }

    @Test
    fun startingTwiceCollectsOnce() = runBlocking {
        add("zavolat", 15)
        val sync = sync()

        sync.start()
        sync.start()

        waitFor("the first booking") { bookings.last != null }
        Thread.sleep(200) // Long enough for a second collector to have booked as well.
        assertEquals("one booking, not two", 1, bookings.count)
    }
}

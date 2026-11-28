package cz.olda.tasks.platform

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.olda.tasks.R
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** One posted reminder, as the recorder sees it. */
internal data class Posted(val taskId: Long, val title: String, val listName: String)

/** Records what would have been put in front of Olda. */
internal class Posts : ReminderNotifier {
    val posted = mutableListOf<Posted>()
    val summaries = mutableListOf<Int>()
    val cancelled = mutableListOf<Long>()

    override fun post(taskId: Long, title: String, listName: String) {
        posted += Posted(taskId, title, listName)
    }

    override fun postSummary(count: Int) {
        summaries += count
    }

    override fun cancel(taskId: Long) {
        cancelled += taskId
    }
}

/**
 * What happens when the alarm fires. Driven directly rather than by booking a real alarm and
 * waiting for the clock, which is what keeps this a four-second test.
 */
@RunWith(AndroidJUnit4::class)
class ReminderReceiverTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var posts: Posts
    private lateinit var bookings: Bookings
    private lateinit var scope: CoroutineScope
    private var inbox = 0L
    private var prace = 0L

    private val zone = ZoneId.of("Europe/Prague")
    private val today = LocalDate.of(2026, 12, 2)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    @Before
    fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        repo = TaskRepository(db.taskDao(), clock)
        val lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
        inbox = lists.inboxId()
        prace = lists.createList("🌊Práce")
        posts = Posts()
        bookings = Bookings()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun close() {
        scope.cancel()
        db.close()
    }

    private fun delivery() = ReminderDelivery(repo, posts, ReminderSync(scope, repo, bookings))

    private suspend fun add(title: String, hour: Int? = null, listId: Long = inbox) =
        repo.add(title, listId, today, hour?.times(60), 4)

    @Test
    fun oneTaskInTheRingPostsOneNotification() = runBlocking {
        val id = add("zavolat na urad", hour = 15, listId = prace)

        delivery().fire(listOf(id))

        assertEquals(listOf(Posted(id, "zavolat na urad", "🌊Práce")), posts.posted)
        assertEquals("one task needs no summary", emptyList<Int>(), posts.summaries)
    }

    /** Six untimed tasks share 08:00; every one of them has to arrive. */
    @Test
    fun threeTasksInTheRingPostThreeNotificationsAndASummary() = runBlocking {
        val first = add("kabat")
        val second = add("ekonomka")
        val third = add("martina")

        delivery().fire(listOf(first, second, third))

        assertEquals(listOf(first, second, third), posts.posted.map { it.taskId })
        assertEquals(listOf(3), posts.summaries)
    }

    @Test
    fun aTaskCompletedBeforeTheAlarmFiredPostsNothing() = runBlocking {
        val id = add("zavolat", hour = 15)
        repo.setDone(id, true)

        delivery().fire(listOf(id))

        assertEquals(emptyList<Posted>(), posts.posted)
    }

    @Test
    fun aTaskDeletedBeforeTheAlarmFiredPostsNothing() = runBlocking {
        val id = add("zavolat", hour = 15)
        repo.softDelete(id)

        delivery().fire(listOf(id))

        assertEquals(emptyList<Posted>(), posts.posted)
    }

    @Test
    fun aTaskThatWentAwayDoesNotStopTheOthers() = runBlocking {
        val gone = add("zmizelo", hour = 15)
        val alive = add("zustalo", hour = 15)
        repo.softDelete(gone)

        delivery().fire(listOf(gone, alive))

        assertEquals(listOf(alive), posts.posted.map { it.taskId })
        assertEquals("one real post needs no summary", emptyList<Int>(), posts.summaries)
    }

    /**
     * The clock here is 15:00, the minute the alarm fired: `nextRing` leaves out a ring that is not
     * strictly in the future, so the 15:00 task it just posted cannot book itself again.
     */
    @Test
    fun firingBooksTheRingAfterIt() = runBlocking {
        val now = add("zavolat", hour = 15)
        val later = add("vecer", hour = 19)
        val atFifteen = Clock.fixed(today.atTime(15, 0).atZone(zone).toInstant(), zone)
        val repoThen = TaskRepository(db.taskDao(), atFifteen)

        ReminderDelivery(repoThen, posts, ReminderSync(scope, repoThen, bookings)).fire(listOf(now))

        assertEquals(
            today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli(),
            bookings.last?.atMillis,
        )
        assertEquals(listOf(later), bookings.last?.taskIds)
    }

    @Test
    fun anEmptyRingPostsNothingAndStillReArms() = runBlocking {
        add("vecer", hour = 19)

        delivery().fire(emptyList())

        assertEquals(emptyList<Posted>(), posts.posted)
        assertEquals(
            today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli(),
            bookings.last?.atMillis,
        )
    }
}

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * `tags` and `task_tags` have existed since Phase 1 and nothing ever used them. Phase 2's
 * `@telefon` is what finally does.
 */
@RunWith(AndroidJUnit4::class)
class TagRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var tags: TagRepository
    private lateinit var tasks: TaskRepository
    private var taskId = 0L
    private var otherTaskId = 0L

    private val zone = ZoneId.of("Europe/Prague")
    private val today = LocalDate.of(2026, 11, 27)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    @Before fun open() = runBlocking<Unit> {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        tags = TagRepository(db.tagDao())
        tasks = TaskRepository(db.taskDao(), clock)
        val lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao(), clock)
        val inbox = lists.inboxId()
        taskId = tasks.add("zavolat", inbox, null, null, 4)
        otherTaskId = tasks.add("napsat", inbox, null, null, 4)
    }

    @After fun close() = db.close()

    @Test fun attachCreatesATagThatDidNotExist() = runBlocking {
        tags.attach(taskId, listOf("telefon"))
        val all = tags.observeTags().first()
        assertEquals(1, all.size)
        assertEquals("telefon", all.single().name)
        assertEquals("#5B6575", all.single().colorHex)
        assertEquals(listOf("telefon"), tags.observeTagsOf(taskId).first().map { it.name })
    }

    @Test fun attachReusesATagIgnoringCaseAndDiacritics() = runBlocking {
        tags.attach(taskId, listOf("Telefon"))
        tags.attach(otherTaskId, listOf("telefón"))
        assertEquals(1, tags.observeTags().first().size)
        assertEquals(1, tags.observeTagsOf(otherTaskId).first().size)
    }

    /** Review Focus 1: task_tags is keyed on (taskId, tagId), so a second insert would throw. */
    @Test fun theSameNameTwiceInOneCallAttachesOnce() = runBlocking {
        tags.attach(taskId, listOf("telefon", "telefon"))
        assertEquals(1, tags.observeTags().first().size)
        assertEquals(1, tags.observeTagsOf(taskId).first().size)
    }

    @Test fun attachingATagTheTaskAlreadyHasIsHarmless() = runBlocking {
        tags.attach(taskId, listOf("telefon"))
        tags.attach(taskId, listOf("telefon"))
        assertEquals(1, tags.observeTagsOf(taskId).first().size)
    }

    @Test fun observeTagsOfReturnsOnlyThatTasksTags() = runBlocking {
        tags.attach(taskId, listOf("telefon", "rychle"))
        tags.attach(otherTaskId, listOf("email"))
        assertEquals(listOf("rychle", "telefon"), tags.observeTagsOf(taskId).first().map { it.name })
        assertEquals(listOf("email"), tags.observeTagsOf(otherTaskId).first().map { it.name })
    }

    @Test fun detachRemovesTheLinkAndKeepsTheTag() = runBlocking {
        tags.attach(taskId, listOf("telefon", "rychle"))
        val telefon = tags.observeTags().first().single { it.name == "telefon" }
        tags.detach(taskId, telefon.id)
        assertEquals(listOf("rychle"), tags.observeTagsOf(taskId).first().map { it.name })
        assertEquals(2, tags.observeTags().first().size)
    }

    @Test fun anEmptyAttachChangesNothing() = runBlocking {
        tags.attach(taskId, emptyList())
        assertEquals(0, tags.observeTags().first().size)
    }
}

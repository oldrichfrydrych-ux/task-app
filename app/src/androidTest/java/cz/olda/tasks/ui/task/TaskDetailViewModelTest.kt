package cz.olda.tasks.ui.task

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import cz.olda.tasks.R
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TagRepository
import cz.olda.tasks.data.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Clock

class TaskDetailViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var lists: ListRepository
    private lateinit var tags: TagRepository
    private var inbox = 0L

    @Before fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        repo = TaskRepository(db.taskDao(), Clock.systemDefaultZone())
        lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
        tags = TagRepository(db.tagDao())
        inbox = lists.inboxId()
    }

    @After fun close() { db.close() }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /** Counts every UPDATE statement that touches a row of `tasks`, from now on. */
    private fun startCountingTaskUpdates() {
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("CREATE TABLE update_log (n INTEGER)")
        sqlite.execSQL("CREATE TRIGGER log_task_updates AFTER UPDATE ON tasks BEGIN INSERT INTO update_log VALUES (1); END")
    }

    private fun countedUpdates(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM update_log").use { it.moveToFirst(); it.getInt(0) }

    private fun awaitStored(id: Long, what: String, check: (cz.olda.tasks.data.entity.TaskEntity) -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (runBlocking { repo.getTask(id) }?.let(check) == true) return
            Thread.sleep(25)
        }
        fail("$what was never stored")
    }

    @Test fun typingANoteCharacterByCharacterIsOneWrite() {
        val id = runBlocking { repo.add("Task", inbox, null, null, 4) }
        val vm = TaskDetailViewModel(id, repo, lists, tags)
        startCountingTaskUpdates()
        val final = "x".repeat(40)
        instrumentation.runOnMainSync { (1..40).forEach { vm.setNotes("x".repeat(it)) } }
        awaitStored(id, "the note") { it.notes == final }
        // Let any straggling write land before counting; a debounce that works has none.
        Thread.sleep(600)
        assertEquals("one transaction for 40 keystrokes", 1, countedUpdates())
        assertEquals(final, runBlocking { repo.getTask(id) }!!.notes)
    }

    @Test fun titleAndNotesTypedTogetherAreBothKept() {
        val id = runBlocking { repo.add("Old", inbox, null, null, 4) }
        val vm = TaskDetailViewModel(id, repo, lists, tags)
        instrumentation.runOnMainSync {
            vm.setTitle("New title")
            vm.setNotes("New notes")
        }
        awaitStored(id, "title and notes") { it.title == "New title" && it.notes == "New notes" }
    }

    @Test fun leavingTheScreenMidPauseStillWritesTheLastKeystrokes() {
        val id = runBlocking { repo.add("Old", inbox, null, null, 4) }
        val store = ViewModelStore()
        val vm = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: java.lang.Class<T>): T =
                    TaskDetailViewModel(id, repo, lists, tags) as T
            },
        )[TaskDetailViewModel::class.java]
        instrumentation.runOnMainSync {
            vm.setTitle("Typed just before leaving")
            store.clear()
        }
        awaitStored(id, "the title typed just before leaving") { it.title == "Typed just before leaving" }
    }
}

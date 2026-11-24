package cz.olda.tasks.ui.lists

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import cz.olda.tasks.R
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Clock

class ListsViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var lists: ListRepository
    private lateinit var viewModel: ListsViewModel

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
        viewModel = ListsViewModel(lists, TaskRepository(db.taskDao(), Clock.systemDefaultZone()))
    }

    @After fun close() { db.close() }

    private fun colourOf(id: Long) = runBlocking { lists.observeLists().first().single { it.id == id }.colorHex }

    private fun awaitColour(id: Long, hex: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (colourOf(id) == hex) return
            Thread.sleep(25)
        }
        fail("colour never became $hex")
    }

    @Test fun aFailingWriteDoesNotCrashAndLaterWritesStillWork() {
        val id = runBlocking { lists.createList("Work", "#112233") }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // The repository rejects a colour that cannot be parsed by throwing; unguarded, that kills the process.
        instrumentation.runOnMainSync { viewModel.recolourList(id, "not a colour") }
        // Control: the ViewModel is alive afterwards and a valid write goes through.
        instrumentation.runOnMainSync { viewModel.recolourList(id, "#445566") }
        awaitColour(id, "#445566")
        assertEquals("#445566", colourOf(id))
    }
}

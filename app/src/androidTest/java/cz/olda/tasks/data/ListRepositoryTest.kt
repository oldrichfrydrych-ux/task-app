package cz.olda.tasks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.olda.tasks.R
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.ui.theme.ListPalette
import cz.olda.tasks.ui.theme.toHex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: ListRepository

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        repo = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
    }

    @After fun close() { db.close() }

    private fun insertTask(listId: Long, headingId: Long? = null): Long {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO tasks (title, notes, titleNormalised, notesNormalised, listId, headingId, priority, done, position, updatedAt) " +
                "VALUES ('t', '', 't', '', ?, ?, 4, 0, 0, 0)",
            arrayOf<Any?>(listId, headingId),
        )
        return db.openHelper.readableDatabase.query("SELECT last_insert_rowid()").use { it.moveToFirst(); it.getLong(0) }
    }

    private fun taskRow(id: Long): Triple<Long, Long?, Long?> =
        db.openHelper.readableDatabase.query("SELECT listId, headingId, deletedAt FROM tasks WHERE id = $id").use {
            it.moveToFirst()
            Triple(
                it.getLong(0),
                if (it.isNull(1)) null else it.getLong(1),
                if (it.isNull(2)) null else it.getLong(2),
            )
        }

    @Test fun firstRunSeedsExactlyOneInbox() = runBlocking {
        val lists = repo.observeLists().first()
        assertEquals(1, lists.size)
        assertTrue(lists[0].isInbox)
        assertEquals(context.getString(R.string.inbox), lists[0].name)
    }

    @Test fun inboxCannotBeDeleted() = runBlocking {
        repo.deleteList(repo.inboxId())
        assertEquals(1, repo.observeLists().first().count { it.isInbox })
    }

    @Test fun deletingAListMovesItsTasksToInbox() = runBlocking {
        val listId = repo.createList("Work", "#112233", null)
        val a = insertTask(listId)
        val b = insertTask(listId)
        repo.deleteList(listId)
        val inbox = repo.inboxId()
        listOf(a, b).forEach {
            val (list, _, deletedAt) = taskRow(it)
            assertEquals(inbox, list)
            assertNull(deletedAt)
        }
        assertTrue(repo.observeLists().first().none { it.id == listId })
    }

    @Test fun deletingAGroupKeepsItsLists() = runBlocking {
        val groupId = repo.createGroup("G")
        val listId = repo.createList("L", "#112233", groupId)
        repo.deleteGroup(groupId)
        val list = repo.observeLists().first().single { it.id == listId }
        assertNull(list.groupId)
    }

    @Test fun deletingAHeadingKeepsItsTasks() = runBlocking {
        val listId = repo.createList("L", "#112233", null)
        val headingId = repo.createHeading(listId, "H")
        val task = insertTask(listId, headingId)
        repo.deleteHeading(headingId)
        assertNull(taskRow(task).second)
        assertEquals(listId, taskRow(task).first)
        assertTrue(repo.observeHeadings(listId).first().isEmpty())
    }

    @Test fun newListTakesTheNextPresetColour() = runBlocking {
        // A blank colour means "pick the next preset".
        val first = repo.createList("L", "", null)
        val second = repo.createList("M", "", null)
        val lists: List<ListEntity> = repo.observeLists().first()
        assertEquals(ListPalette[1].light.toHex(), lists.single { it.id == first }.colorHex)
        assertEquals(ListPalette[2].light.toHex(), lists.single { it.id == second }.colorHex)
        assertEquals(ListPalette[0].light.toHex(), lists.single { it.isInbox }.colorHex)
    }
}

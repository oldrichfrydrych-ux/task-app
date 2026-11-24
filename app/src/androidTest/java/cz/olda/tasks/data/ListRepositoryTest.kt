package cz.olda.tasks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.olda.tasks.R
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.data.entity.TaskEntity
import cz.olda.tasks.ui.theme.ListPalette
import cz.olda.tasks.ui.theme.toHex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    private fun insertTask(listId: Long, headingId: Long? = null): Long = runBlocking {
        db.taskDao().insert(
            TaskEntity(title = "t", titleNormalised = "t", notesNormalised = "", listId = listId, headingId = headingId),
        )
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

    @Test fun aListMovesIntoAGroupAndOutAgain() = runBlocking {
        val group = repo.createGroup("G")
        val list = repo.createList("L")
        repo.moveListToGroup(list, group)
        assertEquals(group, repo.observeLists().first().single { it.id == list }.groupId)
        repo.moveListToGroup(list, null)
        assertNull(repo.observeLists().first().single { it.id == list }.groupId)
    }

    @Test fun inboxCannotBeMovedIntoAGroup() = runBlocking {
        val group = repo.createGroup("G")
        repo.moveListToGroup(repo.inboxId(), group)
        assertNull(repo.observeLists().first().single { it.isInbox }.groupId)
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
        // A null colour means "pick the next preset".
        val first = repo.createList("L", null, null)
        val second = repo.createList("M", null, null)
        val lists: List<ListEntity> = repo.observeLists().first()
        assertEquals(ListPalette[1].light.toHex(), lists.single { it.id == first }.colorHex)
        assertEquals(ListPalette[2].light.toHex(), lists.single { it.id == second }.colorHex)
        assertEquals(ListPalette[0].light.toHex(), lists.single { it.isInbox }.colorHex)
    }

    @Test fun explicitColourIsStoredAsGiven() = runBlocking {
        val id = repo.createList("L", "#ABCDEF", null)
        assertEquals("#ABCDEF", repo.observeLists().first().single { it.id == id }.colorHex)
    }

    @Test fun recolourRejectsBlankColour() = runBlocking {
        val id = repo.createList("L", "#112233", null)
        try {
            repo.recolourList(id, "")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals("#112233", repo.observeLists().first().single { it.id == id }.colorHex)
    }

    @Test fun deletingAListLeavesSoftDeletedTasksAlone() = runBlocking {
        val listId = repo.createList("L", null, null)
        val live = insertTask(listId)
        val trashed = insertTask(listId)
        db.openHelper.writableDatabase.execSQL("UPDATE tasks SET deletedAt = 5 WHERE id = $trashed")
        repo.deleteList(listId)
        assertEquals(repo.inboxId(), taskRow(live).first)
        assertEquals(listId, taskRow(trashed).first)
        assertEquals(5L, taskRow(trashed).third)
    }

    @Test fun deletingAListRemovesItsHeadings() = runBlocking {
        val listId = repo.createList("L", null, null)
        repo.createHeading(listId, "H")
        repo.deleteList(listId)
        assertTrue(repo.observeHeadings(listId).first().isEmpty())
    }

    @Test fun groupsWithListsIncludesEmptyGroupsAndUngroupedEntry() = runBlocking {
        val full = repo.createGroup("Full")
        val empty = repo.createGroup("Empty")
        val inGroup = repo.createList("In", null, full)
        val loose = repo.createList("Loose", null, null)
        val result = repo.observeGroupsWithLists().first()
        assertNull(result[0].group)
        assertEquals(setOf(repo.inboxId(), loose), result[0].lists.map { it.id }.toSet())
        assertEquals(listOf(full, empty), result.drop(1).map { it.group!!.id })
        assertEquals(listOf(inGroup), result[1].lists.map { it.id })
        assertTrue(result[2].lists.isEmpty())
    }

    private fun listRow(id: Long): Pair<Long?, Long> =
        db.openHelper.readableDatabase.query("SELECT deletedAt, updatedAt FROM lists WHERE id = $id").use {
            assertTrue("row must still exist", it.moveToFirst())
            (if (it.isNull(0)) null else it.getLong(0)) to it.getLong(1)
        }

    @Test fun deletingAListKeepsTheRowAsATombstone() = runBlocking {
        val clock = java.time.Clock.fixed(java.time.Instant.ofEpochMilli(777), java.time.ZoneOffset.UTC)
        val stamped = ListRepository(db.listDao(), db.groupDao(), db.headingDao(), clock)
        val listId = stamped.createList("Work", "#112233", null)
        assertNull(listRow(listId).first)
        stamped.deleteList(listId)
        assertEquals(777L to 777L, listRow(listId))
        assertTrue(stamped.observeLists().first().none { it.id == listId })
        // A tombstone is never renamed, regrouped or deleted again.
        val later = ListRepository(db.listDao(), db.groupDao(), db.headingDao(), java.time.Clock.fixed(java.time.Instant.ofEpochMilli(999), java.time.ZoneOffset.UTC))
        later.renameList(listId, "Zombie")
        later.deleteList(listId)
        assertEquals(777L to 777L, listRow(listId))
    }

    @Test fun renameRecolourAndMoveStampUpdatedAt() = runBlocking {
        fun at(ms: Long) = ListRepository(db.listDao(), db.groupDao(), db.headingDao(), java.time.Clock.fixed(java.time.Instant.ofEpochMilli(ms), java.time.ZoneOffset.UTC))
        val id = at(10).createList("L", "#112233", null)
        assertEquals(10L, listRow(id).second)
        at(20).renameList(id, "M")
        assertEquals(20L, listRow(id).second)
        at(30).recolourList(id, "#445566")
        assertEquals(30L, listRow(id).second)
        at(40).moveListToGroup(id, null)
        assertEquals(40L, listRow(id).second)
    }
}

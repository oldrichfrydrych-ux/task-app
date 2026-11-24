package cz.olda.tasks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

class AppContainerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "app-container-test.db"
    private var container: AppContainer? = null

    @Before
    fun freshDatabase() {
        context.deleteDatabase(dbName)
    }

    @After
    fun cleanUp() {
        container?.database?.close()
        context.deleteDatabase(dbName)
    }

    /** Without the seed callback the shipped app starts with no Inbox and nothing else notices. */
    @Test
    fun aFreshDatabaseHasExactlyOneInbox() = runBlocking {
        val c = AppContainer(context, dbName = dbName).also { container = it }
        val lists = c.listRepository.observeLists().first()
        assertEquals(listOf(context.getString(R.string.inbox)), lists.map { it.name })
    }

    /**
     * The name was the only thing asserted here, so the seed could have dropped `isInbox`
     * - the flag that makes Inbox undeletable and unmovable - and every test in the suite
     * would still have passed. Asserts every column the seed writes, because the insert
     * is hand-written SQL that no entity shape checks.
     */
    @Test
    fun theSeededInboxCarriesEveryColumnTheAppReliesOn() = runBlocking {
        val c = AppContainer(context, dbName = dbName).also { container = it }
        val inbox = c.listRepository.observeLists().first().single()

        assertEquals(context.getString(R.string.inbox), inbox.name)
        assertTrue("isInbox unset would let Inbox be deleted or moved", inbox.isInbox)
        assertEquals("Inbox sorts first", 0, inbox.position)
        assertEquals(ListPalette[0].light.toHex(), inbox.colorHex)
        assertEquals("Phase 4 treats 0 as never synced", 0L, inbox.updatedAt)
        assertNull("Inbox belongs to no group", inbox.groupId)
        assertNull("a seeded row is not deleted", inbox.deletedAt)
        assertNull(inbox.icon)
        assertNull(inbox.googleListId)
    }
}

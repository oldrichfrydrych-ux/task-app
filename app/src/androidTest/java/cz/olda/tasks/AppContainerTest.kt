package cz.olda.tasks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
}

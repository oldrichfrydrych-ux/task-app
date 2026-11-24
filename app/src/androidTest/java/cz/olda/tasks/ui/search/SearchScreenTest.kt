package cz.olda.tasks.ui.search

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cz.olda.tasks.R
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.ui.LocalSnackbarHostState
import cz.olda.tasks.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class SearchScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var lists: ListRepository
    private var inbox = 0L
    private var opened: Long? = null
    private var backs = 0

    private val zone = ZoneId.of("Europe/Prague")
    private val clock = Clock.fixed(LocalDate.of(2026, 11, 23).atTime(10, 0).atZone(zone).toInstant(), zone)

    @Before fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        repo = TaskRepository(db.taskDao(), clock)
        lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
        inbox = lists.inboxId()
    }

    @After fun close() { db.close() }

    private fun show() {
        val viewModel = SearchViewModel(repo)
        rule.setContent {
            AppTheme {
                val hostState = remember { SnackbarHostState() }
                CompositionLocalProvider(LocalSnackbarHostState provides hostState) {
                    Scaffold(snackbarHost = { SnackbarHost(hostState) }) { padding ->
                        Box(Modifier.padding(padding)) {
                            SearchScreen(viewModel = viewModel, onOpenTask = { opened = it }, onBack = { backs++ })
                        }
                    }
                }
            }
        }
        // The field is there as soon as the screen is.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(hasTestTag("search-field")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun type(text: String) = rule.onNodeWithTag("search-field").performTextInput(text)

    private fun awaitShown(text: String) = rule.waitUntil(timeoutMillis = 10_000) {
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitGone(text: String) = rule.waitUntil(timeoutMillis = 10_000) {
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
    }

    @Test fun findsByTitle() {
        runBlocking {
            repo.add("Buy milk", inbox, null, null, 4)
            repo.add("Walk the dog", inbox, null, null, 4)
        }
        show()
        type("milk")
        awaitShown("Buy milk")
        rule.onNodeWithText("Walk the dog").assertDoesNotExist()
    }

    @Test fun findsByNotes() {
        runBlocking {
            val id = repo.add("Call the plumber", inbox, null, null, 4)
            repo.add("Walk the dog", inbox, null, null, 4)
            val task = repo.observeTask(id).first()!!
            repo.update(task.copy(notes = "the tap in the kitchen drips"))
        }
        show()
        type("kitchen")
        awaitShown("Call the plumber")
        rule.onNodeWithText("Walk the dog").assertDoesNotExist()
    }

    // Review Focus 3: Czech typed without diacritics and in capitals.
    @Test fun ignoresCaseAndDiacritics() {
        runBlocking {
            repo.add("Zítra zavolat na úřad", inbox, null, null, 4)
            repo.add("Koupit mléko", inbox, null, null, 4)
        }
        show()
        type("ZITRA ZAVOLAT")
        awaitShown("Zítra zavolat na úřad")
        rule.onNodeWithText("Koupit mléko").assertDoesNotExist()
    }

    @Test fun anEmptyQueryShowsNoResults() {
        runBlocking { repo.add("Buy milk", inbox, null, null, 4) }
        show()
        rule.onNodeWithText("Buy milk").assertDoesNotExist()
        // Control: a query does bring it, and clearing the query takes it away again.
        type("milk")
        awaitShown("Buy milk")
        rule.onNodeWithTag("search-clear").performClick()
        awaitGone("Buy milk")
    }

    @Test fun doneAndDeletedTasksDoNotAppear() {
        runBlocking {
            repo.add("milk open", inbox, null, null, 4)
            val done = repo.add("milk done", inbox, null, null, 4)
            val deleted = repo.add("milk deleted", inbox, null, null, 4)
            repo.setDone(done, true)
            repo.softDelete(deleted)
        }
        show()
        type("milk")
        awaitShown("milk open")
        rule.onNodeWithText("milk done").assertDoesNotExist()
        rule.onNodeWithText("milk deleted").assertDoesNotExist()
    }

    @Test fun tappingAResultOpensTheTask() {
        val id = runBlocking { repo.add("Buy milk", inbox, null, null, 4) }
        show()
        type("milk")
        awaitShown("Buy milk")
        rule.onNodeWithText("Buy milk").performClick()
        assertEquals(id, opened)
    }

    @Test fun completingAResultRemovesItWithAnUndo() {
        val id = runBlocking { repo.add("finish milk", inbox, null, null, 4) }
        show()
        type("milk")
        awaitShown("finish milk")
        rule.onNode(hasContentDescription("Complete task") and hasAnyAncestor(hasTestTag("task-$id")))
            .performClick()
        awaitGone("finish milk")
        rule.onNodeWithText("Undo").assertIsDisplayed().performClick()
        awaitShown("finish milk")
    }

    @Test fun backButtonLeaves() {
        show()
        rule.onNodeWithTag("search-back").performClick()
        assertEquals(1, backs)
    }

    @Test fun aQueryWithNoMatchSaysSo() {
        runBlocking { repo.add("Buy milk", inbox, null, null, 4) }
        show()
        rule.onAllNodes(hasTestTag("search-no-results")).assertCountEquals(0)
        type("zzz")
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(hasTestTag("search-no-results")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

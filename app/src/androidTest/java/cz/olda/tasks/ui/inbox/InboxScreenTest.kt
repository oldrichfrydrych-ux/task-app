package cz.olda.tasks.ui.inbox

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cz.olda.tasks.R
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.ui.LocalSnackbarHostState
import cz.olda.tasks.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class InboxScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var lists: ListRepository
    private var inbox = 0L

    private val zone = ZoneId.of("Europe/Prague")
    private val today = LocalDate.of(2026, 11, 23)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

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

    /** Shows the screen and waits for its first load, so every later assertion sees real data. */
    private fun show() {
        setScreen()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(hasTestTag("inbox-empty")).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasContentDescription("Complete task")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitGone(text: String) = rule.waitUntil(timeoutMillis = 10_000) {
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
    }

    private fun awaitShown(tag: String) = rule.waitUntil(timeoutMillis = 10_000) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun setScreen() {
        val viewModel = InboxViewModel(repo, lists)
        rule.setContent {
            AppTheme {
                val hostState = remember { SnackbarHostState() }
                CompositionLocalProvider(LocalSnackbarHostState provides hostState) {
                    Scaffold(snackbarHost = { SnackbarHost(hostState) }) { padding ->
                        Box(Modifier.padding(padding)) {
                            InboxScreen(viewModel = viewModel, onOpenTask = {})
                        }
                    }
                }
            }
        }
    }

    @Test fun showsOnlyInboxTasks() {
        val other = runBlocking { lists.createList("Work", "#E85A4F") }
        runBlocking {
            repo.add("in the inbox", inbox, null, null, 4)
            repo.add("elsewhere", other, null, null, 4)
        }
        show()
        rule.onNodeWithText("in the inbox").assertIsDisplayed()
        rule.onNodeWithText("elsewhere").assertDoesNotExist()
    }

    @Test fun aDatedInboxTaskShowsItsDayAndAnUndatedOneDoesNot() {
        val dated = runBlocking { repo.add("dated", inbox, today.plusDays(20), null, 4) }
        val undated = runBlocking { repo.add("undated", inbox, null, null, 4) }
        show()
        rule.onNodeWithTag("task-$undated").assertIsDisplayed()
        // The dated row has no time of day, so anything in the time slot is the due day.
        rule.onNodeWithTag("task-time-$dated", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("task-time-$undated", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun stateIsNullBeforeTheFirstEmission() {
        org.junit.Assert.assertNull(InboxViewModel(repo, lists).state.value)
    }

    @Test fun emptyInboxSaysSo() {
        show()
        rule.onNodeWithText("Inbox is empty").assertIsDisplayed()
    }

    @Test fun anInboxWithTasksHasNoEmptyState() {
        runBlocking { repo.add("something", inbox, null, null, 4) }
        show()
        rule.onNodeWithText("something").assertIsDisplayed()
        rule.onNodeWithText("Inbox is empty").assertDoesNotExist()
    }

    @Test fun noEmptyTextBeforeTheFirstEmission() {
        rule.setContent { AppTheme { InboxContent(tasks = null, onToggleDone = {}, onOpenTask = {}) } }
        rule.onNodeWithText("Inbox is empty").assertDoesNotExist()
    }

    @Test fun emptyTextShowsOnceLoadedEmpty() {
        rule.setContent { AppTheme { InboxContent(tasks = emptyList(), onToggleDone = {}, onOpenTask = {}) } }
        rule.onNodeWithText("Inbox is empty").assertIsDisplayed()
    }

    @Test fun completingARowRemovesItWithAnUndo() {
        val id = runBlocking { repo.add("finish me", inbox, null, null, 4) }
        show()
        rule.onNode(hasContentDescription("Complete task") and hasAnyAncestor(hasTestTag("task-$id")))
            .performClick()
        awaitGone("finish me")
        rule.onNodeWithText("finish me").assertDoesNotExist()
        rule.onNodeWithText("Undo").assertIsDisplayed().performClick()
        awaitShown("task-$id")
        rule.onNodeWithTag("task-$id").assertIsDisplayed()
    }
}

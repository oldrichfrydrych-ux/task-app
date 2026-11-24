package cz.olda.tasks.ui.task

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
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class TaskDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var lists: ListRepository
    private var work = 0L
    private var wentBack = false

    private val zone = ZoneId.of("Europe/Prague")
    private val clock = Clock.fixed(LocalDate.of(2026, 11, 23).atTime(10, 0).atZone(zone).toInstant(), zone)

    @Before fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        repo = TaskRepository(db.taskDao(), clock)
        lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
        work = lists.createList("Work", "#E85A4F")
    }

    @After fun close() { db.close() }

    /** Shows the screen and waits for the task to load, so later assertions see real data. */
    private fun show(taskId: Long) {
        val viewModel = TaskDetailViewModel(taskId, repo, lists)
        rule.setContent {
            AppTheme {
                val hostState = remember { SnackbarHostState() }
                CompositionLocalProvider(LocalSnackbarHostState provides hostState) {
                    Scaffold(snackbarHost = { SnackbarHost(hostState) }) { padding ->
                        Box(Modifier.padding(padding)) {
                            TaskDetailScreen(viewModel = viewModel, onBack = { wentBack = true })
                        }
                    }
                }
            }
        }
        awaitShown("task-title-field")
    }

    private fun awaitShown(tag: String) = rule.waitUntil(timeoutMillis = 10_000) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun task(id: Long) = runBlocking { repo.observeTask(id).first() }

    @Test fun showsEveryFieldOfTheTask() {
        val day = LocalDate.of(2026, 12, 24)
        val id = runBlocking { repo.add("Buy gifts", work, day, 14 * 60 + 30, 2) }
        runBlocking { repo.update(repo.observeTask(id).first()!!.copy(notes = "Ask about sizes")) }
        show(id)
        rule.onNodeWithTag("task-title-field").assertTextContains("Buy gifts")
        rule.onNodeWithTag("task-notes-field").assertTextContains("Ask about sizes")
        rule.onNodeWithTag("task-list-picker").assertTextContains("Work")
        rule.onNodeWithTag("task-date").assertTextContains("2026", substring = true)
        rule.onNodeWithTag("task-time").assertTextContains("30", substring = true)
        rule.onNodeWithTag("task-priority-2").assertIsSelected()
        rule.onNodeWithTag("task-priority-4").assertIsDisplayed()
        rule.onNodeWithTag("task-done").assertIsOff()
    }

    @Test fun editingTheTitleSaves() {
        val id = runBlocking { repo.add("Old title", work, null, null, 4) }
        show(id)
        rule.onNodeWithTag("task-title-field").performTextReplacement("New title")
        rule.waitUntil(timeoutMillis = 10_000) { task(id)?.title == "New title" }
        assertEquals("new title", task(id)!!.titleNormalised)
    }

    @Test fun addingASubtaskShowsItUnderTheTask() {
        val id = runBlocking { repo.add("Parent", work, null, null, 4) }
        show(id)
        rule.onNodeWithTag("subtask-field").performTextInput("Child step")
        rule.onNodeWithTag("subtask-add").performClick()
        var sub: Long? = null
        rule.waitUntil(timeoutMillis = 10_000) {
            sub = runBlocking { repo.observeSubtasks(id).first() }.singleOrNull()?.id
            sub != null
        }
        awaitShown("subtask-$sub")
        rule.onNodeWithTag("subtask-title-$sub").assertTextEquals("Child step")
        assertEquals(id, task(sub!!)!!.parentTaskId)
        assertEquals(work, task(sub!!)!!.listId)
    }

    @Test fun completingTheParentChecksItsSubtasks() {
        val id = runBlocking { repo.add("Parent", work, null, null, 4) }
        val sub = runBlocking { repo.add("Child", work, null, null, 4, parentTaskId = id) }
        show(id)
        awaitShown("subtask-done-$sub")
        rule.onNodeWithTag("subtask-done-$sub").assertIsOff()
        rule.onNodeWithTag("task-done").performClick()
        rule.waitUntil(timeoutMillis = 10_000) { task(sub)?.done == true }
        rule.onNodeWithTag("task-done").assertIsOn()
        rule.onNodeWithTag("subtask-done-$sub").assertIsOn()
    }

    @Test fun aParentOffersToAddASubtask() {
        val id = runBlocking { repo.add("Parent", work, null, null, 4) }
        show(id)
        rule.onNodeWithTag("subtask-field").assertIsDisplayed()
        rule.onNodeWithTag("subtask-add").assertIsDisplayed()
    }

    @Test fun aSubtaskCannotTakeASubtask() {
        val id = runBlocking { repo.add("Parent", work, null, null, 4) }
        val sub = runBlocking { repo.add("Child", work, null, null, 4, parentTaskId = id) }
        show(sub)
        // show() waited for the subtask itself to load, so these absences are real; the
        // parent test above is the control that the controls exist at all.
        rule.onNodeWithTag("task-title-field").assertTextContains("Child")
        rule.onAllNodes(hasTestTag("subtask-field")).assertCountEquals(0)
        rule.onAllNodes(hasTestTag("subtask-add")).assertCountEquals(0)
    }

    @Test fun deletingTheTaskGoesBack() {
        val id = runBlocking { repo.add("Doomed", work, null, null, 4) }
        val sub = runBlocking { repo.add("Doomed child", work, null, null, 4, parentTaskId = id) }
        show(id)
        rule.onNodeWithTag("task-delete").performClick()
        rule.onNodeWithTag("delete-confirm").performClick()
        rule.waitUntil(timeoutMillis = 10_000) { wentBack }
        // Let the screen see the deletion too, so no query is still running when the database closes.
        awaitShown("task-not-found")
        assertNull(task(id))
        assertNull(task(sub))
        assertTrue(wentBack)
    }
}

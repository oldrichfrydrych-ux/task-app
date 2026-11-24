package cz.olda.tasks.ui.lists

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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.layout.positionInRoot
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class ListDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var lists: ListRepository
    private var work = 0L

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

    /** Shows the screen and waits for the list itself to load, so later assertions see real data. */
    private fun show() {
        val viewModel = ListDetailViewModel(work, lists, repo)
        rule.setContent {
            AppTheme {
                val hostState = remember { SnackbarHostState() }
                CompositionLocalProvider(LocalSnackbarHostState provides hostState) {
                    Scaffold(snackbarHost = { SnackbarHost(hostState) }) { padding ->
                        Box(Modifier.padding(padding)) {
                            ListDetailScreen(viewModel = viewModel, onOpenTask = {}, onBack = {})
                        }
                    }
                }
            }
        }
        awaitShown("list-title")
    }

    private fun awaitShown(tag: String) = rule.waitUntil(timeoutMillis = 10_000) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitGone(tag: String) = rule.waitUntil(timeoutMillis = 10_000) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    @Test fun tasksWithoutAHeadingComeFirst() {
        val heading = runBlocking { lists.createHeading(work, "Phase one") }
        val under = runBlocking { repo.add("under heading", work, null, null, 4, headingId = heading) }
        val loose = runBlocking { repo.add("no heading", work, null, null, 4) }
        show()
        awaitShown("task-$under")
        awaitShown("task-$loose")
        assert(top("task-$loose") < top("heading-$heading")) { "loose task should sit above the heading" }
        assert(top("heading-$heading") < top("task-$under")) { "heading should sit above its task" }
    }

    @Test fun eachHeadingShowsItsTasks() {
        val a = runBlocking { lists.createHeading(work, "Alpha") }
        val b = runBlocking { lists.createHeading(work, "Beta") }
        val inA = runBlocking { repo.add("in alpha", work, null, null, 4, headingId = a) }
        val inB = runBlocking { repo.add("in beta", work, null, null, 4, headingId = b) }
        show()
        awaitShown("task-$inA")
        awaitShown("task-$inB")
        assert(top("heading-$a") < top("task-$inA") && top("task-$inA") < top("heading-$b")) { "alpha's task is between the headings" }
        assert(top("heading-$b") < top("task-$inB")) { "beta's task is under beta" }
    }

    @Test fun addingAHeadingShowsIt() {
        show()
        rule.onNodeWithTag("add-heading").performClick()
        rule.onNodeWithTag("heading-name-field").performTextInput("Later")
        rule.onNodeWithTag("heading-save").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(hasTestTag("heading-title-Later")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Later").assertIsDisplayed()
        assertEquals(listOf("Later"), runBlocking { lists.observeHeadings(work).first() }.map { it.name })
    }

    @Test fun deletingAHeadingKeepsItsTasksInTheList() {
        val heading = runBlocking { lists.createHeading(work, "Doomed") }
        val task = runBlocking { repo.add("survivor", work, null, null, 4, headingId = heading) }
        show()
        awaitShown("task-$task")
        rule.onNodeWithTag("heading-delete-$heading").performClick()
        rule.onNodeWithTag("delete-confirm").performClick()
        awaitGone("heading-$heading")
        awaitShown("task-$task")
        rule.onNodeWithText("survivor").assertIsDisplayed()
        val inList = runBlocking { repo.observeInList(work).first() }
        assertEquals(listOf("survivor"), inList.map { it.task.title })
        assertNull(inList.single().task.headingId)
    }

    @Test fun stateIsEmptyBeforeTheFirstEmission() {
        assertNull(ListDetailViewModel(work, lists, repo).state.value.list)
    }
}

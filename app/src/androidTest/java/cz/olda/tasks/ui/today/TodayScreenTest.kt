package cz.olda.tasks.ui.today

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class TodayScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
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
        inbox = ListRepository(db.listDao(), db.groupDao(), db.headingDao()).inboxId()
    }

    @After fun close() { db.close() }

    private suspend fun add(title: String, due: LocalDate?, minute: Int? = null) =
        repo.add(title, inbox, due, minute, 4)

    private fun show() {
        val viewModel = TodayViewModel(repo, clock)
        rule.setContent {
            AppTheme {
                val host = SnackbarHostState()
                val hostState = androidx.compose.runtime.remember { host }
                CompositionLocalProvider(LocalSnackbarHostState provides hostState) {
                    Scaffold(snackbarHost = { SnackbarHost(hostState) }) { padding ->
                        Box(Modifier.padding(padding)) {
                            TodayScreen(viewModel = viewModel, onOpenTask = {})
                        }
                    }
                }
            }
        }
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    @Test fun showsOverdueThenTimedThenAnytime() {
        val ids = runBlocking {
            listOf(
                add("anytime one", today),
                add("timed one", today, 9 * 60),
                add("late one", today.minusDays(1)),
            )
        }
        show()
        val (anytime, timed, late) = ids
        rule.onNodeWithTag("task-$late").assertIsDisplayed()
        assertTrue(top("task-$late") < top("task-$timed"))
        assertTrue(top("task-$timed") < top("task-$anytime"))
    }

    @Test fun tappingADayInTheStripShowsThatDay() {
        runBlocking {
            add("today task", today)
            add("late one", today.minusDays(1))
            add("wednesday task", today.plusDays(2))
        }
        show()
        rule.onNodeWithText("today task").assertIsDisplayed()
        rule.onNodeWithTag("day-2026-11-23").assertIsSelected()

        rule.onNodeWithTag("day-2026-11-25").performClick()

        rule.onNodeWithTag("day-2026-11-25").assertIsSelected()
        rule.onNodeWithText("wednesday task").assertIsDisplayed()
        rule.onNodeWithText("late one").assertDoesNotExist()
    }

    @Test fun theTomorrowRowOnlyShowsOnToday() {
        runBlocking { add("for tomorrow", today.plusDays(1)) }
        show()
        rule.onNodeWithText("Tomorrow").assertIsDisplayed()
        rule.onNodeWithText("for tomorrow").assertIsDisplayed()

        rule.onNodeWithTag("day-2026-11-25").performClick()
        rule.onNodeWithText("Tomorrow").assertDoesNotExist()
        rule.onNodeWithText("for tomorrow").assertDoesNotExist()
    }

    @Test fun completingARowRemovesItWithAnUndo() {
        val id = runBlocking { add("finish me", today) }
        show()
        val row = rule.onNodeWithTag("task-$id")
        row.assertIsDisplayed()

        rule.onNode(hasContentDescription("Complete task") and hasAnyAncestor(hasTestTag("task-$id")))
            .performClick()

        rule.onNodeWithText("finish me").assertDoesNotExist()
        rule.onNodeWithText("Task completed").assertIsDisplayed()
        rule.onNodeWithText("Undo").assertIsDisplayed().performClick()

        rule.onNodeWithTag("task-$id").assertIsDisplayed()
    }

    @Test fun undoingACompletedParentReopensItsSubtasks() {
        val parent = runBlocking { add("parent", today) }
        val child = runBlocking { repo.add("child", inbox, today, null, 4, parentTaskId = parent) }
        show()
        rule.onNode(hasContentDescription("Complete task") and hasAnyAncestor(hasTestTag("task-$parent")))
            .performClick()
        rule.onNodeWithTag("task-$child").assertDoesNotExist()

        rule.onNodeWithText("Undo").performClick()

        rule.onNodeWithTag("task-$parent").assertIsDisplayed()
        rule.onNodeWithTag("task-$child").assertIsDisplayed()
    }
}

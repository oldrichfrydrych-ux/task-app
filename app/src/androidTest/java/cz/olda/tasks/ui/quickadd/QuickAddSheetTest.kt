package cz.olda.tasks.ui.quickadd

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cz.olda.tasks.R
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate

class QuickAddSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var lists: ListRepository
    private var work = 0L
    private var inbox = 0L
    private var dismissed = false

    @Before fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        tasks = TaskRepository(db.taskDao(), Clock.systemDefaultZone())
        lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
        work = lists.createList("Work", "#E85A4F")
        inbox = lists.inboxId()
    }

    @After fun close() { db.close() }

    /** Shows the sheet and waits for the lists to load, so later assertions see real data. */
    private fun show() {
        val viewModel = QuickAddViewModel(tasks, lists)
        rule.setContent { AppTheme { QuickAddSheet(onDismiss = { dismissed = true }, viewModel = viewModel) } }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(hasTestTag("chip-list") and hasText("Inbox", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun allTasks() = runBlocking {
        lists.observeLists().first().flatMap { tasks.observeInList(it.id).first() }.map { it.task }
    }

    private fun waitForTag(tag: String) = rule.waitUntil(timeoutMillis = 10_000) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitGone(tag: String) = rule.waitUntil(timeoutMillis = 10_000) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()
    }

    /** Picks the 15th of the month the picker opens on, and returns that date. */
    private fun pickTheFifteenth(): LocalDate {
        rule.onNodeWithTag("chip-date").performClick()
        waitForTag("quick-add-date-save")
        // The day cells read like "Sunday, 15 November 2026".
        rule.onNode(hasText("15", substring = true) and hasClickAction()).performClick()
        rule.onNodeWithTag("quick-add-date-save").performClick()
        waitGone("quick-add-date-save")
        return LocalDate.now().withDayOfMonth(15)
    }

    @Test fun chipsStartAtNoDateInboxAndPriorityFour() {
        show()
        rule.onNodeWithTag("chip-date").assertTextContains("No date")
        rule.onNodeWithTag("chip-list").assertTextContains("Inbox")
        rule.onNodeWithTag("chip-priority").assertTextContains("None", substring = true)
    }

    @Test fun pickingADateShowsItOnTheChip() {
        show()
        pickTheFifteenth()
        // The chip names the day (a localised "15 Nov 2026" or "Nov 15, 2026"), not "No date".
        rule.onNodeWithTag("chip-date").assertTextContains("15", substring = true)
        rule.onAllNodes(hasTestTag("chip-date") and hasText("No date")).assertCountEquals(0)
    }

    @Test fun pickingAListShowsItsNameOnTheChip() {
        show()
        rule.onNodeWithTag("chip-list").performClick()
        waitForTag("quick-add-list-option-$work")
        rule.onNodeWithTag("quick-add-list-option-$work").performClick()
        waitGone("quick-add-list-option-$work")
        rule.onNodeWithTag("chip-list").assertTextContains("Work")
    }

    @Test fun pickingAPriorityShowsItOnTheChip() {
        show()
        rule.onNodeWithTag("chip-priority").performClick()
        waitForTag("quick-add-priority-option-1")
        rule.onNodeWithTag("quick-add-priority-option-1").performClick()
        waitGone("quick-add-priority-option-1")
        rule.onNodeWithTag("chip-priority").assertTextContains("High", substring = true)
    }

    @Test fun savingWritesTheTaskWithItsChips() {
        show()
        rule.onNodeWithTag("quick-add-field").performTextInput("  Pay rent  ")
        rule.onNodeWithTag("chip-list").performClick()
        waitForTag("quick-add-list-option-$work")
        rule.onNodeWithTag("quick-add-list-option-$work").performClick()
        waitGone("quick-add-list-option-$work")
        val day = pickTheFifteenth()
        rule.onNodeWithTag("chip-priority").performClick()
        waitForTag("quick-add-priority-option-2")
        rule.onNodeWithTag("quick-add-priority-option-2").performClick()
        waitGone("quick-add-priority-option-2")

        rule.onNodeWithTag("quick-add-save").performClick()
        rule.waitUntil(timeoutMillis = 10_000) { allTasks().isNotEmpty() }

        val saved = allTasks().single()
        assertEquals("Pay rent", saved.title)
        assertEquals(work, saved.listId)
        assertEquals(day.toEpochDay(), saved.dueDate)
        assertNull(saved.dueMinute)
        assertEquals(2, saved.priority)
    }

    @Test fun savingWithoutTouchingTheChipsUsesTheDefaults() {
        show()
        rule.onNodeWithTag("quick-add-field").performTextInput("Call dentist")
        rule.onNodeWithTag("quick-add-save").performClick()
        rule.waitUntil(timeoutMillis = 10_000) { allTasks().isNotEmpty() }

        val saved = allTasks().single()
        assertEquals(inbox, saved.listId)
        assertNull(saved.dueDate)
        assertEquals(4, saved.priority)
    }

    @Test fun blankTitleCannotBeSaved() {
        show()
        rule.onNodeWithTag("quick-add-save").assertIsNotEnabled()
        rule.onNodeWithTag("quick-add-field").performTextInput("   ")
        rule.onNodeWithTag("quick-add-save").assertIsNotEnabled()
        assertTrue(allTasks().isEmpty())
        // Control: the button does come on once there is a title.
        rule.onNodeWithTag("quick-add-field").performTextInput("x")
        rule.onNodeWithTag("quick-add-save").assertIsEnabled()
    }

    @Test fun savingClosesTheSheet() {
        show()
        assertFalse(dismissed)
        rule.onNodeWithTag("quick-add-field").performTextInput("Water the plants")
        rule.onNodeWithTag("quick-add-save").performClick()
        rule.waitUntil(timeoutMillis = 10_000) { dismissed }
        assertEquals(1, allTasks().size)
    }
}

package cz.olda.tasks.ui.quickadd

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.test.espresso.Espresso
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
    private lateinit var viewModel: QuickAddViewModel
    private var open by mutableStateOf(true)

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

    @After fun close() {
        rule.mainClock.autoAdvance = true
        db.close()
    }

    // A test for "the content is full size at once with animations off" was written here and
    // deleted. A scale IS visible to the test tree - node bounds are reported through the
    // layer's matrix, unlike an alpha - so it looked writable. What defeats it is the
    // fixture: the sheet cannot be reached before awaitLoaded(), and awaitLoaded() runs the
    // clock until the composition is idle, so the entrance has finished before any assertion
    // can see it. Making the entrance animate unconditionally did not make the test fail.

    /** Shows the sheet and waits for the lists to load, so later assertions see real data. */
    private fun show() {
        viewModel = QuickAddViewModel(tasks, lists)
        rule.setContent {
            AppTheme {
                if (open) QuickAddSheet(onDismiss = { dismissed = true; open = false }, viewModel = viewModel)
            }
        }
        awaitLoaded()
    }

    private fun awaitLoaded(list: String = "Inbox") = rule.waitUntil(timeoutMillis = 30_000) {
        rule.onAllNodes(hasTestTag("chip-list") and hasText(list, substring = true))
            .fetchSemanticsNodes().isNotEmpty()
    }

    private fun allTasks() = runBlocking {
        lists.observeLists().first().flatMap { tasks.observeInList(it.id).first() }.map { it.task }
    }

    private fun waitForTag(tag: String) = rule.waitUntil(timeoutMillis = 30_000) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitGone(tag: String) = rule.waitUntil(timeoutMillis = 30_000) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()
    }

    /** A day of the month that is not today, so the pick is a real change. */
    private fun dayToPick(): Int = if (LocalDate.now().dayOfMonth == 20) 10 else 20

    /** The day cell reads like "Friday, 20 November 2026"; match the day as a whole number, not the year. */
    private fun dayCell(day: Int) = SemanticsMatcher("day cell $day") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.split(Regex("[^0-9]+")).contains(day.toString()) } == true
    } and hasClickAction()

    /** Picks [dayToPick] in the month the picker opens on, and returns that date. */
    private fun pickADay(): LocalDate {
        val day = dayToPick()
        rule.onNodeWithTag("chip-date").performClick()
        waitForTag("quick-add-date-save")
        rule.onNode(dayCell(day)).performClick()
        rule.onNodeWithTag("quick-add-date-save").performClick()
        waitGone("quick-add-date-save")
        return LocalDate.now().withDayOfMonth(day)
    }

    private fun fieldText() =
        rule.onNodeWithTag("quick-add-field").fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun assertChipsAtDefaults() {
        rule.onNodeWithTag("chip-date").assertTextContains("No date")
        rule.onNodeWithTag("chip-list").assertTextContains("Inbox")
        rule.onNodeWithTag("chip-priority").assertTextContains("None", substring = true)
    }

    private fun pickWorkAndPriorityOne() {
        rule.onNodeWithTag("chip-list").performClick()
        waitForTag("quick-add-list-option-$work")
        rule.onNodeWithTag("quick-add-list-option-$work").performClick()
        waitGone("quick-add-list-option-$work")
        rule.onNodeWithTag("chip-priority").performClick()
        waitForTag("quick-add-priority-option-1")
        rule.onNodeWithTag("quick-add-priority-option-1").performClick()
        waitGone("quick-add-priority-option-1")
    }

    /** [list] is the list chip text to wait for: the draft may have come back with another list chosen. */
    private fun reopen(list: String = "Inbox") {
        // The closed sheet must be gone first, or the load check and the field read see the old one.
        waitGone("quick-add-field")
        rule.runOnUiThread { open = true }
        awaitLoaded(list)
    }

    @Test fun chipsStartAtNoDateInboxAndPriorityFour() {
        show()
        rule.onNodeWithTag("chip-date").assertTextContains("No date")
        rule.onNodeWithTag("chip-list").assertTextContains("Inbox")
        rule.onNodeWithTag("chip-priority").assertTextContains("None", substring = true)
    }

    @Test fun pickingADateShowsItOnTheChip() {
        show()
        val day = pickADay()
        // The chip names the day, not "No date".
        rule.onNodeWithTag("chip-date").assertTextContains(
            day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            substring = true,
        )
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
        val day = pickADay()
        rule.onNodeWithTag("chip-priority").performClick()
        waitForTag("quick-add-priority-option-2")
        rule.onNodeWithTag("quick-add-priority-option-2").performClick()
        waitGone("quick-add-priority-option-2")

        rule.onNodeWithTag("quick-add-save").performClick()
        rule.waitUntil(timeoutMillis = 30_000) { allTasks().isNotEmpty() }

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
        rule.waitUntil(timeoutMillis = 30_000) { allTasks().isNotEmpty() }

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
        // Press Done on the blank field and let the save settle, so "no task" is a result, not a default.
        rule.onNodeWithTag("quick-add-field").performImeAction()
        rule.waitForIdle()
        assertTrue(allTasks().isEmpty())
        assertFalse(dismissed)
        // Control: the button does come on once there is a title.
        rule.onNodeWithTag("quick-add-field").performTextInput("x")
        rule.onNodeWithTag("quick-add-save").assertIsEnabled()
    }

    @Test fun savingClosesTheSheet() {
        show()
        assertFalse(dismissed)
        rule.onNodeWithTag("quick-add-field").performTextInput("Water the plants")
        rule.onNodeWithTag("quick-add-save").performClick()
        rule.waitUntil(timeoutMillis = 30_000) { dismissed }
        assertEquals(1, allTasks().size)
    }

    @Test fun twoSavesAtOnceWriteOneTask() {
        val vm = QuickAddViewModel(tasks, lists)
        vm.setTitle("Once only")
        val results = runBlocking { listOf(async { vm.save() }, async { vm.save() }).awaitAll() }
        assertEquals(listOf(false, true), results.sorted())
        assertEquals(1, allTasks().size)
    }

    @Test fun theDraftIsReadFromSavedState() {
        val day = LocalDate.now().plusDays(3)
        val vm = QuickAddViewModel(
            tasks,
            lists,
            SavedStateHandle(
                mapOf(
                    QuickAddViewModel.TITLE to "Restored",
                    QuickAddViewModel.DATE to day.toEpochDay(),
                    QuickAddViewModel.LIST to work,
                    QuickAddViewModel.PRIORITY to 2,
                ),
            ),
        )
        assertEquals("Restored", vm.draftTitle)
        val state = runBlocking { vm.state.first { it.lists.isNotEmpty() } }
        assertEquals("Restored", state.title)
        assertEquals(day, state.dueDate)
        assertEquals(work, state.listId)
        assertEquals(2, state.priority)
        assertTrue(state.canSave)
    }

    @Test fun aSavedTaskDoesNotComeBackInTheNextQuickAdd() {
        show()
        rule.onNodeWithTag("quick-add-field").performTextInput("Pay rent")
        pickWorkAndPriorityOne()
        pickADay()
        rule.onNodeWithTag("quick-add-save").performClick()
        rule.waitUntil(timeoutMillis = 30_000) { dismissed }
        // Control: the draft really was filled in, and really was saved.
        assertEquals("Pay rent", allTasks().single().title)

        reopen()
        assertEquals("", fieldText())
        assertChipsAtDefaults()
    }

    @Test fun aDismissedDraftComesBackInTheNextQuickAdd() {
        show()
        rule.onNodeWithTag("quick-add-field").performTextInput("Never mind")
        pickWorkAndPriorityOne()
        rule.onNodeWithTag("chip-list").assertTextContains("Work")
        // The first Back hides the keyboard, the second closes the sheet.
        Espresso.pressBack()
        Espresso.pressBack()
        rule.waitUntil(timeoutMillis = 30_000) { dismissed }
        assertTrue(allTasks().isEmpty())

        reopen(list = "Work")
        assertEquals("Never mind", fieldText())
        rule.onNodeWithTag("chip-list").assertTextContains("Work")
        rule.onNodeWithTag("chip-priority").assert(!hasText("None", substring = true))
    }
}

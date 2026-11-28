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
import cz.olda.tasks.data.TagRepository
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
    private lateinit var tags: TagRepository
    private var work = 0L
    private var inbox = 0L
    private var dismissed = false
    private lateinit var viewModel: QuickAddViewModel
    private var open by mutableStateOf(true)

    /** The sheet test asserts against real "today", so it keeps the system clock. */
    private val clock: Clock = Clock.systemDefaultZone()

    @Before fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        tasks = TaskRepository(db.taskDao(), clock)
        lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
        tags = TagRepository(db.tagDao())
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
        viewModel = QuickAddViewModel(tasks, lists, tags, clock)
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

    /** performTextInput sets the field directly, so it does not go through his own keyboard. */
    private fun type(text: String) = rule.onNodeWithTag("quick-add-field").performTextInput(text)

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
        val vm = QuickAddViewModel(tasks, lists, tags, clock)
        vm.setInput("Once only")
        val results = runBlocking { listOf(async { vm.save() }, async { vm.save() }).awaitAll() }
        assertEquals("exactly one save should have written", 1, results.count { it != null })
        assertEquals(1, allTasks().size)
    }

    /** The shell has to know what was written to show where it went. */
    @Test fun saveReportsTheTaskItWrote() {
        val vm = QuickAddViewModel(tasks, lists, tags, clock)
        vm.setInput("  Zavolat na urad  ")
        vm.setList(work)
        val day = LocalDate.now().plusDays(2)
        vm.setDate(day)
        val saved = runBlocking { vm.save() }
        assertEquals(SavedTask("Zavolat na urad", day, toInbox = false), saved)
    }

    @Test fun saveReportsInboxWhenThatIsWhereItWent() {
        val vm = QuickAddViewModel(tasks, lists, tags, clock)
        vm.setInput("Bez data")
        val saved = runBlocking { vm.save() }
        assertEquals(SavedTask("Bez data", null, toInbox = true), saved)
    }

    @Test fun saveReportsNothingWhenItWroteNothing() {
        val vm = QuickAddViewModel(tasks, lists, tags, clock)
        assertNull(runBlocking { vm.save() })
    }

    @Test fun theDraftIsReadFromSavedState() {
        val day = LocalDate.now().plusDays(3)
        val vm = QuickAddViewModel(
            tasks,
            lists,
            tags,
            clock,
            SavedStateHandle(
                mapOf(
                    QuickAddViewModel.INPUT to "Restored",
                    QuickAddViewModel.DATE to day.toEpochDay(),
                    QuickAddViewModel.LIST to work,
                    QuickAddViewModel.PRIORITY to 2,
                ),
            ),
        )
        assertEquals("Restored", vm.draftInput)
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

    // ---- Phase 2: the chips the parser fills in ----

    /** Typing a time shows it; "ve 3" is three in the afternoon. */
    @Test fun theTimeChipShowsAParsedTime() {
        show()
        type("zitra ve 3 zavolat")
        rule.onNodeWithTag("chip-time").assertTextContains("15:00")
    }

    @Test fun theTimeChipOpensThePickerAndPinsWhatYouChoose() {
        show()
        type("zitra ve 3 zavolat")
        rule.onNodeWithTag("chip-time").performClick()
        waitForTag("quick-add-time-save")
        rule.onNodeWithTag("quick-add-time-save").performClick()
        waitGone("quick-add-time-save")
        // Typing on does not undo the pick: the chip keeps the time the picker confirmed.
        type(" Petrovi")
        rule.onNodeWithTag("chip-time").assertTextContains("15:00")
    }

    /**
     * Since Phase 3 the chip is always there, because it is how a repeat is **set**. Phase 2's
     * version of this test asserted the opposite, when there was no picker behind it.
     */
    @Test fun theRepeatChipIsAlwaysThereSoARepeatCanBeSet() {
        show()
        type("zavolat")
        waitForTag("chip-repeat")
        rule.onNodeWithTag("chip-repeat").assertTextContains("Repeat", substring = true)
    }

    /** His own spelling, not the stored rule: the chip echoes what he typed. */
    @Test fun theRepeatChipEchoesWhatWasTyped() {
        show()
        type("kazdy den cvicit")
        waitForTag("chip-repeat")
        rule.onNodeWithTag("chip-repeat").assertTextContains("kazdy den", substring = true)
    }

    @Test fun tappingTheRepeatChipOpensThePicker() {
        show()
        type("cvicit")
        waitForTag("chip-repeat")

        rule.onNodeWithTag("chip-repeat").performClick()

        waitForTag("repeat-picker")
    }

    @Test fun pickingARepeatInTheSheetSavesItWithTheTask() {
        show()
        type("cvicit")
        waitForTag("chip-repeat")
        rule.onNodeWithTag("chip-repeat").performClick()
        waitForTag("repeat-picker")

        rule.onNodeWithTag("repeat-option-FREQ=DAILY").performClick()
        rule.onNodeWithTag("quick-add-save").performClick()

        rule.waitUntil(timeoutMillis = 30_000) { allTasks().isNotEmpty() }
        assertEquals("FREQ=DAILY", allTasks().single().recurrence)
    }

    /**
     * The wart from Phase 2, named in a test: clearing the repeat used to take the chip with it,
     * leaving nothing on screen to bring it back.
     */
    @Test fun clearingTheRepeatLeavesTheChipThereToTapAgain() {
        show()
        type("kazdy den cvicit")
        waitForTag("chip-repeat")
        rule.onNodeWithTag("chip-repeat").performClick()
        waitForTag("repeat-picker")
        rule.onNodeWithTag("repeat-option-none").performClick()

        waitForTag("chip-repeat")
        rule.onNodeWithTag("chip-repeat").performClick()

        waitForTag("repeat-picker")
    }

    @Test fun aTagChipAppearsPerTag() {
        show()
        type("zavolat @telefon @rychle")
        waitForTag("chip-tag-telefon")
        waitForTag("chip-tag-rychle")
        rule.onNodeWithTag("chip-tag-telefon").assertTextContains("@telefon")
    }
}

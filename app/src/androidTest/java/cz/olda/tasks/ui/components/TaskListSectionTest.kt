package cz.olda.tasks.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.data.entity.TaskEntity
import cz.olda.tasks.ui.theme.AppTheme
import cz.olda.tasks.ui.theme.LocalAnimationsEnabled
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * The fold a completed task leaves behind. Everything here drives the clock by hand and
 * asserts the states around the animation - present, shown as done, gone - never the pixels
 * in between.
 */
class TaskListSectionTest {

    @get:Rule
    val rule = createComposeRule()

    @After
    fun letTheClockRunAgain() {
        rule.mainClock.autoAdvance = true
    }

    private val header = "Dnes"

    private fun task(id: Long, title: String) = TaskWithList(
        task = TaskEntity(
            id = id,
            title = title,
            titleNormalised = title.lowercase(),
            notesNormalised = "",
            listId = 1,
        ),
        listName = "Inbox",
        listColorHex = "#3D6BE0",
    )

    private class Section(
        val items: MutableState<List<TaskWithList>>,
        val tracker: CompletionTracker,
    ) {
        /** What the repository does when a row is completed: stamp it, then stop emitting it. */
        fun complete(id: Long) {
            tracker.justCompleted = id
            items.value = items.value.filterNot { it.task.id == id }
        }

        /** A row leaving for any other reason: a new search query, another day, a re-file. */
        fun drop(id: Long) {
            items.value = items.value.filterNot { it.task.id == id }
        }

        fun restore(item: TaskWithList) {
            tracker.justCompleted = null
            items.value = items.value + item
        }
    }

    private fun show(
        initial: List<TaskWithList>,
        animate: Boolean = true,
        fontScale: Float = 1f,
        widthDp: Int = 400,
    ): Section {
        val section = Section(mutableStateOf(initial), CompletionTracker())
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalCompletionTracker provides section.tracker,
            ) {
                // Through the parameter, not a provider around it: AppTheme provides
                // LocalAnimationsEnabled itself, so a provider outside it is shadowed.
                AppTheme(animationsEnabled = animate) {
                    TaskListSection(
                        title = header,
                        items = section.items.value,
                        onToggleDone = {},
                        onClick = {},
                        modifier = Modifier.width(widthDp.dp),
                    )
                }
            }
        }
        return section
    }

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first().lineCount
    }

    @Test
    fun aCompletedRowIsStillThereWhileItFolds() {
        val section = show(listOf(task(1, "Zavolat na urad")))
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { section.complete(1) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("task-1").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(2000)
        rule.onNodeWithTag("task-1").assertDoesNotExist()
    }

    /** docs/design.md: the circle fills and the title strikes through before the row goes. */
    @Test
    fun theFoldingRowShowsAsDone() {
        val section = show(listOf(task(1, "Zavolat na urad")))
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { section.complete(1) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("task-1").assertIsDisplayed()
        rule.onNodeWithTag("task-checkbox-1").assertIsOn()
    }

    /**
     * Review Focus 4. Rows leave a list constantly without being completed - a search
     * re-queries on every keystroke, the week strip swaps the whole day, a task gets
     * re-filed. Folding those would be wrong, and search would fold letter by letter.
     */
    @Test
    fun aRowThatLeavesWithoutBeingCompletedVanishesAtOnce() {
        val section = show(listOf(task(1, "Zavolat na urad"), task(2, "Koupit kabat")))
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { section.drop(1) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("task-1").assertDoesNotExist()
        rule.onNodeWithTag("task-2").assertIsDisplayed()
    }

    /**
     * Review Focus 2. The section used to disappear the instant its last row left, which
     * would drop the header out from over a row that is still folding.
     */
    @Test
    fun theHeaderStaysWhileTheLastRowFolds() {
        val section = show(listOf(task(1, "Zavolat na urad")))
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { section.complete(1) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText(header).assertIsDisplayed()

        rule.mainClock.advanceTimeBy(2000)
        rule.onNodeWithText(header).assertDoesNotExist()
    }

    /** Review Focus 3. Undo must give back one row, not two, and not a ghost. */
    @Test
    fun undoDuringTheFoldBringsTheRowBackExactlyOnce() {
        val item = task(1, "Zavolat na urad")
        val section = show(listOf(item))
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { section.complete(1) }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnUiThread { section.restore(item) }
        rule.mainClock.advanceTimeBy(2000)
        assertEquals(1, rule.onAllNodesWithTag("task-1").fetchSemanticsNodes().size)
        rule.onNodeWithTag("task-1").assertIsDisplayed()
    }

    /**
     * Review Focus 5. The fold animates height; a long Czech word must not start breaking
     * mid-word on the way out, which is the failure two fix rounds went into preventing.
     */
    @Test
    fun aLongCzechTitleDoesNotReflowWhileFolding() {
        val section = show(
            listOf(task(1, "zkontrolovat preobjednavku")),
            fontScale = 2f,
            widthDp = 360,
        )
        rule.onNodeWithTag("task-1").assertIsDisplayed()
        // The row is clickable, so its semantics merge; the title's own tag only exists in
        // the unmerged tree.
        val before = rule.onNodeWithTag("task-title-1", useUnmergedTree = true).lineCount()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { section.complete(1) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val during = rule.onNodeWithTag("task-title-1", useUnmergedTree = true).lineCount()
        assertEquals("the title reflowed while folding", before, during)
        assertFalse("the title was clipped while folding", during > 2)
    }

    /** Review Focus 1. */
    @Test
    fun completionIsInstantWithAnimationsOff() {
        val section = show(listOf(task(1, "Zavolat na urad")), animate = false)
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { section.complete(1) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("task-1").assertDoesNotExist()
    }
}

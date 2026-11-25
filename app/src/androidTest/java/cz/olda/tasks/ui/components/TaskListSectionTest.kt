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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.data.entity.TaskEntity
import cz.olda.tasks.ui.theme.AppTheme
import cz.olda.tasks.ui.theme.LocalAnimationsEnabled
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        /** A row coming back for some other reason - restored from the trash, re-filed here. */
        fun reappear(item: TaskWithList) {
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

    /**
     * It folds where it was. Every other fold test here uses a one-item section, which is the
     * only arrangement in which appending the held row to the end looks right.
     */
    @Test
    fun aCompletedRowFoldsWhereItWasAndDoesNotJumpToTheEnd() {
        val section = show(listOf(task(1, "Prvni"), task(2, "Druhy"), task(3, "Treti")))
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { section.complete(1) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        val folding = rule.onNodeWithTag("task-1").getUnclippedBoundsInRoot().top
        val below = rule.onNodeWithTag("task-2").getUnclippedBoundsInRoot().top
        assertTrue(
            "the folding row jumped below the rest: task-1 at $folding, task-2 at $below",
            folding < below,
        )
    }

    /**
     * The screens compose their sections with an empty list before Room answers, so a
     * section-wide "we have settled" flag would mark every row that follows as new and
     * unfurl the whole list on every cold start - 31 height animations at once on a
     * non-lazy Column, which is the jank the spec wrote itself to avoid.
     */
    @Test
    fun rowsThatArriveWithTheFirstLoadDoNotAnimateIn() {
        val section = show(emptyList())
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { section.items.value = listOf(task(1, "Prvni")) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        // Clipped bounds, deliberately: expandVertically clips the row to its animated
        // height while leaving the unclipped size at full, so unclipped sees nothing.
        val onArrival = rule.onNodeWithTag("task-1").getBoundsInRoot().height

        rule.mainClock.advanceTimeBy(1000)
        val settled = rule.onNodeWithTag("task-1").getBoundsInRoot().height
        assertEquals("the first load unfurled instead of being there", settled, onArrival)
    }

    /**
     * The completed id is remembered so the list can tell a completion from any other
     * departure. It must not stay remembered: a task completed, then restored, then re-filed
     * would fold on a departure that is not a completion.
     */
    @Test
    fun aRowDoesNotFoldLongAfterItsOwnCompletion() {
        val item = task(1, "Prvni")
        val section = show(listOf(item, task(2, "Druhy")))
        rule.runOnUiThread { section.complete(1) }
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnUiThread { section.reappear(item) }
        rule.mainClock.advanceTimeBy(2000)

        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { section.drop(1) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("task-1").assertDoesNotExist()
    }

    /**
     * Olda reports the fold looks jerky. A collapse can only stutter in ways a height
     * sequence shows: growing back partway through, standing still for several frames, or
     * dropping most of its height in one step. This samples every frame of the collapse and
     * holds it to all three.
     */
    @Test
    fun theFoldCollapsesSmoothly() {
        val section = show(listOf(task(1, "Prvni"), task(2, "Druhy")))
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { section.complete(1) }
        rule.mainClock.advanceTimeBy(130)

        val heights = mutableListOf<Float>()
        var collapsedButStillThere = 0
        repeat(40) {
            val node = rule.onAllNodesWithTag("task-1").fetchSemanticsNodes().firstOrNull()
            val h = node?.boundsInRoot?.height ?: 0f
            heights += h
            if (node != null && h == 0f) collapsedButStillThere++
            rule.mainClock.advanceTimeBy(16)
        }
        val trace = heights.joinToString(" ") { "%.0f".format(it) }

        val grewBack = heights.zipWithNext().any { (a, b) -> b > a + 1f }
        assertFalse("the fold grew back: $trace", grewBack)

        // A spring aimed at zero that undershoots gets clamped and sits there: the row is
        // invisible, but it still holds its place in the list, so the gap closes long after
        // the eye says the fold ended.
        assertTrue(
            "the row was collapsed but still in the list for $collapsedButStillThere " +
                "frames (${collapsedButStillThere * 16}ms) after reaching zero: $trace",
            collapsedButStillThere <= 4,
        )

        val start = heights.first()
        val biggestStep = heights.zipWithNext().maxOf { (a, b) -> a - b }
        assertTrue(
            "the fold dropped ${"%.0f".format(biggestStep)}px of $start in one frame: $trace",
            biggestStep <= start / 3f,
        )
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
        // Past the 120ms hold and into the shrink: undo during the collapse is the hard case,
        // and advancing a frame or two would only ever reach the hold.
        rule.mainClock.advanceTimeBy(200)
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
        // Past the 120ms hold, so this reads the title while the height is actually
        // collapsing rather than while the row merely sits there struck through.
        rule.mainClock.advanceTimeBy(200)
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

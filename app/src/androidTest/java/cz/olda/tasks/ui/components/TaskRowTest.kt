package cz.olda.tasks.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.data.entity.TaskEntity
import cz.olda.tasks.ui.theme.AppTheme
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TaskRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val sample = TaskWithList(
        task = TaskEntity(
            id = 7,
            title = "Pay the electricity bill",
            titleNormalised = "pay the electricity bill",
            notesNormalised = "",
            listId = 1,
            dueDate = 20000,
            dueMinute = 9 * 60 + 30,
        ),
        listName = "Home",
        listColorHex = "#2E7D32",
    )

    @Test
    fun showsTitleTimeAndListName() {
        rule.setContent { AppTheme { TaskRow(sample, {}, {}) } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 30)
        }
        val expected = DateFormat.getTimeFormat(context).format(cal.time)
        rule.onNodeWithText(sample.task.title).assertIsDisplayed()
        rule.onNodeWithText(expected).assertIsDisplayed()
        rule.onNodeWithText("Home").assertIsDisplayed()
        rule.onNodeWithTag("task-7").assertIsDisplayed()
    }

    @Test
    fun undatedRowShowsNoTime() {
        val undated = sample.copy(task = sample.task.copy(dueDate = null, dueMinute = null))
        rule.setContent { AppTheme { TaskRow(undated, {}, {}) } }
        rule.onNodeWithText(undated.task.title).assertIsDisplayed()
        rule.onNodeWithTag("task-time-7").assertDoesNotExist()
    }

    @Test
    fun tappingTheCheckboxReportsDone() {
        var toggled = 0
        var opened = 0
        rule.setContent { AppTheme { TaskRow(sample, { toggled++ }, { opened++ }) } }
        rule.onNodeWithContentDescription("Complete task").performClick()
        assertEquals(1, toggled)
        assertEquals(0, opened)
    }

    @Test
    fun anEmptySectionDrawsNothing() {
        rule.setContent {
            AppTheme { TaskListSection(title = "Overdue", items = emptyList(), onToggleDone = {}, onClick = {}) }
        }
        rule.onNodeWithText("Overdue").assertDoesNotExist()
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(out)
        return out.first()
    }

    /**
     * `hasVisualOverflow` reports width overflow for any wrap-content Text (its paragraph is laid
     * out at the max width, the node is sized to the content), so check the real symptoms:
     * ellipsis, vertical cut-off, and for one-line text a node narrower than the text.
     */
    private fun clipped(l: TextLayoutResult, singleLine: Boolean = false): Boolean {
        val ellipsized = (0 until l.lineCount).any { l.isLineEllipsized(it) }
        val cutOffVertically = l.size.height < l.multiParagraph.height.toInt()
        val cutOffHorizontally = singleLine && l.size.width < l.multiParagraph.maxIntrinsicWidth.toInt()
        return ellipsized || cutOffVertically || cutOffHorizontally
    }

    private fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)

    private fun assertSurvivesLargestFont(title: String, singleWord: Boolean) {
        val scale = mutableStateOf(1f)
        val phone = sample.copy(task = sample.task.copy(title = title))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = scale.value)) {
                AppTheme { TaskRow(phone, {}, {}, modifier = Modifier.width(360.dp)) }
            }
        }
        val normalHeight = rule.onNodeWithTag("task-7").getBoundsInRoot().height

        scale.value = 2f
        rule.waitForIdle()
        val rowBounds = rule.onNodeWithTag("task-7").getBoundsInRoot()
        val bigHeight = rowBounds.height

        val titleLayout = tag("task-title-7").textLayout()
        val time = tag("task-time-7").textLayout()
        val list = tag("task-list-7").textLayout()
        assertFalse("title clipped or ellipsized", clipped(titleLayout))
        assertTrue("title within 2 lines, was ${titleLayout.lineCount}", titleLayout.lineCount <= 2)
        if (singleWord) assertEquals("a single word must not break mid-word", 1, titleLayout.lineCount)
        assertFalse("time clipped", clipped(time, singleLine = true))
        assertEquals("time on one line", 1, time.lineCount)
        assertFalse("list name clipped", clipped(list, singleLine = true))
        assertEquals("list name on one line", 1, list.lineCount)

        val tb = tag("task-title-7").getBoundsInRoot()
        val mb = tag("task-time-7").getBoundsInRoot()
        val lb = tag("task-list-7").getBoundsInRoot()
        assertFalse("title overlaps time", intersects(tb, mb))
        assertFalse("title overlaps list name", intersects(tb, lb))
        assertFalse("time overlaps list name", intersects(mb, lb))
        assertTrue("title outside row", inside(tb, rowBounds))
        assertTrue("time outside row", inside(mb, rowBounds))
        assertTrue("list name outside row", inside(lb, rowBounds))
        assertTrue("row must grow: $normalHeight -> $bigHeight", bigHeight > normalHeight)
    }

    @Test
    fun rowSurvivesTheLargestFontSize() = assertSurvivesLargestFont("Pay the bill", singleWord = false)

    // Czech compounds are long single words; one cannot wrap, so it must not break or ellipsize.
    @Test
    fun rowSurvivesTheLargestFontSizeWithALongCzechWord() =
        assertSurvivesLargestFont("zkontrolovat", singleWord = true)

    private fun inside(inner: DpRect, outer: DpRect) =
        inner.left >= outer.left && inner.right <= outer.right &&
            inner.top >= outer.top && inner.bottom <= outer.bottom

    private fun intersects(a: DpRect, b: DpRect) =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun item(id: Long, title: String) =
        sample.copy(task = sample.task.copy(id = id, title = title))

    @Test
    fun sectionWithTitleShowsHeaderAndRows() {
        var toggled = -1L
        var clicked = -1L
        rule.setContent {
            AppTheme {
                TaskListSection(
                    title = "Overdue",
                    items = listOf(item(1, "First"), item(2, "Second")),
                    onToggleDone = { toggled = it },
                    onClick = { clicked = it },
                )
            }
        }
        rule.onNodeWithText("Overdue").assertIsDisplayed()
        rule.onNodeWithTag("task-1").assertIsDisplayed()
        rule.onNodeWithTag("task-2").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Complete task")[1].performClick()
        assertEquals(2L, toggled)
        rule.onNodeWithText("First").performClick()
        assertEquals(1L, clicked)
    }

    @Test
    fun sectionWithNullTitleShowsRowsWithoutHeader() {
        rule.setContent {
            AppTheme {
                TaskListSection(title = null, items = listOf(item(1, "Only row")), onToggleDone = {}, onClick = {})
            }
        }
        rule.onNodeWithTag("task-1").assertIsDisplayed()
        rule.onNodeWithText("Only row").assertIsDisplayed()
        // The only text nodes are the row's own: time, title, list name.
        rule.onAllNodesWithText("Overdue").assertCountEquals(0)
        rule.onNodeWithTag("task-1").onParent().onChildren().assertCountEquals(1)
    }
}

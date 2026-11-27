package cz.olda.tasks.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import cz.olda.tasks.ui.theme.ListPalette
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cz.olda.tasks.ui.theme.contrastRatio
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

    private val orange = sample.copy(listName = "Garden", listColorHex = "#E07B39")

    @Test
    fun listNameIsNotDrawnInTheRawColourWhenThatIsTooFaint() {
        rule.setContent { AppTheme(darkTheme = false) { TaskRow(orange, {}, {}) } }
        val drawn = tag("task-list-7").textLayout().layoutInput.style.color
        assertTrue("raw Orange is only 3:1, the name must be darker", drawn != Color(0xFFE07B39))
        assertTrue(contrastRatio(drawn, Color(0xFFFAF7F3)) >= 4.5f)
    }

    @Test
    fun listNameUsesThePresetsDarkShadeInDarkTheme() {
        rule.setContent { AppTheme(darkTheme = true) { TaskRow(orange, {}, {}) } }
        val drawn = tag("task-list-7").textLayout().layoutInput.style.color
        assertEquals(Color(0xFFFF9F5A), drawn)
    }

    @Test
    fun undatedRowShowsNoTime() {
        val undated = sample.copy(task = sample.task.copy(dueDate = null, dueMinute = null))
        rule.setContent { AppTheme { TaskRow(undated, {}, {}) } }
        rule.onNodeWithText(undated.task.title).assertIsDisplayed()
        rule.onNodeWithTag("task-time-7").assertDoesNotExist()
    }

    /**
     * The rows in the mockups sit straight on the warm background - the rail is the only thing
     * drawn behind them - so a row must not carry a card of its own. Sampled at the left edge
     * halfway down, which is inside a card but outside its rounded corner, so a corner pixel
     * would pass either way.
     */
    @Test
    fun theRowSitsOnTheBackgroundWithNoCardBehindIt() {
        rule.setContent {
            AppTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) { TaskRow(sample, {}, {}) }
            }
        }
        val pixels = rule.onNodeWithTag("task-7").captureToImage().toPixelMap()
        assertEquals(Color(0xFFFAF7F3), pixels[1, pixels.height / 2])
    }

    /** Raw, not the text shade: the ring is a graphic, and the mockups draw it in the list's colour. */
    @Test
    fun theRingIsDrawnInTheListsOwnColour() {
        rule.setContent {
            AppTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) { TaskRow(orange, {}, {}) }
            }
        }
        assertTrue("no pixel of the ring is #E07B39", ringPixels() > 0)
    }

    @Test
    fun completingFillsTheRing() {
        var done by mutableStateOf(false)
        rule.setContent {
            AppTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TaskRow(orange.copy(task = orange.task.copy(done = done)), {}, {})
                }
            }
        }
        val hollow = ringPixels()

        done = true
        rule.waitForIdle()
        val filled = ringPixels()
        // A 3dp stroke covers about 40% of an 18dp circle; the disc covers all of it bar the tick
        // cut out of the middle. Measured on the phone: 1632px open, 2804px done.
        assertTrue("a filled ring ($filled px) must be well more than an empty one ($hollow px)", filled > hollow * 3 / 2)
    }

    /** How much of the ring is drawn in Garden's raw orange: a stroke when open, a disc when done. */
    private fun ringPixels(): Int {
        val pixels = rule.onNodeWithTag("task-ring-7", useUnmergedTree = true).captureToImage().toPixelMap()
        var found = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                if (pixels[x, y] == Color(0xFFE07B39)) found++
            }
        }
        return found
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

    private fun assertSurvivesLargestFont(
        title: String,
        singleWord: Boolean,
        widthDp: Int = 360,
        bigScale: Float = 2f,
        listName: String = sample.listName,
        showDate: Boolean = false,
    ) {
        val scale = mutableStateOf(1f)
        val phone = sample.copy(task = sample.task.copy(title = title), listName = listName)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = scale.value)) {
                AppTheme { TaskRow(phone, {}, {}, modifier = Modifier.width(widthDp.dp), showDate = showDate) }
            }
        }
        val normalHeight = rule.onNodeWithTag("task-7").getBoundsInRoot().height

        scale.value = bigScale
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
        if (bigScale > 1f) assertTrue("row must grow: $normalHeight -> $bigHeight", bigHeight > normalHeight)
    }

    @Test
    fun rowSurvivesTheLargestFontSize() = assertSurvivesLargestFont("Pay the bill", singleWord = false)

    // Czech compounds are long single words; one cannot wrap, so it must not break or ellipsize.
    @Test
    fun rowSurvivesTheLargestFontSizeWithALongCzechWord() =
        assertSurvivesLargestFont("zkontrolovat", singleWord = true)

    @Test
    fun longCzechWordSurvivesAMidFontScaleWithALongListName() = assertSurvivesLargestFont(
        "zkontrolovat", singleWord = true, bigScale = 1.4f, listName = "Household chores",
    )

    @Test
    fun longCzechWordSurvivesANarrowScreenAtNormalFontScale() = assertSurvivesLargestFont(
        "zkontrolovat", singleWord = true, widthDp = 320, bigScale = 1f, listName = "Household chores",
    )

    // The due day takes width in the same slot as the time, so the stacking maths must still hold.
    @Test
    fun rowWithADateSurvivesTheLargestFontSize() =
        assertSurvivesLargestFont("Pay the bill", singleWord = false, showDate = true)

    @Test
    fun longCzechWordWithADateSurvivesTheLargestFontSize() =
        assertSurvivesLargestFont("zkontrolovat", singleWord = true, showDate = true)

    @Test
    fun longCzechWordWithADateSurvivesANarrowScreenAndALongListName() = assertSurvivesLargestFont(
        "zkontrolovat", singleWord = true, widthDp = 320, bigScale = 1f, listName = "Household chores", showDate = true,
    )

    private fun timeText(): String =
        tag("task-time-7").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }

    @Test
    fun theDueDayShowsOnlyWhenAsked() {
        var asked by mutableStateOf(false)
        rule.setContent { AppTheme { TaskRow(sample, {}, {}, showDate = asked) } }
        val withoutDate = timeText()
        assertFalse("no date by default: $withoutDate", withoutDate.contains("2024"))

        asked = true
        rule.waitForIdle()
        val withDate = timeText()
        // 20000 is 4 October 2024; the year shows because it is not this one.
        assertTrue("date missing: $withDate", withDate.contains("2024") && withDate.endsWith(withoutDate))
    }

    /**
     * On the Inbox and inside one list, every row carries the same list name, so the name is
     * noise taking width the title needs. The row can be told to leave it out.
     */
    @Test
    fun aRowCanBeToldToLeaveOutItsListName() {
        rule.setContent { AppTheme { TaskRow(sample, {}, {}, showList = false) } }
        rule.onNodeWithText(sample.task.title).assertIsDisplayed()
        rule.onNodeWithTag("task-list-7").assertDoesNotExist()
    }

    /** With the name gone, the title gets that width rather than wrapping for no reason. */
    @Test
    fun droppingTheListNameLeavesTheTitleMoreRoom() {
        var withName by mutableStateOf(true)
        val longName = sample.copy(
            task = sample.task.copy(title = "Zkontrolovat fakturu od dodavatele"),
            listName = "Household chores",
        )
        rule.setContent {
            AppTheme { TaskRow(longName, {}, {}, modifier = Modifier.width(360.dp), showList = withName) }
        }
        val wrapped = tag("task-title-7").textLayout().size.width

        withName = false
        rule.waitForIdle()
        val roomier = tag("task-title-7").textLayout().size.width
        assertTrue("the title got no wider: $wrapped -> $roomier", roomier > wrapped)
    }

    /**
     * The day in a row is "4 Oct 2024", not "Fri, 4 Oct 2024". On the Inbox the weekday of a date
     * weeks past says nothing, and it cost the title a line.
     */
    @Test
    fun theDueDayInARowCarriesNoWeekday() {
        rule.setContent { AppTheme { TaskRow(sample, {}, {}, showDate = true) } }
        val shown = timeText()
        // 20000 is Friday 4 October 2024: the control is that the date itself is there.
        assertTrue("the date is missing: $shown", shown.contains("2024"))
        assertFalse("the weekday is still there: $shown", shown.contains("Fri"))
    }

    @Test
    fun anUndatedRowShowsNothingEvenWhenAskedForDates() {
        val undated = sample.copy(task = sample.task.copy(dueDate = null, dueMinute = null))
        rule.setContent { AppTheme { TaskRow(undated, {}, {}, showDate = true) } }
        rule.onNodeWithText(undated.task.title).assertIsDisplayed()
        rule.onNodeWithTag("task-time-7").assertDoesNotExist()
    }

    private fun barColour(priority: Int, darkTheme: Boolean): Color? {
        val row = sample.copy(task = sample.task.copy(priority = priority))
        rule.setContent { AppTheme(darkTheme = darkTheme) { TaskRow(row, {}, {}) } }
        val bar = rule.onAllNodesWithTag("task-priority-bar-7")
        if (bar.fetchSemanticsNodes().isEmpty()) return null
        val pixels = bar.onFirst().captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    @Test
    fun priorityOneIsRed() {
        assertEquals(ListPalette.first { it.name == "Red" }.light, barColour(1, darkTheme = false))
    }

    @Test
    fun priorityTwoIsOrange() {
        assertEquals(ListPalette.first { it.name == "Orange" }.light, barColour(2, darkTheme = false))
    }

    @Test
    fun priorityThreeIsBlue() {
        assertEquals(ListPalette.first { it.name == "Blue" }.light, barColour(3, darkTheme = false))
    }

    @Test
    fun theBarUsesTheDarkShadeInDarkTheme() {
        assertEquals(ListPalette.first { it.name == "Red" }.dark, barColour(1, darkTheme = true))
    }

    /**
     * A mark on the row, not a line down it. The rail already runs down the ring column, and a
     * full-height bar at the leading edge read as a second rail beside it.
     */
    @Test
    fun thePriorityMarkIsADashShorterThanItsRow() {
        val long = sample.copy(
            task = sample.task.copy(priority = 1, title = "A title long enough to wrap onto a second line"),
        )
        rule.setContent { AppTheme { TaskRow(long, {}, {}, modifier = Modifier.width(320.dp)) } }
        val row = rule.onNodeWithTag("task-7").getBoundsInRoot().height
        val mark = tag("task-priority-bar-7").getBoundsInRoot().height
        // A row that fits on one line is exactly the 48dp minimum, so anything above it wrapped -
        // which is what makes this the case a full-height bar would have stretched through.
        assertTrue("the row must wrap for this to be the case under test, was $row", row > 48.dp)
        assertTrue("the mark ($mark) must be well short of the row ($row)", mark < row / 2)
    }

    @Test
    fun priorityFourHasNoBar() {
        // The tests above are the control: with a priority the same lookup finds a bar.
        assertEquals(null, barColour(4, darkTheme = false))
    }

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

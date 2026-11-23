package cz.olda.tasks.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.data.entity.TaskEntity
import cz.olda.tasks.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

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

    @Test
    fun rowSurvivesTheLargestFontSize() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                AppTheme { TaskRow(sample, {}, {}) }
            }
        }
        rule.onNodeWithText(sample.task.title).assertIsDisplayed()
        rule.onNodeWithText(sample.listName).assertIsDisplayed()
    }
}


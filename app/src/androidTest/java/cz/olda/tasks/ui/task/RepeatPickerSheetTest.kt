package cz.olda.tasks.ui.task

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.olda.tasks.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The picker Phase 2 left out: a short list to tap, with Custom… behind it. Olda chose this shape
 * on 28 Nov over a sentence builder and a text field read by the parser.
 */
@RunWith(AndroidJUnit4::class)
class RepeatPickerSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val tuesday = LocalDate.of(2026, 12, 1)
    private val picked = mutableListOf<String?>()

    private fun picker(current: String? = null, anchor: LocalDate = tuesday) {
        compose.setContent {
            AppTheme {
                RepeatPickerContent(current = current, anchor = anchor, onPick = { picked += it })
            }
        }
    }

    @Test
    fun theFiveWaysToRepeatAreAllThere() {
        picker()

        compose.onNodeWithTag("repeat-option-none").assertIsDisplayed()
        compose.onNodeWithTag("repeat-option-FREQ=DAILY").assertIsDisplayed()
        compose.onNodeWithTag("repeat-option-FREQ=WEEKLY;BYDAY=TU").assertIsDisplayed()
        compose.onNodeWithTag("repeat-option-FREQ=MONTHLY").assertIsDisplayed()
        compose.onNodeWithTag("repeat-option-FREQ=YEARLY").assertIsDisplayed()
        compose.onNodeWithTag("repeat-option-custom").assertIsDisplayed()
    }

    /** The weekly row names the day the task actually falls on, not a day Olda has to work out. */
    @Test
    fun theWeeklyRowNamesTheTasksOwnWeekday() {
        picker(anchor = tuesday)

        compose.onNodeWithTag("repeat-option-FREQ=WEEKLY;BYDAY=TU")
            .assertTextContains("Tuesday", substring = true)
    }

    @Test
    fun tappingEveryDayPicksIt() {
        picker()

        compose.onNodeWithTag("repeat-option-FREQ=DAILY").performClick()

        assertEquals(listOf<String?>("FREQ=DAILY"), picked)
    }

    @Test
    fun tappingDoesNotRepeatPicksNothing() {
        picker(current = "FREQ=DAILY")

        compose.onNodeWithTag("repeat-option-none").performClick()

        assertEquals(listOf<String?>(null), picked)
    }

    @Test
    fun theCurrentRuleIsTickedAndTheOthersAreNot() {
        picker(current = "FREQ=MONTHLY")

        compose.onNodeWithTag("repeat-option-FREQ=MONTHLY").assertIsSelected()
        compose.onNodeWithTag("repeat-option-FREQ=DAILY").assertIsNotSelected()
        compose.onNodeWithTag("repeat-option-none").assertIsNotSelected()
    }

    /**
     * "Every week" is what Phase 2's parser writes for `kazdy tyden`, and it is not the same string
     * as the weekly preset, which always carries a weekday. Nothing may be left with no tick at
     * all: the sheet would be telling Olda that a task with a repeat has none.
     */
    @Test
    fun aPlainWeeklyRuleIsStillTickedSomewhere() {
        picker(current = "FREQ=WEEKLY")

        compose.onNodeWithTag("repeat-option-FREQ=WEEKLY;BYDAY=TU").assertIsSelected()
        compose.onNodeWithTag("repeat-option-none").assertIsNotSelected()
    }

    /**
     * His real task "Číst při Týdenním resetu (pondělí)" is `FREQ=WEEKLY;BYDAY=MO` on a task due a
     * Saturday. The weekly row is worded from the due date, so it does not equal the rule — and
     * without this, the sheet shows nothing ticked and tapping the weekly row would quietly move
     * the task off Monday.
     */
    @Test
    fun aWeekdayRuleThatDisagreesWithTheDueDateIsStillTicked() {
        val saturday = LocalDate.of(2026, 12, 5)
        picker(current = "FREQ=WEEKLY;BYDAY=MO", anchor = saturday)

        compose.onNodeWithTag("repeat-option-FREQ=WEEKLY;BYDAY=MO").assertIsSelected()
        compose.onNodeWithTag("repeat-option-FREQ=WEEKLY;BYDAY=MO")
            .assertTextContains("Monday", substring = true)
        compose.onNodeWithTag("repeat-option-none").assertIsNotSelected()
    }

    /** Content descriptions are read aloud, so they are user-facing text and live in strings.xml. */
    @Test
    fun theCustomStepperButtonsAreNamedFromTheStringsFile() {
        picker()
        compose.onNodeWithTag("repeat-option-custom").performClick()

        compose.onNodeWithContentDescription("Fewer").assertExists()
        compose.onNodeWithContentDescription("More").assertExists()
    }

    @Test
    fun aTaskWithNoRepeatHasDoesNotRepeatTicked() {
        picker(current = null)

        compose.onNodeWithTag("repeat-option-none").assertIsSelected()
    }

    @Test
    fun customWritesTheIntervalItWasBuiltWith() {
        picker()

        compose.onNodeWithTag("repeat-option-custom").performClick()
        compose.onNodeWithTag("repeat-custom-more").performClick()
        compose.onNodeWithTag("repeat-custom-save").performClick()

        assertEquals(listOf<String?>("FREQ=DAILY;INTERVAL=2"), picked)
    }

    @Test
    fun customCountsDownButNeverBelowOne() {
        picker()

        compose.onNodeWithTag("repeat-option-custom").performClick()
        repeat(3) { compose.onNodeWithTag("repeat-custom-fewer").performClick() }
        compose.onNodeWithTag("repeat-custom-interval").assertTextContains("1", substring = true)
        compose.onNodeWithTag("repeat-custom-save").performClick()

        assertEquals("an interval of one is written without it", listOf<String?>("FREQ=DAILY"), picked)
    }

    @Test
    fun customOpensOnTheRuleTheTaskAlreadyHas() {
        picker(current = "FREQ=MONTHLY;INTERVAL=3")

        compose.onNodeWithTag("repeat-option-custom").performClick()

        compose.onNodeWithTag("repeat-custom-interval").assertTextContains("3", substring = true)
        compose.onNodeWithTag("repeat-custom-unit").assertTextContains("months", substring = true)
    }

    @Test
    fun customCanChangeTheUnit() {
        picker()

        compose.onNodeWithTag("repeat-option-custom").performClick()
        compose.onNodeWithTag("repeat-custom-unit").performClick()
        compose.onNodeWithTag("repeat-custom-unit-WEEKS").performClick()
        compose.onNodeWithTag("repeat-custom-more").performClick()
        compose.onNodeWithTag("repeat-custom-save").performClick()

        assertEquals(listOf<String?>("FREQ=WEEKLY;INTERVAL=2"), picked)
    }
}

package cz.olda.tasks.ui.task

import cz.olda.tasks.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

/**
 * What the repeat picker writes. The rows Olda taps and the Custom sheet both come from here, and
 * everything either of them can produce has to be a rule the rest of the app can say out loud —
 * `recurrenceLabel` is the judge of that, and the last test holds the two together.
 */
class RepeatRulesTest {

    private val tuesday = LocalDate.of(2026, 12, 1)
    private val sunday = LocalDate.of(2026, 12, 6)

    @Test
    fun theWeeklyPresetTakesTheWeekdayFromTheTask() {
        assertEquals(
            listOf("FREQ=DAILY", "FREQ=WEEKLY;BYDAY=TU", "FREQ=MONTHLY", "FREQ=YEARLY"),
            presetRules(tuesday),
        )
        assertEquals("FREQ=WEEKLY;BYDAY=SU", presetRules(sunday)[1])
    }

    @Test
    fun anIntervalOfOneIsLeftOutSoThePresetAndTheCustomSheetAgree() {
        assertEquals("FREQ=DAILY", customRule(1, RepeatUnit.DAYS))
        assertEquals("FREQ=WEEKLY", customRule(1, RepeatUnit.WEEKS))
        assertEquals("FREQ=MONTHLY", customRule(1, RepeatUnit.MONTHS))
        assertEquals("FREQ=YEARLY", customRule(1, RepeatUnit.YEARS))
    }

    @Test
    fun anIntervalAboveOneIsWrittenOut() {
        assertEquals("FREQ=DAILY;INTERVAL=2", customRule(2, RepeatUnit.DAYS))
        assertEquals("FREQ=WEEKLY;INTERVAL=3", customRule(3, RepeatUnit.WEEKS))
        assertEquals("FREQ=MONTHLY;INTERVAL=3", customRule(3, RepeatUnit.MONTHS))
        assertEquals("FREQ=YEARLY;INTERVAL=2", customRule(2, RepeatUnit.YEARS))
    }

    @Test
    fun customOpensOnTheRuleItIsGiven() {
        assertEquals(2 to RepeatUnit.DAYS, customOf("FREQ=DAILY;INTERVAL=2"))
        assertEquals(3 to RepeatUnit.WEEKS, customOf("FREQ=WEEKLY;INTERVAL=3"))
        assertEquals(3 to RepeatUnit.MONTHS, customOf("FREQ=MONTHLY;INTERVAL=3"))
        assertEquals(2 to RepeatUnit.YEARS, customOf("FREQ=YEARLY;INTERVAL=2"))
    }

    /** A preset is not a custom rule, and neither is rubbish: Custom opens on its own defaults. */
    @Test
    fun aPresetOrAnUnreadableRuleIsNotACustomOne() {
        listOf("FREQ=DAILY", "FREQ=WEEKLY", "FREQ=WEEKLY;BYDAY=TU", "FREQ=MONTHLY", "FREQ=YEARLY", "", "rubbish")
            .forEach { assertNull(it, customOf(it)) }
    }

    @Test
    fun everyRuleThePickerCanWriteIsOneTheAppCanSayOutLoud() {
        val rules = presetRules(tuesday) +
            RepeatUnit.entries.flatMap { unit -> (1..4).map { customRule(it, unit) } }

        rules.forEach { rule ->
            assertNotEquals(
                "the picker can write $rule, which task detail would show as \"Repeats\"",
                RecurrenceLabel(R.string.repeat_unknown),
                recurrenceLabel(rule),
            )
        }
    }
}

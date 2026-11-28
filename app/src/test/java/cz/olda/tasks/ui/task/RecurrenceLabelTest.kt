package cz.olda.tasks.ui.task

import cz.olda.tasks.R
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * By the time a task is open, Olda's own words for its repeat are gone — only the stored rule is
 * left — and there is no Czech string file, so task detail says it in English.
 */
class RecurrenceLabelTest {

    @Test
    fun eachRuleGetsItsOwnLabel() {
        assertEquals(RecurrenceLabel(R.string.repeat_daily), recurrenceLabel("FREQ=DAILY"))
        assertEquals(RecurrenceLabel(R.string.repeat_weekly), recurrenceLabel("FREQ=WEEKLY"))
        assertEquals(RecurrenceLabel(R.string.repeat_monthly), recurrenceLabel("FREQ=MONTHLY"))
        assertEquals(RecurrenceLabel(R.string.repeat_yearly), recurrenceLabel("FREQ=YEARLY"))
        assertEquals(
            RecurrenceLabel(R.string.repeat_weekly_on, day = DayOfWeek.MONDAY),
            recurrenceLabel("FREQ=WEEKLY;BYDAY=MO"),
        )
        assertEquals(
            RecurrenceLabel(R.string.repeat_weekly_on, day = DayOfWeek.SUNDAY),
            recurrenceLabel("FREQ=WEEKLY;BYDAY=SU"),
        )
        assertEquals(
            RecurrenceLabel(R.string.repeat_every_n_days, interval = 3),
            recurrenceLabel("FREQ=DAILY;INTERVAL=3"),
        )
        assertEquals(
            RecurrenceLabel(R.string.repeat_every_n_weeks, interval = 2),
            recurrenceLabel("FREQ=WEEKLY;INTERVAL=2"),
        )
    }

    /**
     * Added in Phase 3 with the picker, whose Custom sheet offers months and years: without these
     * two, picking "every 3 months" would quietly store something that reads as "Repeats".
     */
    @Test
    fun anIntervalInMonthsOrYearsHasItsOwnLabel() {
        assertEquals(
            RecurrenceLabel(R.string.repeat_every_n_months, interval = 3),
            recurrenceLabel("FREQ=MONTHLY;INTERVAL=3"),
        )
        assertEquals(
            RecurrenceLabel(R.string.repeat_every_n_years, interval = 2),
            recurrenceLabel("FREQ=YEARLY;INTERVAL=2"),
        )
    }

    @Test
    fun aWeekdayOnAMonthlyOrYearlyRuleIsStillUnreadable() {
        assertEquals(RecurrenceLabel(R.string.repeat_unknown), recurrenceLabel("FREQ=MONTHLY;BYDAY=MO"))
        assertEquals(RecurrenceLabel(R.string.repeat_unknown), recurrenceLabel("FREQ=YEARLY;BYDAY=MO"))
    }

    @Test
    fun anUnreadableRuleFallsBackToRepeats() {
        listOf("", "nonsense", "FREQ=HOURLY", "FREQ=WEEKLY;BYDAY=XX", "FREQ=DAILY;INTERVAL=abc")
            .forEach { assertEquals(it, RecurrenceLabel(R.string.repeat_unknown), recurrenceLabel(it)) }
    }
}

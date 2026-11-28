package cz.olda.tasks.data.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The rule Olda chose on 28 Nov: the next occurrence keeps the schedule and skips what was missed.
 * Every case here is a row of the design document's table.
 *
 * November 2026 starts on a Sunday, so the three dates below really are a Monday, a Wednesday and
 * a Friday; `theDatesAreTheWeekdaysTheTestNamesClaim` holds that.
 */
class NextOccurrenceTest {

    private val mon = LocalDate.of(2026, 11, 30)
    private val wed = LocalDate.of(2026, 12, 2)
    private val fri = LocalDate.of(2026, 12, 4)

    @Test
    fun theDatesAreTheWeekdaysTheTestNamesClaim() {
        assertEquals(java.time.DayOfWeek.MONDAY, mon.dayOfWeek)
        assertEquals(java.time.DayOfWeek.WEDNESDAY, wed.dayOfWeek)
        assertEquals(java.time.DayOfWeek.FRIDAY, fri.dayOfWeek)
    }

    @Test
    fun dailyTickedLateSkipsTheMissedDays() =
        assertEquals(LocalDate.of(2026, 12, 3), nextOccurrence("FREQ=DAILY", mon, wed))

    @Test
    fun dailyTickedOnTimeStepsOnce() =
        assertEquals(LocalDate.of(2026, 12, 1), nextOccurrence("FREQ=DAILY", mon, mon))

    @Test
    fun dailyTickedEarlyStillStepsFromItsDueDate() =
        assertEquals(LocalDate.of(2026, 12, 5), nextOccurrence("FREQ=DAILY", fri, wed))

    @Test
    fun anIntervalKeepsItsRhythmRatherThanRestarting() =
        assertEquals(LocalDate.of(2026, 12, 6), nextOccurrence("FREQ=DAILY;INTERVAL=3", mon, fri))

    @Test
    fun weeklyStepsSevenDays() =
        assertEquals(LocalDate.of(2026, 12, 7), nextOccurrence("FREQ=WEEKLY", mon, wed))

    @Test
    fun everyTwoWeeksKeepsItsParity() =
        assertEquals(LocalDate.of(2026, 12, 14), nextOccurrence("FREQ=WEEKLY;INTERVAL=2", mon, wed))

    @Test
    fun aBydayRuleStaysOnItsWeekday() =
        assertEquals(LocalDate.of(2026, 12, 7), nextOccurrence("FREQ=WEEKLY;BYDAY=MO", mon, wed))

    @Test
    fun aBydayRuleMovesOffADueDateThatIsTheWrongWeekday() =
        assertEquals(LocalDate.of(2026, 12, 7), nextOccurrence("FREQ=WEEKLY;BYDAY=MO", wed, wed))

    @Test
    fun aBydayRuleTickedWeeksLateLandsOnTheNextSuchWeekday() =
        assertEquals(
            LocalDate.of(2026, 12, 14),
            nextOccurrence("FREQ=WEEKLY;BYDAY=MO", mon, LocalDate.of(2026, 12, 10)),
        )

    @Test
    fun monthlyClampsToAShortMonth() = assertEquals(
        LocalDate.of(2027, 2, 28),
        nextOccurrence("FREQ=MONTHLY", LocalDate.of(2027, 1, 31), LocalDate.of(2027, 1, 31)),
    )

    @Test
    fun everyThreeMonthsIsQuarterly() = assertEquals(
        LocalDate.of(2027, 6, 1),
        nextOccurrence("FREQ=MONTHLY;INTERVAL=3", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 1)),
    )

    @Test
    fun yearlyClampsALeapDay() = assertEquals(
        LocalDate.of(2029, 2, 28),
        nextOccurrence("FREQ=YEARLY", LocalDate.of(2028, 2, 29), LocalDate.of(2028, 2, 29)),
    )

    @Test
    fun everyTwoYearsSkipsAYear() = assertEquals(
        LocalDate.of(2028, 6, 1),
        nextOccurrence("FREQ=YEARLY;INTERVAL=2", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1)),
    )

    @Test
    fun withNoDueDateTodayIsTheAnchor() =
        assertEquals(LocalDate.of(2026, 12, 3), nextOccurrence("FREQ=DAILY", null, wed))

    @Test
    fun aDailyRuleFromAnotherEraCatchesUp() =
        assertEquals(LocalDate.of(2026, 12, 3), nextOccurrence("FREQ=DAILY", LocalDate.of(2020, 1, 1), wed))

    @Test
    fun aYearlyRuleFromNineteenSeventyCatchesUp() =
        assertEquals(LocalDate.of(2027, 1, 1), nextOccurrence("FREQ=YEARLY", LocalDate.of(1970, 1, 1), wed))

    /** Nothing may throw, and nothing may create a task on a guessed day. */
    @Test
    fun anUnreadableRuleIsRefused() {
        listOf(
            "", "FREQ=HOURLY", "FREQ=", "nonsense", "FREQ=DAILY;INTERVAL=0",
            "FREQ=DAILY;INTERVAL=-2", "FREQ=DAILY;INTERVAL=x", "FREQ=WEEKLY;BYDAY=XX",
            "FREQ=DAILY;FREQ=WEEKLY",
        ).forEach { assertNull(it, nextOccurrence(it, mon, wed)) }
    }

    /** The stepping path's guard: a monthly rule anchored in 1800 is ~2,724 steps, past the cap. */
    @Test
    fun aRuleAnchoredAbsurdlyFarBackIsRefused() =
        assertNull(nextOccurrence("FREQ=MONTHLY", LocalDate.of(1800, 1, 1), wed))
}

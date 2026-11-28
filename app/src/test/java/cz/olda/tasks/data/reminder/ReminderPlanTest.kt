package cz.olda.tasks.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which minute a task rings at, and which ring is next. No clock and no time zone of its own:
 * both are passed in, so a test can say "it is 07:00 in Prague" and get one answer.
 */
class ReminderPlanTest {

    private val zone = ZoneId.of("Europe/Prague")
    private val day = LocalDate.of(2026, 12, 2).toEpochDay()

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDate.of(y, m, d).atTime(h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun aTimedTaskRingsAtItsMinute() =
        assertEquals(at(2026, 12, 2, 15, 0), ringAt(ReminderInput(1, day, 15 * 60, null), zone))

    @Test
    fun anUntimedTaskRingsAtEight() =
        assertEquals(at(2026, 12, 2, 8, 0), ringAt(ReminderInput(1, day, null, null), zone))

    @Test
    fun aSnoozeAheadOfTheDueTimeWins() = assertEquals(
        at(2026, 12, 2, 16, 0),
        ringAt(ReminderInput(1, day, 15 * 60, at(2026, 12, 2, 16, 0)), zone),
    )

    @Test
    fun aSnoozeBeforeTheDueTimeIsIgnoredAndTheDueTimeStands() = assertEquals(
        at(2026, 12, 2, 15, 0),
        ringAt(ReminderInput(1, day, 15 * 60, at(2026, 12, 2, 9, 0)), zone),
    )

    @Test
    fun aTaskWithNoDateNeverRings() =
        assertNull(ringAt(ReminderInput(1, null, 15 * 60, null), zone))

    /** An Inbox item cannot be snoozed into ringing: with no date there is no reminder to move. */
    @Test
    fun aTaskWithNoDateButASnoozeStillNeverRings() =
        assertNull(ringAt(ReminderInput(1, null, null, at(2026, 12, 2, 16, 0)), zone))

    /**
     * 2027-03-28 02:30 does not exist in Europe/Prague — the clocks go forward at 02:00 — and a
     * task dated that day with that minute must still produce a real instant rather than throwing.
     */
    @Test
    fun theHourSkippedByTheSpringForwardStillGivesAnInstant() {
        val springForward = LocalDate.of(2027, 3, 28).toEpochDay()
        assertEquals(
            at(2027, 3, 28, 3, 30),
            ringAt(ReminderInput(1, springForward, 2 * 60 + 30, null), zone),
        )
    }

    @Test
    fun theEarliestFutureRingWins() {
        val tasks = listOf(
            ReminderInput(1, day, 15 * 60, null),
            ReminderInput(2, day, 9 * 60, null),
        )
        val ring = nextRing(tasks, at(2026, 12, 2, 7, 0), zone)
        assertEquals(at(2026, 12, 2, 9, 0), ring!!.atMillis)
        assertEquals(listOf(2L), ring.taskIds)
    }

    /** Six dated tasks with no time all ring at 08:00; every one of them has to be in the ring. */
    @Test
    fun everyTaskSharingTheEarliestInstantIsInTheRing() {
        val tasks = listOf(
            ReminderInput(7, day, null, null),
            ReminderInput(3, day, null, null),
            ReminderInput(9, day, 15 * 60, null),
        )
        val ring = nextRing(tasks, at(2026, 12, 2, 7, 0), zone)
        assertEquals(at(2026, 12, 2, 8, 0), ring!!.atMillis)
        assertEquals(listOf(3L, 7L), ring.taskIds)
    }

    /** Dating a task today with no time, at 10:00, must not fire an 08:00 notification. */
    @Test
    fun aRingThatHasPassedIsSkipped() =
        assertNull(nextRing(listOf(ReminderInput(1, day, null, null)), at(2026, 12, 2, 10, 0), zone))

    /** The receiver has just posted this instant; re-arming must not pick it up again. */
    @Test
    fun theInstantItselfIsNotInTheFuture() = assertNull(
        nextRing(listOf(ReminderInput(1, day, 8 * 60, null)), at(2026, 12, 2, 8, 0), zone),
    )

    @Test
    fun aPastRingDoesNotHideALaterOne() {
        val tasks = listOf(
            ReminderInput(1, day, null, null),
            ReminderInput(2, day, 15 * 60, null),
        )
        val ring = nextRing(tasks, at(2026, 12, 2, 10, 0), zone)
        assertEquals(at(2026, 12, 2, 15, 0), ring!!.atMillis)
        assertEquals(listOf(2L), ring.taskIds)
    }

    @Test
    fun nothingComingIsNull() = assertNull(nextRing(emptyList(), at(2026, 12, 2, 10, 0), zone))

    @Test
    fun aTaskWithNoDateIsNotInAnyRing() = assertNull(
        nextRing(listOf(ReminderInput(1, null, 15 * 60, null)), at(2026, 12, 2, 7, 0), zone),
    )
}

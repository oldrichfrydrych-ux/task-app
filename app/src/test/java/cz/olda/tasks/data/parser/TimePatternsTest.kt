package cz.olda.tasks.data.parser

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimePatternsTest {

    private val lists = listOf("Inbox", "☀️Osobní", "🌊Práce", "🎓Škola", "🧊Someday")

    /** A Friday, 16:30 — late enough that a bare afternoon time has already gone. */
    private val now = LocalDateTime.of(2026, 11, 27, 16, 30)

    private fun minuteOf(input: String, at: LocalDateTime = now) =
        parseQuickAdd(input, at, lists).dueMinute

    @Test
    fun oneToSevenReadAsAfternoon() {
        assertEquals(900, minuteOf("ve 3 zavolat"))
        assertEquals(900, minuteOf("at 3 call"))
        assertEquals(930, minuteOf("ve 3:30 zavolat"))
        assertEquals(1140, minuteOf("v 7 zavolat"))
    }

    @Test
    fun eightToTwentyThreeStandAsWritten() {
        assertEquals(480, minuteOf("v 8 zavolat"))
        assertEquals(900, minuteOf("v 15 zavolat"))
        assertEquals(1305, minuteOf("v 21:45 zavolat"))
    }

    @Test
    fun aColonNeedsNoPreposition() {
        val parsed = parseQuickAdd("15:30 zavolat", now, lists)
        assertEquals(930, parsed.dueMinute)
        assertEquals("zavolat", parsed.title)
    }

    /** The other half of the dot rule; `15. 3.` as a date is pinned in CalendarDatePatternsTest. */
    @Test
    fun theDotRuleMakesAnHourAndMinutes() {
        assertEquals(930, minuteOf("v 15.30 zavolat"))
    }

    @Test
    fun amAndPm() {
        assertEquals(900, minuteOf("3pm call"))
        assertEquals(420, minuteOf("7am call"))
        assertEquals(930, minuteOf("at 3:30 pm call"))
        assertEquals(0, minuteOf("at 12 am call"))
    }

    @Test
    fun aMorningMarkerTurnsTheAfternoonRuleOff() {
        assertEquals(180, minuteOf("ve 3 rano zavolat"))
        assertEquals(180, minuteOf("at 3 am call"))
        assertEquals(180, minuteOf("ve 3 dopoledne zavolat"))
    }

    @Test
    fun hourSuffix() {
        assertEquals(900, minuteOf("v 15h zavolat"))
        assertEquals(900, minuteOf("ve 3 hod zavolat"))
    }

    @Test
    fun theNamedHours() {
        assertEquals(480, minuteOf("rano zavolat"))
        assertEquals(600, minuteOf("dopoledne zavolat"))
        assertEquals(720, minuteOf("v poledne zavolat"))
        assertEquals(900, minuteOf("odpoledne zavolat"))
        assertEquals(1140, minuteOf("vecer zavolat"))
        assertEquals(1320, minuteOf("v noci zavolat"))
        assertEquals(480, minuteOf("morning call"))
        assertEquals(720, minuteOf("noon call"))
        assertEquals(1140, minuteOf("evening call"))
    }

    @Test
    fun odpoledneIsNotPoledne() {
        assertEquals(900, minuteOf("odpoledne zavolat"))
    }

    @Test
    fun tonightIsTodayAtSeven() {
        val parsed = parseQuickAdd("tonight call Petr", now, lists)
        assertEquals(LocalDate.of(2026, 11, 27), parsed.dueDate)
        assertEquals(1140, parsed.dueMinute)
        assertEquals("call Petr", parsed.title)
    }

    /** Olda's call on 27 Nov: a bare time that has gone means the next one. */
    @Test
    fun aTimeWithNoDayThatHasGoneMeansTomorrow() {
        val parsed = parseQuickAdd("ve 3 zavolat", now, lists)
        assertEquals(LocalDate.of(2026, 11, 28), parsed.dueDate)
        assertEquals(900, parsed.dueMinute)
    }

    @Test
    fun aTimeWithNoDayStillToComeMeansToday() {
        val morning = LocalDateTime.of(2026, 11, 27, 11, 0)
        assertEquals(LocalDate.of(2026, 11, 27), parseQuickAdd("ve 3 zavolat", morning, lists).dueDate)
    }

    @Test
    fun exactlyNowCountsAsGone() {
        val three = LocalDateTime.of(2026, 11, 27, 15, 0)
        assertEquals(LocalDate.of(2026, 11, 28), parseQuickAdd("ve 3 zavolat", three, lists).dueDate)
    }

    @Test
    fun aDaySaidOutLoudAlwaysWins() {
        val parsed = parseQuickAdd("dnes ve 3 zavolat", now, lists)
        assertEquals(LocalDate.of(2026, 11, 27), parsed.dueDate)
        assertEquals(900, parsed.dueMinute)
        assertEquals("zavolat", parsed.title)
    }

    @Test
    fun aBareNumberIsNeverATime() {
        val parsed = parseQuickAdd("koupit 3 rohliky", now, lists)
        assertNull(parsed.dueMinute)
        assertNull(parsed.dueDate)
        assertEquals("koupit 3 rohliky", parsed.title)
    }
}

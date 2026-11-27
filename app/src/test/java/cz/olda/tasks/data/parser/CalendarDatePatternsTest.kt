package cz.olda.tasks.data.parser

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarDatePatternsTest {

    private val lists = listOf("Inbox", "☀️Osobní", "🌊Práce", "🎓Škola", "🧊Someday")

    /** A Friday, 16:30. */
    private val now = LocalDateTime.of(2026, 11, 27, 16, 30)

    private fun dateOf(input: String) = parseQuickAdd(input, now, lists).dueDate

    @Test
    fun czechNumericForms() {
        listOf("24. 12.", "24.12.", "24. 12")
            .forEach { assertEquals(it, LocalDate.of(2026, 12, 24), dateOf("$it zavolat")) }
    }

    @Test
    fun withAYear() {
        assertEquals(LocalDate.of(2026, 12, 24), dateOf("24.12.2026 zavolat"))
        assertEquals(LocalDate.of(2026, 12, 24), dateOf("24. 12. 2026 zavolat"))
        assertEquals(LocalDate.of(2027, 1, 3), dateOf("3.1.2027 zavolat"))
    }

    @Test
    fun czechMonthNames() {
        assertEquals(LocalDate.of(2026, 12, 24), dateOf("24. prosince zavolat"))
        assertEquals(LocalDate.of(2026, 12, 24), dateOf("24. prosinec zavolat"))
        assertEquals(LocalDate.of(2027, 1, 1), dateOf("1. ledna zavolat"))
        assertEquals(LocalDate.of(2027, 6, 3), dateOf("3. cervna zavolat"))
        assertEquals(LocalDate.of(2027, 7, 3), dateOf("3. cervence zavolat"))
    }

    @Test
    fun englishMonthNames() {
        assertEquals(LocalDate.of(2026, 12, 24), dateOf("Dec 24 call"))
        assertEquals(LocalDate.of(2026, 12, 24), dateOf("24 December call"))
        assertEquals(LocalDate.of(2026, 12, 24), dateOf("dec 24 call"))
    }

    @Test
    fun aPastDayWithoutAYearRollsToNextYear() {
        assertEquals(LocalDate.of(2027, 1, 3), dateOf("3. 1. zavolat"))
    }

    @Test
    fun todayWithoutAYearIsThisYear() {
        assertEquals(LocalDate.of(2026, 11, 27), dateOf("27. 11. zavolat"))
    }

    @Test
    fun anImpossibleDateIsNotADate() {
        listOf("31. 2.", "32. 1.", "1. 13.").forEach { assertNull(it, dateOf("$it zavolat")) }
    }

    /** The other half of the dot rule; `v 15.30` as a time is pinned in TimePatternsTest. */
    @Test
    fun theDotRuleKeepsADateADate() {
        assertEquals(LocalDate.of(2027, 3, 15), dateOf("15. 3. zavolat"))
    }

    @Test
    fun aPrepositionIsAbsorbed() {
        val parsed = parseQuickAdd("ve 3. 12. zavolat", now, lists)
        assertEquals(LocalDate.of(2026, 12, 3), parsed.dueDate)
        assertNull(parsed.dueMinute)
        assertEquals("zavolat", parsed.title)
    }

    @Test
    fun aBareNumberPairWithoutTheFinalDotIsNothing() {
        val parsed = parseQuickAdd("24.12 zavolat", now, lists)
        assertNull(parsed.dueDate)
        assertEquals("24.12 zavolat", parsed.title)
    }
}

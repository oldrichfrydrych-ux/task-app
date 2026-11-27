package cz.olda.tasks.data.parser

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelativePatternsTest {

    private val lists = listOf("Inbox", "☀️Osobní", "🌊Práce", "🎓Škola", "🧊Someday")

    /** A Friday, 16:30. */
    private val now = LocalDateTime.of(2026, 11, 27, 16, 30)
    private val lateEvening = LocalDateTime.of(2026, 11, 27, 23, 30)

    private fun dateOf(input: String, at: LocalDateTime = now) =
        parseQuickAdd(input, at, lists).dueDate

    @Test
    fun czechDays() {
        assertEquals(LocalDate.of(2026, 11, 30), dateOf("za 3 dny zavolat"))
        assertEquals(LocalDate.of(2026, 11, 28), dateOf("za den zavolat"))
        assertEquals(LocalDate.of(2026, 12, 2), dateOf("za 5 dni zavolat"))
    }

    @Test
    fun czechWeeksMonthsYears() {
        assertEquals(LocalDate.of(2026, 12, 4), dateOf("za tyden zavolat"))
        assertEquals(LocalDate.of(2026, 12, 11), dateOf("za 2 tydny zavolat"))
        assertEquals(LocalDate.of(2026, 12, 27), dateOf("za mesic zavolat"))
        assertEquals(LocalDate.of(2027, 11, 27), dateOf("za rok zavolat"))
    }

    @Test
    fun englishForms() {
        assertEquals(LocalDate.of(2026, 11, 30), dateOf("in 3 days call"))
        assertEquals(LocalDate.of(2026, 11, 28), dateOf("in a day call"))
        assertEquals(LocalDate.of(2026, 12, 4), dateOf("in a week call"))
        assertEquals(LocalDate.of(2026, 12, 11), dateOf("in 2 weeks call"))
        assertEquals(LocalDate.of(2026, 12, 27), dateOf("in a month call"))
        assertEquals(LocalDate.of(2027, 11, 27), dateOf("in a year call"))
    }

    @Test
    fun hoursSetBothFields() {
        val parsed = parseQuickAdd("za hodinu zavolat", now, lists)
        assertEquals(LocalDate.of(2026, 11, 27), parsed.dueDate)
        assertEquals(17 * 60 + 30, parsed.dueMinute)
        assertEquals("zavolat", parsed.title)
    }

    @Test
    fun minutesSetBothFields() {
        assertEquals(16 * 60 + 50, parseQuickAdd("za 20 minut zavolat", now, lists).dueMinute)
        assertEquals(17 * 60, parseQuickAdd("in 30 min call", now, lists).dueMinute)
        assertEquals(17 * 60 + 30, parseQuickAdd("in an hour call", now, lists).dueMinute)
    }

    @Test
    fun anHourAcrossMidnightRollsTheDate() {
        val parsed = parseQuickAdd("za hodinu zavolat", lateEvening, lists)
        assertEquals(LocalDate.of(2026, 11, 28), parsed.dueDate)
        assertEquals(30, parsed.dueMinute)
    }

    @Test
    fun manyMinutesAcrossMidnight() {
        val parsed = parseQuickAdd("za 90 minut zavolat", lateEvening, lists)
        assertEquals(LocalDate.of(2026, 11, 28), parsed.dueDate)
        assertEquals(60, parsed.dueMinute)
    }

    @Test
    fun theWholePhraseLeavesTheTitle() {
        assertEquals("koupit kabat", parseQuickAdd("za 3 dny koupit kabat", now, lists).title)
    }

    @Test
    fun aRelativeDateDoesNotAlsoSetATime() {
        assertNull(parseQuickAdd("za 3 dny zavolat", now, lists).dueMinute)
    }

    @Test
    fun itLosesToAnAlreadyTakenField() {
        val parsed = parseQuickAdd("zitra za 3 dny zavolat", now, lists)
        assertEquals(LocalDate.of(2026, 11, 28), parsed.dueDate)
        assertEquals("za 3 dny zavolat", parsed.title)
    }
}

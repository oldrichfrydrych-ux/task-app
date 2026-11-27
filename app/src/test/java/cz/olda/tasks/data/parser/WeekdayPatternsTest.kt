package cz.olda.tasks.data.parser

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekdayPatternsTest {

    private val lists = listOf("Inbox", "☀️Osobní", "🌊Práce", "🎓Škola", "🧊Someday")

    private val friday = LocalDateTime.of(2026, 11, 27, 16, 30)
    private val monday = LocalDateTime.of(2026, 11, 23, 10, 0)
    private val wednesday = LocalDateTime.of(2026, 11, 25, 10, 0)

    private fun dateOf(input: String, now: LocalDateTime = friday) =
        parseQuickAdd(input, now, lists).dueDate

    /** The rule Olda's own task "Číst při Týdenním resetu (pondělí)." exists to protect. */
    @Test
    fun aBareWeekdayIsNotADate() {
        assertNull(dateOf("Číst při Týdenním resetu (pondělí)."))
        assertNull(dateOf("pondeli zavolat"))
        assertNull(dateOf("monday call"))
    }

    @Test
    fun nearestIncludesToday() {
        assertEquals(LocalDate.of(2026, 11, 23), dateOf("v pondeli zavolat", monday))
    }

    @Test
    fun nearestIsTheComingOne() {
        assertEquals(LocalDate.of(2026, 11, 30), dateOf("v pondeli zavolat"))
        assertEquals(LocalDate.of(2026, 12, 2), dateOf("ve stredu zavolat"))
        assertEquals(LocalDate.of(2026, 11, 28), dateOf("v sobotu zavolat"))
    }

    @Test
    fun pristiIsTheFollowingWeek() {
        assertEquals(LocalDate.of(2026, 12, 4), dateOf("pristi patek zavolat", wednesday))
        assertEquals(LocalDate.of(2026, 12, 2), dateOf("příští středa zavolat", wednesday))
    }

    @Test
    fun pristiOnTheSameWeekdayIsSevenDaysOn() {
        assertEquals(LocalDate.of(2026, 11, 30), dateOf("pristi pondeli zavolat", monday))
    }

    @Test
    fun englishForms() {
        assertEquals(LocalDate.of(2026, 11, 30), dateOf("on monday call"))
        assertEquals(LocalDate.of(2026, 12, 4), dateOf("next friday call"))
        assertEquals(LocalDate.of(2026, 11, 29), dateOf("this sunday call"))
        assertEquals(LocalDate.of(2026, 11, 27), dateOf("on fri call"))
    }

    @Test
    fun thePrepositionLeavesTheTitle() {
        assertEquals("zavolat", parseQuickAdd("v pondeli zavolat", friday, lists).title)
        assertEquals("call Petr", parseQuickAdd("next friday call Petr", friday, lists).title)
    }
}

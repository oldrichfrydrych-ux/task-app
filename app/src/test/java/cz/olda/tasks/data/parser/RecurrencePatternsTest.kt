package cz.olda.tasks.data.parser

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurrencePatternsTest {

    private val lists = listOf("Inbox", "☀️Osobní", "🌊Práce", "🎓Škola", "🧊Someday")

    /** A Friday, 16:30 — late enough that three in the afternoon has gone. */
    private val now = LocalDateTime.of(2026, 11, 27, 16, 30)

    private fun ruleOf(input: String, at: LocalDateTime = now) =
        parseQuickAdd(input, at, lists).recurrence

    @Test
    fun theCzechAndEnglishForms() {
        assertEquals("FREQ=DAILY", ruleOf("kazdy den cvicit"))
        assertEquals("FREQ=DAILY", ruleOf("denne cvicit"))
        assertEquals("FREQ=DAILY", ruleOf("every day exercise"))
        assertEquals("FREQ=DAILY", ruleOf("daily exercise"))
        assertEquals("FREQ=WEEKLY;BYDAY=MO", ruleOf("kazde pondeli porada"))
        assertEquals("FREQ=WEEKLY;BYDAY=MO", ruleOf("every monday meeting"))
        assertEquals("FREQ=WEEKLY;BYDAY=FR", ruleOf("kazdy patek porada"))
        assertEquals("FREQ=WEEKLY", ruleOf("kazdy tyden porada"))
        assertEquals("FREQ=WEEKLY", ruleOf("weekly meeting"))
        assertEquals("FREQ=MONTHLY", ruleOf("kazdy mesic platba"))
        assertEquals("FREQ=MONTHLY", ruleOf("monthly payment"))
        assertEquals("FREQ=YEARLY", ruleOf("kazdy rok revize"))
        assertEquals("FREQ=YEARLY", ruleOf("yearly checkup"))
        assertEquals("FREQ=DAILY;INTERVAL=2", ruleOf("kazde 2 dny cvicit"))
        assertEquals("FREQ=DAILY;INTERVAL=3", ruleOf("every 3 days exercise"))
        assertEquals("FREQ=WEEKLY;INTERVAL=2", ruleOf("kazde 2 tydny porada"))
    }

    @Test
    fun everyKazdyEnding() {
        listOf("kazdy", "kazda", "kazde", "každý", "každé")
            .forEach { assertEquals(it, "FREQ=DAILY", ruleOf("$it den cvicit")) }
    }

    @Test
    fun aRepeatSetsItsFirstDay() {
        assertEquals(LocalDate.of(2026, 11, 27), parseQuickAdd("kazdy den cvicit", now, lists).dueDate)
        assertEquals(LocalDate.of(2026, 11, 30), parseQuickAdd("kazde pondeli porada", now, lists).dueDate)
    }

    @Test
    fun aRepeatWithNoTimeNeverRolls() {
        val lateEvening = LocalDateTime.of(2026, 11, 27, 23, 59)
        assertEquals(LocalDate.of(2026, 11, 27), parseQuickAdd("kazdy den cvicit", lateEvening, lists).dueDate)
    }

    /** The builder's call after the design review: an implied first day moves on, a typed one does not. */
    @Test
    fun anImpliedDayRollsOnWhenItsTimeHasGone() {
        val parsed = parseQuickAdd("kazdy den ve 3 cvicit", now, lists)
        assertEquals(LocalDate.of(2026, 11, 28), parsed.dueDate)
        assertEquals(900, parsed.dueMinute)
    }

    @Test
    fun anImpliedDayStaysWhenItsTimeIsStillToCome() {
        val morning = LocalDateTime.of(2026, 11, 27, 11, 0)
        val parsed = parseQuickAdd("kazdy den ve 3 cvicit", morning, lists)
        assertEquals(LocalDate.of(2026, 11, 27), parsed.dueDate)
        assertEquals(900, parsed.dueMinute)
    }

    @Test
    fun aWeeklyRepeatRollsByAWeek() {
        val monday = LocalDateTime.of(2026, 11, 23, 11, 0)
        val parsed = parseQuickAdd("kazde pondeli v 9 porada", monday, lists)
        assertEquals(LocalDate.of(2026, 11, 30), parsed.dueDate)
        assertEquals(540, parsed.dueMinute)
    }

    @Test
    fun aTypedDayIsNeverRolled() {
        val parsed = parseQuickAdd("dnes kazdy den ve 3 cvicit", now, lists)
        assertEquals(LocalDate.of(2026, 11, 27), parsed.dueDate)
        assertEquals("FREQ=DAILY", parsed.recurrence)
    }

    @Test
    fun theWholePhraseLeavesTheTitle() {
        assertEquals("porada", parseQuickAdd("kazde pondeli v 9 porada", now, lists).title)
        assertEquals("cvicit", parseQuickAdd("kazde 2 dny cvicit", now, lists).title)
    }

    @Test
    fun kazdyAloneIsNotARepeat() {
        val parsed = parseQuickAdd("kazdy clovek je jiny", now, lists)
        assertNull(parsed.recurrence)
        assertEquals("kazdy clovek je jiny", parsed.title)
    }
}

package cz.olda.tasks.data.parser

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayPatternsTest {

    private val lists = listOf("Inbox", "☀️Osobní", "🌊Práce", "🎓Škola", "🧊Someday")

    /** A Friday, 16:30. */
    private val now = LocalDateTime.of(2026, 11, 27, 16, 30)

    private fun dateOf(input: String) = parseQuickAdd(input, now, lists).dueDate

    @Test
    fun theThreeDayWordsInBothLanguages() {
        listOf("dnes", "dneska", "today").forEach { assertEquals(it, LocalDate.of(2026, 11, 27), dateOf("$it zavolat")) }
        listOf("zitra", "zejtra", "tomorrow").forEach { assertEquals(it, LocalDate.of(2026, 11, 28), dateOf("$it zavolat")) }
        listOf("pozitri", "day after tomorrow").forEach { assertEquals(it, LocalDate.of(2026, 11, 29), dateOf("$it zavolat")) }
    }

    @Test
    fun worksWithDiacritics() {
        val parsed = parseQuickAdd("Zítra zavolat", now, lists)
        assertEquals(LocalDate.of(2026, 11, 28), parsed.dueDate)
        assertEquals("zavolat", parsed.title)
    }

    @Test
    fun firstDateWinsAndTheLoserStaysInTheTitle() {
        val parsed = parseQuickAdd("dnes zitra zavolat", now, lists)
        assertEquals(LocalDate.of(2026, 11, 27), parsed.dueDate)
        assertEquals("zitra zavolat", parsed.title)
    }

    @Test
    fun notInsideAWord() {
        listOf("dnesek resit", "zitrat resit", "pozitrik").forEach { assertNull(it, dateOf(it)) }
    }

    /**
     * Nothing in Phase 2 reads the tokens; the highlighting in a later phase does, so they are
     * pinned here, on the first family that produces any.
     */
    @Test
    fun theTokensSayWhereEachPhraseWas() {
        assertEquals(
            listOf(ParsedToken(0, 5, TokenKind.DATE, "Zítra")),
            parseQuickAdd("Zítra zavolat", now, lists).tokens,
        )
    }
}

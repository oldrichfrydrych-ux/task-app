package cz.olda.tasks.data.parser

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Appendix A of the design doc: Olda's own live task titles, read from a database backup on
 * 27 Nov. None of them may gain a date, a time, a list, a tag, a priority or a repeat, and
 * none may lose a word. This is the backstop every pattern family is added against — an
 * over-eager pattern shows up here first.
 */
class ParserNegativeCasesTest {

    private val realTitles = listOf(
        "Číst při Týdenním resetu (pondělí).",
        "Koupit zimní kabát",
        "Napsat na ekonomku",
        "Odevzdat Martině dokumentaci AUI",
        "Opravit Fluxus automatizace",
        "Udělat auditní scrapper",
        "Vyzkoušet facebook MUSE",
        "Vyzkoušet speech stuff s ai",
        "Fluxus: export PDF reportu",
        "Aplikace na tvorbu formulářů.",
        "Alea, Více rolí?",
        "Penzijní spoření",
        "napsat katce Kobušové -",
        "Zastav se pro přítomný okamžik.",
        "- I have no enemies, even if the enemy is myself.",
    )

    private val lists = listOf("Inbox", "☀️Osobní", "🌊Práce", "🎓Škola", "🧊Someday")

    /** A Friday, 16:30 — late enough that a bare afternoon time has already gone. */
    private val now = LocalDateTime.of(2026, 11, 27, 16, 30)

    @Test
    fun hisRealTitlesComeOutUntouched() {
        realTitles.forEach { title ->
            assertEquals(title, ParsedInput(title = title), parseQuickAdd(title, now, lists))
        }
    }

    @Test
    fun aLongPastedLineIsNotMangledOrSlow() {
        val pasted = realTitles.joinToString("\n").repeat(20)
        assertEquals(
            pasted.replace(Regex("\\s+"), " ").trim(),
            parseQuickAdd(pasted, now, lists).title,
        )
    }
}

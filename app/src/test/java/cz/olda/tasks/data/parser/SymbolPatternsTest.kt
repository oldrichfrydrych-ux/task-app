package cz.olda.tasks.data.parser

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SymbolPatternsTest {

    /** Olda's real lists, emoji and all: the emoji lives inside `name`, not in `icon`. */
    private val lists = listOf("Inbox", "☀️Osobní", "🌊Práce", "🎓Škola", "🧊Someday")

    private val now = LocalDateTime.of(2026, 11, 27, 16, 30)

    private fun parse(input: String) = parseQuickAdd(input, now, lists)

    @Test
    fun anExactNameMatchesPastTheEmoji() {
        assertEquals("🌊Práce", parse("zavolat #prace").listName)
        assertEquals("☀️Osobní", parse("zavolat #osobni").listName)
        assertEquals("Inbox", parse("zavolat #inbox").listName)
    }

    @Test
    fun diacriticsAndCaseDoNotMatter() {
        assertEquals("🌊Práce", parse("zavolat #Práce").listName)
        assertEquals("🌊Práce", parse("zavolat #PRACE").listName)
    }

    @Test
    fun aUniquePrefixIsEnough() {
        assertEquals("🌊Práce", parse("zavolat #pra").listName)
        assertEquals("🎓Škola", parse("zavolat #skol").listName)
    }

    @Test
    fun anAmbiguousPrefixMatchesNothing() {
        val parsed = parse("zavolat #s")
        assertNull(parsed.listName)
        assertEquals("zavolat #s", parsed.title)
    }

    @Test
    fun anUnknownListStaysInTheTitle() {
        val parsed = parse("zavolat #xyz")
        assertNull(parsed.listName)
        assertEquals("zavolat #xyz", parsed.title)
    }

    @Test
    fun aSecondListIsIgnoredAndLeftInTheTitle() {
        val parsed = parse("zavolat #prace #skola")
        assertEquals("🌊Práce", parsed.listName)
        assertEquals("zavolat #skola", parsed.title)
    }

    @Test
    fun tagsAreAlwaysRecognisedAndAccumulate() {
        val parsed = parse("zavolat @telefon @rychle")
        assertEquals(listOf("telefon", "rychle"), parsed.tagNames)
        assertEquals("zavolat", parsed.title)
    }

    @Test
    fun anEmailIsNotATag() {
        val parsed = parse("napsat na jan@firma.cz")
        assertEquals(emptyList<String>(), parsed.tagNames)
        assertEquals("napsat na jan@firma.cz", parsed.title)
    }

    @Test
    fun priorities() {
        (1..4).forEach { assertEquals(it, parse("zavolat p$it").priority) }
        assertEquals(1, parse("zavolat P1").priority)
        assertEquals("zavolat", parse("zavolat p1").title)
    }

    @Test
    fun notAPriorityInsideAWord() {
        listOf("tisknout typ1a", "zavolat p5", "zavolat p0").forEach {
            assertNull(it, parse(it).priority)
            assertEquals(it, it, parse(it).title)
        }
    }

    /** The line the whole phase is judged on. */
    @Test
    fun theWholeLineFromTheSpec() {
        val parsed = parse("zitra ve 3 zavolat na urad #prace")
        assertEquals(LocalDate.of(2026, 11, 28), parsed.dueDate)
        assertEquals(900, parsed.dueMinute)
        assertEquals("🌊Práce", parsed.listName)
        assertEquals("zavolat na urad", parsed.title)
    }

    @Test
    fun aListNameFoldsPastItsEmojiAndPunctuation() {
        assertEquals("prace", listMatchName("🌊Práce"))
        assertEquals("osobni", listMatchName("☀️Osobní"))
        assertEquals("inbox", listMatchName("Inbox"))
    }
}

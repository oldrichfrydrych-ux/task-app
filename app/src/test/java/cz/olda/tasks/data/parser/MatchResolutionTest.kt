package cz.olda.tasks.data.parser

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchResolutionTest {

    private fun date(start: Int, end: Int) =
        Candidate(start, end, TokenKind.DATE, date = LocalDate.of(2026, 11, 28))

    private fun time(start: Int, end: Int) =
        Candidate(start, end, TokenKind.TIME, minute = 900)

    private fun tag(start: Int, end: Int, name: String) =
        Candidate(start, end, TokenKind.TAG, tagName = name)

    /** "ve 3. 12." — the time and the date both begin at the preposition, so the date wins. */
    @Test
    fun longerWinsAtTheSameStart() {
        assertEquals(listOf(date(0, 9)), resolve(listOf(date(0, 4), date(0, 9))))
    }

    /** "dnes zitra zavolat" — today wins and "zitra" is left in the title, though it is longer. */
    @Test
    fun anEarlierPhraseBeatsALaterLongerOne() {
        assertEquals(listOf(date(0, 4)), resolve(listOf(date(0, 4), date(5, 15))))
    }

    @Test
    fun leftmostWinsAtEqualLength() {
        assertEquals(listOf(date(0, 5)), resolve(listOf(date(10, 15), date(0, 5))))
    }

    @Test
    fun secondValueForAFieldIsRejected() {
        assertEquals(listOf(date(0, 5)), resolve(listOf(date(0, 5), date(10, 14))))
    }

    @Test
    fun tagsAccumulate() {
        val tags = listOf(tag(0, 4, "a"), tag(5, 9, "b"), tag(10, 14, "c"))
        assertEquals(tags, resolve(tags))
    }

    @Test
    fun aCandidateNeedingTwoFreeFieldsIsRejectedWhenOneIsTaken() {
        val taken = time(0, 15)
        val both = Candidate(20, 25, TokenKind.DATE, date = LocalDate.of(2026, 11, 28), minute = 600)
        assertEquals(listOf(taken), resolve(listOf(taken, both)))
    }

    @Test
    fun acceptedCandidatesComeBackInReadingOrder() {
        assertEquals(listOf(date(0, 5), time(6, 10)), resolve(listOf(time(6, 10), date(0, 5))))
    }

    @Test
    fun cutRemovesRangesAndTidiesSpacing() {
        val input = "zitra ve 3 zavolat na urad"
        assertEquals("zavolat na urad", cutTitle(input, listOf(date(0, 5), time(6, 10))))
    }

    @Test
    fun cutCollapsesNewlinesAndSpaceBeforePunctuation() {
        assertEquals("volat Petrovi.", cutTitle("zitra  volat\n\nPetrovi .", listOf(date(0, 5))))
    }

    @Test
    fun cutOfEverythingLeavesAnEmptyTitle() {
        assertEquals("", cutTitle("zitra", listOf(date(0, 5))))
    }
}

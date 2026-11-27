package cz.olda.tasks.data.parser

import java.time.LocalDate

/**
 * The three day words, in both languages. These are the only date phrases that match bare,
 * with nothing in front of them: nobody writes "dnes" or "zitra" in a title by accident.
 *
 * Longer spellings come first inside each alternation so the regex cannot stop at a prefix.
 */
private val dayWords: List<Pair<Regex, Long>> = listOf(
    wordPattern("dneska|dnes|today") to 0L,
    wordPattern("zitra|zejtra|tomorrow") to 1L,
    wordPattern("pozitri|day after tomorrow") to 2L,
)

internal fun dayCandidates(folded: String, today: LocalDate): List<Candidate> =
    dayWords.flatMap { (pattern, offset) ->
        pattern.findAll(folded).map { match ->
            Candidate(
                start = match.range.first,
                end = match.range.last + 1,
                kind = TokenKind.DATE,
                date = today.plusDays(offset),
            )
        }
    }

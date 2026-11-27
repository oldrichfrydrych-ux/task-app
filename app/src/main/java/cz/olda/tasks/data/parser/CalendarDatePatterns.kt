package cz.olda.tasks.data.parser

import java.time.LocalDate

/**
 * Month names, folded. Czech appears in the genitive ("24. prosince") and the nominative
 * ("24. prosinec"), because both get typed; English in full and in the usual abbreviations.
 */
private val monthNames: Map<String, Int> = mapOf(
    "ledna" to 1, "leden" to 1,
    "unora" to 2, "unor" to 2,
    "brezna" to 3, "brezen" to 3,
    "dubna" to 4, "duben" to 4,
    "kvetna" to 5, "kveten" to 5,
    "cervna" to 6, "cerven" to 6,
    "cervence" to 7, "cervenec" to 7,
    "srpna" to 8, "srpen" to 8,
    "zari" to 9,
    "rijna" to 10, "rijen" to 10,
    "listopadu" to 11, "listopad" to 11,
    "prosince" to 12, "prosinec" to 12,
    "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6,
    "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11,
    "december" to 12,
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6, "jul" to 7, "aug" to 8,
    "sept" to 9, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
)

/** Longest first, so the regex cannot stop at "cerven" inside "cervence". */
private val anyMonth = monthNames.keys.sortedByDescending { it.length }.joinToString("|")

/** `v`, `ve` or `dne` in front of a date is absorbed, so the title keeps no dangling word. */
private const val PREPOSITION = "(?:(?:v|ve|dne)\\s+)?"

/**
 * The numeric forms, and the dot rule they encode. A date carries the dots Czech writing gives
 * it: a trailing dot (`24. 12.`, `24.12.`), a space after the first one (`24. 12`), or a year
 * (`24.12.2026`). That is what tells `15. 3.` (the fifteenth of March) apart from `15.30`
 * (half past three), and it is why `24.12` is recognised as neither and stays in the title.
 *
 * All of these begin at the same character when a preposition is absorbed, so resolution's
 * longest-at-the-same-start rule picks the fullest reading of "24. 12. 2026".
 *
 * Each entry says which capture groups hold the day, the month and the year, where group 0
 * means "not written".
 */
private class DatePattern(
    val regex: Regex,
    val day: Int,
    val month: Int,
    val year: Int = 0,
    val monthIsAName: Boolean = false,
)

private val datePatterns = listOf(
    DatePattern(wordPattern("$PREPOSITION(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{4})"), day = 1, month = 2, year = 3),
    DatePattern(wordPattern("$PREPOSITION(\\d{1,2})\\.\\s*(\\d{1,2})\\."), day = 1, month = 2),
    DatePattern(wordPattern("$PREPOSITION(\\d{1,2})\\.\\s+(\\d{1,2})"), day = 1, month = 2),
    DatePattern(
        wordPattern("$PREPOSITION(\\d{1,2})\\.?\\s+($anyMonth)(?:\\s+(\\d{4}))?"),
        day = 1, month = 2, year = 3, monthIsAName = true,
    ),
    DatePattern(
        wordPattern("$PREPOSITION($anyMonth)\\s+(\\d{1,2})(?:\\s+(\\d{4}))?"),
        day = 2, month = 1, year = 3, monthIsAName = true,
    ),
)

internal fun calendarDateCandidates(folded: String, today: LocalDate): List<Candidate> =
    datePatterns.flatMap { pattern ->
        pattern.regex.findAll(folded).mapNotNull { match ->
            val groups = match.groupValues
            val day = groups[pattern.day].toIntOrNull() ?: return@mapNotNull null
            val month = if (pattern.monthIsAName) {
                monthNames[groups[pattern.month]] ?: return@mapNotNull null
            } else {
                groups[pattern.month].toIntOrNull() ?: return@mapNotNull null
            }
            val year = pattern.year.takeIf { it > 0 }?.let { groups[it] }.orEmpty()
            val date = resolveDate(day, month, year, today) ?: return@mapNotNull null
            Candidate(
                start = match.range.first,
                end = match.range.last + 1,
                kind = TokenKind.DATE,
                date = date,
            )
        }
    }

/**
 * A year that was not written means **this year if that day is today or still ahead, otherwise
 * next year** — so "3. 1." typed in November means next January. An impossible day gives null,
 * so the words stay in the title rather than becoming a wrong date.
 */
private fun resolveDate(day: Int, month: Int, yearText: String, today: LocalDate): LocalDate? {
    if (yearText.isNotEmpty()) return dateOrNull(yearText.toInt(), month, day)
    val thisYear = dateOrNull(today.year, month, day)
    if (thisYear != null && !thisYear.isBefore(today)) return thisYear
    return dateOrNull(today.year + 1, month, day)
}

private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
    runCatching { LocalDate.of(year, month, day) }.getOrNull()

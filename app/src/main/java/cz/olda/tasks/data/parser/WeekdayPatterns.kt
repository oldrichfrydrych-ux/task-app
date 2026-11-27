package cz.olda.tasks.data.parser

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Every spelling of a weekday the parser accepts, folded, in Czech and English. Czech appears
 * in both the nominative and the form that follows "v" or "ve", because both get typed.
 *
 * There are no Czech abbreviations: "po" is also the word "after", "st" and "ne" are as common
 * inside ordinary sentences, and a wrong date is worse than a few more keystrokes.
 *
 * Shared with `RecurrencePatterns.kt`, which needs the same mapping for "kazde pondeli".
 */
internal val weekdayNames: Map<String, DayOfWeek> = mapOf(
    "pondeli" to DayOfWeek.MONDAY,
    "utery" to DayOfWeek.TUESDAY,
    "streda" to DayOfWeek.WEDNESDAY,
    "stredu" to DayOfWeek.WEDNESDAY,
    "ctvrtek" to DayOfWeek.THURSDAY,
    "patek" to DayOfWeek.FRIDAY,
    "sobota" to DayOfWeek.SATURDAY,
    "sobotu" to DayOfWeek.SATURDAY,
    "nedele" to DayOfWeek.SUNDAY,
    "nedeli" to DayOfWeek.SUNDAY,
    "monday" to DayOfWeek.MONDAY,
    "tuesday" to DayOfWeek.TUESDAY,
    "wednesday" to DayOfWeek.WEDNESDAY,
    "thursday" to DayOfWeek.THURSDAY,
    "friday" to DayOfWeek.FRIDAY,
    "saturday" to DayOfWeek.SATURDAY,
    "sunday" to DayOfWeek.SUNDAY,
    "mon" to DayOfWeek.MONDAY,
    "tue" to DayOfWeek.TUESDAY,
    "wed" to DayOfWeek.WEDNESDAY,
    "thu" to DayOfWeek.THURSDAY,
    "fri" to DayOfWeek.FRIDAY,
    "sat" to DayOfWeek.SATURDAY,
    "sun" to DayOfWeek.SUNDAY,
)

/** Longest first, so the regex cannot stop at "mon" inside "monday". */
private val anyWeekday = weekdayNames.keys.sortedByDescending { it.length }.joinToString("|")

/** "v pondeli", "ve stredu", "on monday", "this sunday" — the nearest one, today included. */
private val nearestWeekday = wordPattern("(?:v|ve|on|this|tento|tuto|toto)\\s+($anyWeekday)")

/** "pristi patek", "next friday" — that weekday in the following Monday-to-Sunday week. */
private val followingWeekday = wordPattern("(?:pristi|next)\\s+($anyWeekday)")

internal fun weekdayCandidates(folded: String, today: LocalDate): List<Candidate> =
    nearestWeekday.candidates(folded) { day -> today.with(TemporalAdjusters.nextOrSame(day)) } +
        followingWeekday.candidates(folded) { day ->
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(1)
                .plusDays((day.value - 1).toLong())
        }

private fun Regex.candidates(folded: String, dateOf: (DayOfWeek) -> LocalDate): List<Candidate> =
    findAll(folded).mapNotNull { match ->
        val day = weekdayNames[match.groupValues[1]] ?: return@mapNotNull null
        Candidate(
            start = match.range.first,
            end = match.range.last + 1,
            kind = TokenKind.DATE,
            date = dateOf(day),
        )
    }.toList()

package cz.olda.tasks.data.parser

import java.time.LocalDate

/** The words that announce a clock time, so that a bare number never becomes one. */
private const val PREPOSITION = "(?:v|ve|at|od|kolem|okolo)\\s*"

/** A marker after the hour that says which half of the day is meant. */
private const val MARKER = "(?:\\s*(am|pm|rano|dopoledne|morning|odpoledne|vecer|evening|afternoon))?"

private val morningMarkers = setOf("am", "rano", "dopoledne", "morning")
private val afternoonMarkers = setOf("pm", "odpoledne", "vecer", "evening", "afternoon")

/**
 * A colon is unambiguous, so it needs no preposition: "15:30 zavolat" is a time. A dot is not —
 * `15. 3.` is the fifteenth of March — so the dot form asks for `v`, `ve` or `at` in front. See
 * the dot rule in `CalendarDatePatterns.kt`, which holds the other half.
 */
private class TimePattern(
    val regex: Regex,
    val hour: Int,
    val marker: Int,
    /** 0 when the pattern has no minutes group. */
    val minutes: Int = 0,
)

private val timePatterns = listOf(
    // "15:30", "v 15:30", "at 3:30 pm"
    TimePattern(wordPattern("(?:$PREPOSITION)?(\\d{1,2}):(\\d{2})$MARKER"), hour = 1, minutes = 2, marker = 3),
    // "v 15.30" — the dot form, preposition required
    TimePattern(wordPattern("$PREPOSITION(\\d{1,2})\\.(\\d{2})$MARKER"), hour = 1, minutes = 2, marker = 3),
    // "ve 3", "v 15", "v 15h", "ve 3 hod", "ve 3 rano"
    TimePattern(wordPattern("$PREPOSITION(\\d{1,2})(?:\\s*(?:hod(?:in)?|h))?$MARKER"), hour = 1, marker = 2),
    // "3pm", "7am" — the marker makes these unambiguous on their own
    TimePattern(wordPattern("(\\d{1,2})\\s*(am|pm)"), hour = 1, marker = 2),
)

/**
 * The vague ones, in the hours Olda picked on 27 Nov: 8 · 10 · 12 · 15 · 19 · 22. "vecer" on
 * its own is only an hour, so an evening that has already gone lands on tomorrow like any
 * other bare time.
 */
private val namedHours: Map<String, Int> = mapOf(
    "rano" to 8 * 60,
    "morning" to 8 * 60,
    "dopoledne" to 10 * 60,
    "poledne" to 12 * 60,
    "noon" to 12 * 60,
    "odpoledne" to 15 * 60,
    "afternoon" to 15 * 60,
    "vecer" to 19 * 60,
    "evening" to 19 * 60,
    "noci" to 22 * 60,
    "night" to 22 * 60,
)

/** Longest first, so the regex cannot stop at "poledne" inside "odpoledne". */
private val anyNamedHour = namedHours.keys.sortedByDescending { it.length }.joinToString("|")

private val namedHour = wordPattern("(?:(?:v|ve)\\s+)?($anyNamedHour)")

/** The one time phrase that also names a day, so it claims the date rather than implying it. */
private val tonight = wordPattern("tonight|dnes\\s+vecer")

internal fun timeCandidates(folded: String, today: LocalDate): List<Candidate> =
    timePatterns.flatMap { pattern ->
        pattern.regex.findAll(folded).mapNotNull { match ->
            val groups = match.groupValues
            val minute = minuteOfDay(
                hour = groups[pattern.hour].toIntOrNull() ?: return@mapNotNull null,
                minutes = if (pattern.minutes == 0) 0 else groups[pattern.minutes].toIntOrNull() ?: 0,
                marker = groups[pattern.marker].takeIf { it.isNotEmpty() },
            ) ?: return@mapNotNull null
            Candidate(match.range.first, match.range.last + 1, TokenKind.TIME, minute = minute)
        }
    } +
        namedHour.findAll(folded).mapNotNull { match ->
            val minute = namedHours[match.groupValues[1]] ?: return@mapNotNull null
            Candidate(match.range.first, match.range.last + 1, TokenKind.TIME, minute = minute)
        } +
        tonight.findAll(folded).map { match ->
            Candidate(
                start = match.range.first,
                end = match.range.last + 1,
                kind = TokenKind.TIME,
                date = today,
                minute = 19 * 60,
            )
        }

/**
 * **Hours 1 to 7 read as afternoon**, so "ve 3" is 15:00: that is how Olda says it, and the
 * spec asks for it. 0 and 8 to 23 stand as written. A morning marker turns the rule off, which
 * is what makes "ve 3 rano" three in the morning and "at 12 am" midnight.
 */
private fun minuteOfDay(hour: Int, minutes: Int, marker: String?): Int? {
    if (hour > 23 || minutes > 59) return null
    val adjusted = when {
        marker in morningMarkers -> if (hour == 12) 0 else hour
        marker in afternoonMarkers -> if (hour < 12) hour + 12 else hour
        hour in 1..7 -> hour + 12
        else -> hour
    }
    return adjusted * 60 + minutes
}

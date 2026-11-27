package cz.olda.tasks.data.parser

import java.time.LocalDateTime

private enum class Span { DAYS, WEEKS, MONTHS, YEARS, HOURS, MINUTES }

/**
 * The unit words, folded, in both languages. Czech inflects the unit after a number — one den,
 * two dny, five dní — so every form a person actually types is listed rather than stemmed.
 */
private val units: Map<String, Span> = mapOf(
    "den" to Span.DAYS, "dny" to Span.DAYS, "dni" to Span.DAYS, "dnu" to Span.DAYS,
    "day" to Span.DAYS, "days" to Span.DAYS,
    "tyden" to Span.WEEKS, "tydny" to Span.WEEKS, "tydnu" to Span.WEEKS, "tydne" to Span.WEEKS,
    "week" to Span.WEEKS, "weeks" to Span.WEEKS,
    "mesic" to Span.MONTHS, "mesice" to Span.MONTHS, "mesicu" to Span.MONTHS,
    "month" to Span.MONTHS, "months" to Span.MONTHS,
    "rok" to Span.YEARS, "roky" to Span.YEARS, "roku" to Span.YEARS, "let" to Span.YEARS,
    "year" to Span.YEARS, "years" to Span.YEARS,
    "hodina" to Span.HOURS, "hodinu" to Span.HOURS, "hodiny" to Span.HOURS, "hodin" to Span.HOURS,
    "hour" to Span.HOURS, "hours" to Span.HOURS,
    "minuta" to Span.MINUTES, "minutu" to Span.MINUTES, "minuty" to Span.MINUTES,
    "minut" to Span.MINUTES, "minute" to Span.MINUTES, "minutes" to Span.MINUTES,
    "min" to Span.MINUTES, "mins" to Span.MINUTES,
)

/** Longest first, so the regex cannot stop at "minut" inside "minuty". */
private val anyUnit = units.keys.sortedByDescending { it.length }.joinToString("|")

/**
 * "za 3 dny", "za den", "in 3 days", "in a day". A missing count means one, which is how both
 * languages say it: Czech leaves the number out, English says "a" or "an".
 */
private val relative = wordPattern("(?:za|in)\\s+(?:(\\d{1,3})|an|a)?\\s*($anyUnit)")

internal fun relativeCandidates(folded: String, now: LocalDateTime): List<Candidate> =
    relative.findAll(folded).mapNotNull { match ->
        val count = match.groupValues[1].toIntOrNull() ?: 1
        val span = units[match.groupValues[2]] ?: return@mapNotNull null
        val start = match.range.first
        val end = match.range.last + 1
        when (span) {
            // Hours and minutes are counted from now to the minute, so they roll past midnight
            // by themselves and set the day as well as the time.
            Span.HOURS, Span.MINUTES -> {
                val then = now.plusMinutes((if (span == Span.HOURS) count * 60 else count).toLong())
                Candidate(
                    start = start,
                    end = end,
                    kind = TokenKind.DATE,
                    date = then.toLocalDate(),
                    minute = then.hour * 60 + then.minute,
                )
            }
            else -> {
                val today = now.toLocalDate()
                val date = when (span) {
                    Span.DAYS -> today.plusDays(count.toLong())
                    Span.WEEKS -> today.plusWeeks(count.toLong())
                    Span.MONTHS -> today.plusMonths(count.toLong())
                    else -> today.plusYears(count.toLong())
                }
                Candidate(start = start, end = end, kind = TokenKind.DATE, date = date)
            }
        }
    }.toList()

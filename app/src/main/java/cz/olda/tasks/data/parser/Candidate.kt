package cz.olda.tasks.data.parser

import java.time.LocalDate
import java.time.Period

/** The one value each kind of phrase fills in. Tags are the only one that may repeat. */
internal enum class Field { DATE, TIME, LIST, TAG, PRIORITY, RECURRENCE }

/**
 * One phrase a pattern family recognised, before resolution decides whether it wins.
 *
 * Every family resolves its own values while scanning, so a candidate carries finished values
 * rather than something to interpret later: [date] is a day, [minute] is minutes since
 * midnight, [recurrence] is an RRULE fragment.
 *
 * [impliedDate] and [rollOn] belong to a repeat. A repeat does **not** claim the date field —
 * it only offers a first occurrence for when nothing else set one, which is why "dnes kazdy
 * den ve 3" keeps the day Olda typed. [rollOn] is one period of that repeat, used to move an
 * implied first occurrence on when its time has already gone.
 */
internal data class Candidate(
    val start: Int,
    val end: Int,
    val kind: TokenKind,
    val date: LocalDate? = null,
    val minute: Int? = null,
    val listName: String? = null,
    val tagName: String? = null,
    val priority: Int? = null,
    val recurrence: String? = null,
    val impliedDate: LocalDate? = null,
    val rollOn: Period? = null,
)

internal val Candidate.length: Int get() = end - start

/** The fields this candidate claims. [impliedDate] is deliberately not one of them. */
internal val Candidate.fields: Set<Field>
    get() = buildSet {
        if (date != null) add(Field.DATE)
        if (minute != null) add(Field.TIME)
        if (listName != null) add(Field.LIST)
        if (tagName != null) add(Field.TAG)
        if (priority != null) add(Field.PRIORITY)
        if (recurrence != null) add(Field.RECURRENCE)
    }

/**
 * Wraps [body] so it can only match a whole word: not preceded and not followed by a letter or
 * a digit. This is what keeps `p1` out of `typ1a`, `poledne` out of `odpoledne`, and the `@` of
 * an email address from starting a tag.
 */
internal fun wordPattern(body: String): Regex =
    Regex("(?<![\\p{L}\\p{N}])(?:$body)(?![\\p{L}\\p{N}])")

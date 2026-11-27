package cz.olda.tasks.data.parser

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Turns one typed line into a task: `zitra ve 3 zavolat na urad #prace` becomes tomorrow at
 * 15:00 in Práce, titled `zavolat na urad`.
 *
 * Pure, and the only thing it knows about the outside world is what it is handed. [now] is
 * passed in rather than read from a clock, so a test can say "it is Wednesday at 16:30" and
 * get the same answer every time. [knownLists] is the existing lists' names as they are
 * stored: three of the `#` rules cannot be decided without them — whether a prefix is unique,
 * whether it is ambiguous, and whether an unknown name stays in the title — and deciding them
 * here keeps the title cut in one place and testable without a device.
 *
 * It returns names, never ids. Turning a name into a list id needs the database, which is the
 * quick-add sheet's job.
 */
fun parseQuickAdd(input: String, now: LocalDateTime, knownLists: List<String>): ParsedInput {
    val folded = fold(input)
    val accepted = resolve(candidatesIn(folded, now, knownLists))
    val minute = accepted.firstNotNullOfOrNull { it.minute }
    return ParsedInput(
        title = cutTitle(input, accepted),
        dueDate = dueDate(accepted, minute, now),
        dueMinute = minute,
        listName = accepted.firstNotNullOfOrNull { it.listName },
        tagNames = accepted.mapNotNull { it.tagName },
        priority = accepted.firstNotNullOfOrNull { it.priority },
        recurrence = accepted.firstNotNullOfOrNull { it.recurrence },
        tokens = accepted.map {
            ParsedToken(it.start, it.end, it.kind, input.substring(it.start, it.end))
        },
    )
}

/**
 * The day the task lands on, in the order the spec settles it.
 *
 * 1. **A day Olda typed always wins**, overdue or not: "dnes ve 3" at half past four is today,
 *    because he said today.
 * 2. **A day a repeat implied moves on** by one period of that repeat when its time has already
 *    gone, so "kazdy den ve 3" typed at 16:30 starts tomorrow and "kazde pondeli v 9" typed on
 *    Monday morning starts next Monday. With no time there is nothing to have missed.
 * 3. **A time with no day at all lands on the next one** — "ve 3" typed at 16:30 means tomorrow,
 *    Olda's call on 27 Nov. An hour that is exactly now counts as gone.
 */
private fun dueDate(accepted: List<Candidate>, minute: Int?, now: LocalDateTime): LocalDate? {
    accepted.firstNotNullOfOrNull { it.date }?.let { return it }

    val repeat = accepted.firstOrNull { it.impliedDate != null }
    if (repeat != null) {
        val first = repeat.impliedDate!!
        val rollOn = repeat.rollOn
        return if (minute != null && rollOn != null && !hasYetToCome(first, minute, now)) {
            first.plus(rollOn)
        } else {
            first
        }
    }

    if (minute == null) return null
    val today = now.toLocalDate()
    return if (hasYetToCome(today, minute, now)) today else today.plusDays(1)
}

private fun hasYetToCome(date: LocalDate, minute: Int, now: LocalDateTime): Boolean =
    date.atStartOfDay().plusMinutes(minute.toLong()).isAfter(now)

/** Everything every pattern family offers for this line. One family per phrase family. */
private fun candidatesIn(
    folded: String,
    now: LocalDateTime,
    knownLists: List<String>,
): List<Candidate> {
    val today = now.toLocalDate()
    return dayCandidates(folded, today) +
        weekdayCandidates(folded, today) +
        calendarDateCandidates(folded, today) +
        timeCandidates(folded, today) +
        relativeCandidates(folded, now) +
        recurrenceCandidates(folded, today) +
        symbolCandidates(folded, knownLists)
}

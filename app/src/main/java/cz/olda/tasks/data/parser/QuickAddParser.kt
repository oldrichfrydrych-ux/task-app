package cz.olda.tasks.data.parser

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
    return ParsedInput(
        title = cutTitle(input, accepted),
        dueDate = accepted.firstNotNullOfOrNull { it.date },
        dueMinute = accepted.firstNotNullOfOrNull { it.minute },
        listName = accepted.firstNotNullOfOrNull { it.listName },
        tagNames = accepted.mapNotNull { it.tagName },
        priority = accepted.firstNotNullOfOrNull { it.priority },
        recurrence = accepted.firstNotNullOfOrNull { it.recurrence },
        tokens = accepted.map {
            ParsedToken(it.start, it.end, it.kind, input.substring(it.start, it.end))
        },
    )
}

/** Everything every pattern family offers for this line. One family per phrase family. */
private fun candidatesIn(
    folded: String,
    now: LocalDateTime,
    knownLists: List<String>,
): List<Candidate> {
    val today = now.toLocalDate()
    return dayCandidates(folded, today) + weekdayCandidates(folded, today)
}

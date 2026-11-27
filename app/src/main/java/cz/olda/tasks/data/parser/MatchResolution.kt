package cz.olda.tasks.data.parser

private val whitespaceRun = Regex("\\s+")
private val spaceBeforePunctuation = Regex(" +([,.!?])")

/**
 * Picks the winners out of everything the pattern families offered: **longest first, then
 * leftmost**. A candidate is accepted when it overlaps nothing already accepted and every
 * field it claims is still free.
 *
 * Longest-first is what makes "ve 3. 12." one date rather than the time "ve 3" plus an orphan
 * "12.". Leftmost-at-equal-length means the first thing Olda typed wins, so the words of the
 * loser stay visible in the title instead of a coin toss deciding his date.
 *
 * The result comes back in reading order, so tokens and tag names follow the line.
 */
internal fun resolve(candidates: List<Candidate>): List<Candidate> {
    val accepted = mutableListOf<Candidate>()
    val taken = mutableSetOf<Field>()
    candidates
        .sortedWith(compareByDescending<Candidate> { it.length }.thenBy { it.start })
        .forEach { candidate ->
            // Tags accumulate; every other kind holds one value.
            val claimed = candidate.fields - Field.TAG
            if (claimed.any { it in taken }) return@forEach
            if (accepted.any { candidate.start < it.end && it.start < candidate.end }) return@forEach
            accepted += candidate
            taken += claimed
        }
    return accepted.sortedBy { it.start }
}

/**
 * The title: [input] with the [accepted] ranges removed, runs of whitespace collapsed to one
 * space, a space left in front of `,` `.` `!` `?` removed, and the result trimmed. Olda's
 * words are only ever removed, never rewritten.
 */
internal fun cutTitle(input: String, accepted: List<Candidate>): String {
    val kept = StringBuilder(input.length)
    var index = 0
    accepted.sortedBy { it.start }.forEach { candidate ->
        if (candidate.start > index) kept.append(input, index, candidate.start)
        index = maxOf(index, candidate.end)
    }
    if (index < input.length) kept.append(input, index, input.length)
    return kept.toString()
        .replace(whitespaceRun, " ")
        .replace(spaceBeforePunctuation, "$1")
        .trim()
}

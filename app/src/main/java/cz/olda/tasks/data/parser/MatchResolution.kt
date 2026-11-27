package cz.olda.tasks.data.parser

private val whitespaceRun = Regex("\\s+")
private val spaceBeforePunctuation = Regex(" +([,.!?])")

/**
 * Picks the winners out of everything the pattern families offered: **leftmost first, and the
 * longest of the ones that start together**. A candidate is accepted when it overlaps nothing
 * already accepted and every field it claims is still free.
 *
 * Leftmost-first is "the first thing Olda typed wins": in "dnes zitra zavolat" the date is
 * today and the word "zitra" stays visible in the title, rather than the longer word quietly
 * winning. Longest-at-the-same-start is what makes "ve 3. 12." one date rather than the time
 * "ve 3" plus an orphaned "12." — the two candidates for an absorbed preposition always begin
 * at the same character.
 *
 * The result comes back in reading order, so tokens and tag names follow the line.
 */
internal fun resolve(candidates: List<Candidate>): List<Candidate> {
    val accepted = mutableListOf<Candidate>()
    val taken = mutableSetOf<Field>()
    candidates
        .sortedWith(compareBy<Candidate> { it.start }.thenByDescending { it.length })
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

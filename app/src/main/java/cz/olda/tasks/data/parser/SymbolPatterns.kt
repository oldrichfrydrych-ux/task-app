package cz.olda.tasks.data.parser

/** The characters a `#` or `@` name is made of: no spaces, so a name is one word. */
private const val NAME = "([\\p{L}\\p{N}_-]+)"

private val listReference = wordPattern("#$NAME")
private val tagReference = wordPattern("@$NAME")
private val priorityReference = wordPattern("p([1-4])")

/**
 * A list's name as `#` matches it: folded, then stripped of everything that is not a letter, a
 * digit, a space, `_` or `-`.
 *
 * Olda's lists are stored as `🌊Práce` and `☀️Osobní` — Phase 1 left the `icon` column unused
 * and the emoji sits inside the name. Folded, `🌊Práce` is `🌊práce`, which does not begin with
 * `prace`, so without this `#prace` would match nothing at all.
 */
internal fun listMatchName(name: String): String =
    fold(name)
        .filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }
        .trim()

/**
 * `#list`, `@tag` and `p1` to `p4`.
 *
 * Olda's call on 27 Nov: **a list has to exist already, a tag is made on the spot.** A `#` that
 * matches no list produces no candidate, so the word stays in the title and the task goes to
 * Inbox — a typo must not leave a junk list behind. A tag always matches, because he has none
 * yet and tags are throwaway by nature; creating it happens at save time, not here.
 */
internal fun symbolCandidates(folded: String, knownLists: List<String>): List<Candidate> {
    val matchNames = knownLists.associateWith { listMatchName(it) }
    val listCandidates = listReference.findAll(folded).mapNotNull { match ->
        val typed = match.groupValues[1]
        val stored = matchNames.entries.singleOrNull { it.value == typed }?.key
            ?: matchNames.entries.singleOrNull { it.value.startsWith(typed) }?.key
            ?: return@mapNotNull null
        Candidate(match.range.first, match.range.last + 1, TokenKind.LIST, listName = stored)
    }
    val tagCandidates = tagReference.findAll(folded).map { match ->
        Candidate(
            start = match.range.first,
            end = match.range.last + 1,
            kind = TokenKind.TAG,
            tagName = match.groupValues[1],
        )
    }
    val priorityCandidates = priorityReference.findAll(folded).mapNotNull { match ->
        val priority = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        Candidate(match.range.first, match.range.last + 1, TokenKind.PRIORITY, priority = priority)
    }
    return (listCandidates + tagCandidates + priorityCandidates).toList()
}

package cz.olda.tasks.data.parser

import java.time.LocalDate

/** What kind of thing a recognised phrase turned out to be. */
enum class TokenKind { DATE, TIME, LIST, TAG, PRIORITY, RECURRENCE }

/**
 * Where a recognised phrase sat in the original input, with [end] exclusive and [text] as
 * Olda typed it. Nothing in Phase 2 reads these; they exist so that highlighting the
 * recognised words inside the field, in a later phase, is a change to the UI and not to the
 * parser.
 */
data class ParsedToken(val start: Int, val end: Int, val kind: TokenKind, val text: String)

/**
 * One typed line, understood. [title] is the line with every recognised range removed.
 *
 * [dueMinute] is minutes since midnight, the way `TaskEntity` stores it. [priority] is null
 * for "not said", so the quick-add sheet keeps its own default of 4. [listName] is the name of
 * an existing list, **verbatim as it is stored**, so the sheet finds its id by string equality
 * and never has to repeat the matching.
 */
data class ParsedInput(
    val title: String,
    val dueDate: LocalDate? = null,
    val dueMinute: Int? = null,
    val listName: String? = null,
    val tagNames: List<String> = emptyList(),
    val priority: Int? = null,
    val recurrence: String? = null,
    val tokens: List<ParsedToken> = emptyList(),
)

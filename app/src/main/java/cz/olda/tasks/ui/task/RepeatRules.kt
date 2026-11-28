package cz.olda.tasks.ui.task

import java.time.LocalDate

/** The units the Custom sheet offers; the order is the order they appear in. */
enum class RepeatUnit(internal val freq: String) {
    DAYS("DAILY"),
    WEEKS("WEEKLY"),
    MONTHS("MONTHLY"),
    YEARS("YEARLY"),
}

/**
 * The four ready answers in the picker, in the order they are shown: every day, every week on the
 * task's own weekday, every month, every year.
 *
 * [anchor] is the task's due date, or today when it has none, so the weekly row names the day the
 * task actually falls on rather than a day Olda has to work out.
 */
fun presetRules(anchor: LocalDate): List<String> = listOf(
    "FREQ=DAILY",
    "FREQ=WEEKLY;BYDAY=${anchor.dayOfWeek.name.take(2)}",
    "FREQ=MONTHLY",
    "FREQ=YEARLY",
)

/**
 * What the Custom sheet writes. An interval of one is left out of the rule, so picking "every 1
 * week" and tapping the weekly preset produce the same string and the picker cannot end up with
 * two spellings of one meaning.
 */
fun customRule(interval: Int, unit: RepeatUnit): String =
    if (interval <= 1) "FREQ=${unit.freq}" else "FREQ=${unit.freq};INTERVAL=$interval"

/**
 * The interval and unit the Custom sheet should open on, or null when [rule] is a preset, is not a
 * rule this picker could have written, or cannot be read at all — in which case Custom opens on its
 * own defaults rather than on a guess.
 */
fun customOf(rule: String): Pair<Int, RepeatUnit>? {
    val parts = rule.split(";").mapNotNull { part ->
        part.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }.toMap()
    if (parts.size != 2) return null
    val unit = RepeatUnit.entries.firstOrNull { it.freq == parts["FREQ"] } ?: return null
    val interval = parts["INTERVAL"]?.toIntOrNull()?.takeIf { it > 1 } ?: return null
    return interval to unit
}

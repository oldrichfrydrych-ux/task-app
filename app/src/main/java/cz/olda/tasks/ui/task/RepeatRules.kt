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
 * Whether [rule] is the one [preset] stands for, which is not always string equality.
 *
 * The weekly preset carries the task's own weekday (`FREQ=WEEKLY;BYDAY=TU`), but two other weekly
 * rules reach this screen and must still show as weekly: a plain `FREQ=WEEKLY`, which Phase 2's
 * parser writes for `kazdy tyden`, and a `BYDAY` rule whose day is not the task's due day — his
 * real "Číst při Týdenním resetu (pondělí)" is `BYDAY=MO` on a task due a Saturday. Without this
 * the sheet would show nothing ticked at all, telling him a repeating task has no repeat.
 */
fun matchesPreset(rule: String, preset: String): Boolean =
    rule == preset || (rule.isWeekly() && preset.isWeekly())

private fun String.isWeekly(): Boolean = startsWith("FREQ=WEEKLY") && !contains("INTERVAL=")

/**
 * The weekly row's own wording follows the rule when the rule names a weekday, and the task's due
 * day only when it does not — so the row a tap would keep says what it would keep.
 */
fun weeklyPresetFor(rule: String?, anchor: LocalDate): String =
    rule?.takeIf { it.isWeekly() && it.contains("BYDAY=") } ?: presetRules(anchor)[1]

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

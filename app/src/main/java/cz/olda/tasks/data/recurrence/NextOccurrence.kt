package cz.olda.tasks.data.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How far a month- or year-based rule is allowed to walk before it gives up; see [nextOccurrence]. */
private const val MAX_STEPS = 1_000

private val byDayCodes = DayOfWeek.entries.associateBy { it.name.take(2) }

/**
 * The next date in [rule]'s series after [today], anchored on [dueDate].
 *
 * Olda's call on 28 Nov: **keep the schedule and skip what was missed.** The series starts at the
 * task's own due date and steps forward in whole periods until it lands past today, so a daily
 * task due Monday and ticked on Wednesday comes back on Thursday rather than on the Tuesday it
 * missed, a weekly Monday task stays on Mondays, and "every three days" keeps its rhythm instead
 * of restarting from whenever the tick happened. A task ticked early still steps once off its own
 * due date. Nothing this returns is ever in the past.
 *
 * [dueDate] of null means the task has a repeat and no date — possible, because task detail can
 * clear a date — and then today is the anchor, so a daily rule gives tomorrow.
 *
 * Returns **null** for a rule it cannot read, which is how a rule from a later phase, a typo or an
 * interval that is not a positive number ends up completing the task and creating nothing, rather
 * than putting a new task on a guessed day. A duplicated key (`FREQ=DAILY;FREQ=WEEKLY`) is a
 * refusal for the same reason: there is no honest way to choose.
 *
 * Day- and week-based rules are worked out arithmetically, so a daily repeat anchored years back
 * resolves at once. Month- and year-based ones have to step, because a month is not a fixed number
 * of days, and they give up after [MAX_STEPS] — roughly 83 years of monthly — so that a rule
 * anchored absurdly far back cannot spin while Olda waits for a task to tick.
 *
 * `BYDAY` is anchored on its own weekday rather than the due date's: the candidates are that
 * weekday's dates after the due date. A task due on a Wednesday carrying "every Monday" is
 * reachable through the repeat picker, and it has to move to a Monday. `BYDAY` with an `INTERVAL`
 * is handled too, though nothing in the app writes one yet (`ui/task/recurrenceLabel` reads that
 * pair as the catch-all "Repeats"), because stepping it is the same arithmetic.
 *
 * Month and year steps inherit `java.time`'s clamping: 31 January repeating monthly lands on 28 or
 * 29 February, and a 29 February task repeating yearly lands on 28 February. That is the documented
 * behaviour, not a bug to correct here — `BYMONTHDAY` is the proper fix and is out of this phase's
 * vocabulary.
 */
fun nextOccurrence(rule: String, dueDate: LocalDate?, today: LocalDate): LocalDate? {
    val parts = parse(rule) ?: return null
    val interval = parts["INTERVAL"]?.let { it.toIntOrNull()?.takeIf { n -> n > 0 } ?: return null } ?: 1
    val anchor = dueDate ?: today

    return when (parts["FREQ"]) {
        "DAILY" -> afterToday(anchor, interval, today)
        "WEEKLY" -> when (val code = parts["BYDAY"]) {
            null -> afterToday(anchor, 7 * interval, today)
            else -> {
                val day = byDayCodes[code] ?: return null
                // Strictly after the due date, so the first candidate is already one step on.
                val first = anchor.plusDays(1 + ((day.value - anchor.plusDays(1).dayOfWeek.value + 7) % 7).toLong())
                onOrAfterToday(first, 7 * interval, today)
            }
        }
        "MONTHLY" -> stepped(anchor, today) { k -> anchor.plusMonths(k * interval.toLong()) }
        "YEARLY" -> stepped(anchor, today) { k -> anchor.plusYears(k * interval.toLong()) }
        else -> null
    }
}

/** `FREQ=DAILY;FREQ=WEEKLY` and other duplicated keys are refusals, so this returns null for them. */
private fun parse(rule: String): Map<String, String>? {
    val pairs = rule.split(";").map { part ->
        val halves = part.split("=", limit = 2)
        if (halves.size != 2 || halves[0].isBlank() || halves[1].isBlank()) return null
        halves[0] to halves[1]
    }
    if (pairs.isEmpty() || pairs.distinctBy { it.first }.size != pairs.size) return null
    return pairs.toMap()
}

/** The first `anchor + k * days` with k at least 1 that is after [today]. */
private fun afterToday(anchor: LocalDate, days: Int, today: LocalDate): LocalDate =
    anchor.plusDays(stepsNeeded(anchor, days, today, minimum = 1) * days.toLong())

/** The same, but the candidate series starts at [anchor] itself (k may be 0). */
private fun onOrAfterToday(anchor: LocalDate, days: Int, today: LocalDate): LocalDate =
    anchor.plusDays(stepsNeeded(anchor, days, today, minimum = 0) * days.toLong())

private fun stepsNeeded(anchor: LocalDate, days: Int, today: LocalDate, minimum: Int): Long {
    val elapsed = ChronoUnit.DAYS.between(anchor, today)
    return maxOf(minimum.toLong(), Math.floorDiv(elapsed, days.toLong()) + 1)
}

private inline fun stepped(anchor: LocalDate, today: LocalDate, at: (Long) -> LocalDate): LocalDate? {
    for (k in 1..MAX_STEPS) {
        val candidate = at(k.toLong())
        if (candidate.isAfter(today)) return candidate
    }
    return null
}

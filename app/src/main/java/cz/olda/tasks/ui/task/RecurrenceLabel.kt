package cz.olda.tasks.ui.task

import androidx.annotation.StringRes
import cz.olda.tasks.R
import java.time.DayOfWeek

/**
 * How task detail says a repeat: a string resource plus at most one value to put in it.
 *
 * It returns ids and plain values rather than reading resources, so it is testable on the PC.
 * The composable formats [day] with `getDisplayName(TextStyle.FULL, Locale.getDefault())`, the
 * way `WeekStrip.kt` already names a weekday, so this adds no weekday strings of its own.
 */
data class RecurrenceLabel(
    @StringRes val stringId: Int,
    val interval: Int? = null,
    val day: DayOfWeek? = null,
)

private val byDayCodes = DayOfWeek.entries.associateBy { it.name.take(2) }

/**
 * Reads an RRULE fragment such as `FREQ=WEEKLY;BYDAY=MO`. Quick-add writes these in Olda's own
 * words, but by the time a task is open those words are gone, and there is no `values-cs`, so
 * this says it in English. Anything it cannot read becomes a plain "Repeats" rather than a
 * crash: a rule from a later phase must never break this screen.
 */
fun recurrenceLabel(rule: String): RecurrenceLabel {
    val parts = rule.split(";").mapNotNull { part ->
        val (key, value) = part.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        key to value
    }.toMap()

    val interval = parts["INTERVAL"]?.let { it.toIntOrNull() ?: return unknown }
    val day = parts["BYDAY"]?.let { byDayCodes[it] ?: return unknown }

    return when (parts["FREQ"]) {
        "DAILY" -> when {
            day != null -> unknown
            interval != null -> RecurrenceLabel(R.string.repeat_every_n_days, interval = interval)
            else -> RecurrenceLabel(R.string.repeat_daily)
        }
        "WEEKLY" -> when {
            day != null && interval == null -> RecurrenceLabel(R.string.repeat_weekly_on, day = day)
            day == null && interval != null -> RecurrenceLabel(R.string.repeat_every_n_weeks, interval = interval)
            day == null -> RecurrenceLabel(R.string.repeat_weekly)
            else -> unknown
        }
        // An interval in months or years arrived with Phase 3's picker, whose Custom sheet offers
        // both units; without these, "every 3 months" would read as the catch-all "Repeats".
        "MONTHLY" -> when {
            day != null -> unknown
            interval != null -> RecurrenceLabel(R.string.repeat_every_n_months, interval = interval)
            else -> RecurrenceLabel(R.string.repeat_monthly)
        }
        "YEARLY" -> when {
            day != null -> unknown
            interval != null -> RecurrenceLabel(R.string.repeat_every_n_years, interval = interval)
            else -> RecurrenceLabel(R.string.repeat_yearly)
        }
        else -> unknown
    }
}

private val unknown = RecurrenceLabel(R.string.repeat_unknown)

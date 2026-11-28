package cz.olda.tasks.data.reminder

import java.time.LocalDate
import java.time.ZoneId

/** 08:00 — Olda's choice on 28 Nov, and the hour his parser already reads `ráno` as. */
const val MORNING_MINUTE = 8 * 60

/**
 * What the planner needs to know about one task: a projection of four columns, not the entity.
 *
 * The names are what the DAO query aliases to (`SELECT id AS taskId, ...`), so Room can map a row
 * straight onto this.
 */
data class ReminderInput(
    val taskId: Long,
    /** Local epoch day, as `TaskEntity` stores it. */
    val dueDate: Long?,
    /** Minutes since local midnight, or null for a task dated with no time. */
    val dueMinute: Int?,
    /** Epoch millis, or null when the task is not snoozed. */
    val snoozedUntil: Long?,
)

/**
 * One moment, and every task that should ring at it.
 *
 * A list and not a single id because several tasks share a moment routinely: every task dated
 * with no time rings at 08:00, so a day with six of them is one ring with six tasks. With one task
 * per ring, the alarm would fire, the first task would be posted, and the re-arm — which looks for
 * a ring strictly after now — would skip the other five for good.
 */
data class Ring(val atMillis: Long, val taskIds: List<Long>)

/**
 * The instant [task] should ring at, in epoch millis, or null if it should not ring at all.
 *
 * A task with a time rings at that minute; a task dated with no time rings at [morningMinute]; a
 * snooze that is later than either of those wins, because that is what a snooze is for. A task
 * with no date never rings, however it is snoozed — there is no day to hang a reminder on.
 *
 * `atZone` is what resolves a local time that does not exist or exists twice, on the two days a
 * year the clocks move, so a task at 02:30 on a spring-forward morning gets a real instant rather
 * than an exception.
 */
fun ringAt(task: ReminderInput, zone: ZoneId, morningMinute: Int = MORNING_MINUTE): Long? {
    val day = task.dueDate ?: return null
    val minute = task.dueMinute ?: morningMinute
    val due = LocalDate.ofEpochDay(day)
        .atTime(minute / 60, minute % 60)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
    return maxOf(due, task.snoozedUntil ?: due)
}

/**
 * The earliest ring **strictly after** [nowMillis], carrying every task that shares that instant,
 * ids ascending; null when nothing is coming.
 *
 * Strictly after, and so a ring whose minute has passed is skipped rather than fired late. That is
 * deliberate and is the one real cost in this design: a reminder due while the phone was switched
 * off does not arrive when it comes back, and the task is simply overdue on Today, which is where
 * the app shows it anyway. The reason is that this is recomputed on **every** write to the tasks
 * table — completing something, editing a title — and a past ring that could still fire would fire
 * again on each of those, so the app would have to remember "this one has been shown", which means
 * another column and a new way to be wrong: a notification dismissed and then coming back.
 * Skipping the past keeps every recomputation idempotent, so booking the same alarm twice is the
 * same as booking it once, and nothing has to be remembered.
 */
fun nextRing(
    tasks: List<ReminderInput>,
    nowMillis: Long,
    zone: ZoneId,
    morningMinute: Int = MORNING_MINUTE,
): Ring? {
    val rings = tasks.mapNotNull { task ->
        ringAt(task, zone, morningMinute)?.takeIf { it > nowMillis }?.let { it to task.taskId }
    }
    val earliest = rings.minOfOrNull { it.first } ?: return null
    return Ring(earliest, rings.filter { it.first == earliest }.map { it.second }.sorted())
}

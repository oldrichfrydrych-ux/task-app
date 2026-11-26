package cz.olda.tasks.ui

import cz.olda.tasks.ui.quickadd.SavedTask
import java.time.LocalDate

/**
 * Which tab will be showing a task that was just saved.
 *
 * The rules mirror what the screens actually query, which is the whole point: the pill that
 * flies to a tab has to land on one that really holds the task, or it tells a lie.
 *
 * - A date today or earlier shows on **Today** — an overdue task is listed there, under
 *   Overdue, and never appears on Upcoming.
 * - A later date shows on **Upcoming**.
 * - Undated, it is wherever its list is: **Inbox** has its own tab, anything else is reached
 *   through **Lists**.
 *
 * A date wins over the list, because Today and Upcoming list a task whatever list it is in.
 */
fun arrivalTab(saved: SavedTask, today: LocalDate): Destination = when {
    saved.dueDate == null -> if (saved.toInbox) Destination.Inbox else Destination.Lists
    saved.dueDate.isAfter(today) -> Destination.Upcoming
    else -> Destination.Today
}

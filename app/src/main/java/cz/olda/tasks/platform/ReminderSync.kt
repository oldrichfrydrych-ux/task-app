package cz.olda.tasks.platform

import cz.olda.tasks.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the booked alarm in step with the tasks table, without anything having to say so.
 *
 * [start] collects `observeNextReminder()` for the life of the process: Room re-runs the query
 * behind it on every write to `tasks`, so adding a task, moving its date, snoozing it, completing
 * it or deleting it all arrive here and re-book. Nothing in the app calls the scheduler directly,
 * which means no call site can be the one that forgot.
 *
 * [rearmNow] is the same thing once, for the receivers: an alarm can fire, or the phone can
 * restart, with no app process alive to be collecting anything.
 */
class ReminderSync(
    private val scope: CoroutineScope,
    private val repository: TaskRepository,
    private val booking: AlarmBooking,
) {
    private var collecting = false

    /** Idempotent: a second call does not collect the flow twice. */
    fun start() {
        if (collecting) return
        collecting = true
        scope.launch {
            repository.observeNextReminder().collect { ring ->
                if (ring == null) booking.cancel() else booking.book(ring)
            }
        }
    }

    suspend fun rearmNow() {
        val ring = repository.observeNextReminder().first()
        if (ring == null) booking.cancel() else booking.book(ring)
    }
}

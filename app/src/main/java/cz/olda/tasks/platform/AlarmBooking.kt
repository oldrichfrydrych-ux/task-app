package cz.olda.tasks.platform

import cz.olda.tasks.data.reminder.Ring

/**
 * Where a [Ring] is booked, as a seam.
 *
 * A test records what would have been booked instead of waiting for a real alarm to fire, which is
 * the difference between a four-second test and one that waits on the clock.
 */
interface AlarmBooking {
    fun book(ring: Ring)

    fun cancel()
}

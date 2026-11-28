package cz.olda.tasks.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import cz.olda.tasks.data.reminder.Ring

/**
 * The only file in the app that touches [AlarmManager].
 *
 * **One alarm at a time.** The app books the earliest ring and nothing else; when it fires, the
 * receiver posts it and books the one after. An alarm for every pending reminder would mean
 * hundreds of registered alarms to keep in step with every edit, where this leaves one thing to
 * get right — and because every booking uses the same request code, re-booking replaces what was
 * there rather than adding to it, so doing it twice is the same as doing it once.
 *
 * `setExactAndAllowWhileIdle` is what fires at the minute even in Doze, and it needs no permission
 * check here: `USE_EXACT_ALARM` is in the manifest and granted at install, which is the route Olda
 * chose on 28 Nov precisely so there is no refusal to handle.
 */
class ReminderScheduler(private val context: Context) : AlarmBooking {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun book(ring: Ring) {
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ring.atMillis, pendingIntent(ring))
    }

    /** Cancels by matching the request code; the extras play no part in matching. */
    override fun cancel() {
        alarms.cancel(pendingIntent(Ring(0, emptyList())))
    }

    private fun pendingIntent(ring: Ring): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_AT_MILLIS, ring.atMillis)
            .putExtra(ReminderReceiver.EXTRA_TASK_IDS, ring.taskIds.toLongArray()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        /** One code for the whole app, so a new booking replaces the old one. */
        const val REQUEST_CODE = 1
    }
}

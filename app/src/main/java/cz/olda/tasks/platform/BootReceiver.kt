package cz.olda.tasks.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cz.olda.tasks.TasksApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Puts the booked alarm back after the four things that leave it wrong.
 *
 * A booked alarm is an absolute instant held by the system, and four ordinary events break that
 * with nothing to recompute it:
 *
 * - **a restart** cancels every pending alarm;
 * - **a time-zone change** leaves 15:00 booked as the instant it was in the old zone, so it fires
 *   at the wrong local hour;
 * - **a clock change**, by hand or from the network, including the two days a year the clocks move;
 * - **a reinstall**, which is Olda's own workflow: every `gradlew installDebug` replaces the
 *   package and cancels its alarms, several times an evening while this app is being built. Without
 *   this, reminders would quietly stop after each new build and look like a bug in the code.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!rearmsOn(intent.action)) return
        val sync = (context.applicationContext as TasksApplication).container.reminderSync
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                sync.rearmNow()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /**
         * The manifest already filters these, so this is the second lock on the same door: a
         * receiver can be started with anything, and re-arming on an unrelated broadcast would read
         * the database for no reason.
         */
        internal fun rearmsOn(action: String?): Boolean = action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}

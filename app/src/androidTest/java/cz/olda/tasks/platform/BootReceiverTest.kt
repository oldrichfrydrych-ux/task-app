package cz.olda.tasks.platform

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The four things that leave a booked alarm wrong, and the receiver that puts it right.
 *
 * A booked alarm is an absolute instant, so a restart, a clock change, a flight or — the one that
 * matters most while this phase is being built — a `gradlew installDebug` all leave it either
 * cancelled or pointing at the wrong local hour. What the re-arm itself does is tested in
 * `ReminderSyncTest`; what has to be true here is that the four intents actually reach this
 * receiver, which is a property of the manifest and the easiest thing in the phase to get silently
 * wrong.
 */
@RunWith(AndroidJUnit4::class)
class BootReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun theManifestListensForAllFourIntents() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        ).forEach { action ->
            val receivers = context.packageManager.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName),
                0,
            )
            assertTrue(
                "nothing in this app listens for $action",
                receivers.any { it.activityInfo.name == BootReceiver::class.java.name },
            )
        }
    }

    @Test
    fun thoseFourAreTheOnesTheReceiverActsOn() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        ).forEach { assertTrue(it, BootReceiver.rearmsOn(it)) }
    }

    @Test
    fun anUnrelatedBroadcastRearmsNothing() {
        listOf(Intent.ACTION_BATTERY_LOW, Intent.ACTION_SCREEN_ON, "", "something.else")
            .forEach { assertFalse(it, BootReceiver.rearmsOn(it)) }
    }
}

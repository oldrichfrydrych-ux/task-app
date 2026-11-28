package cz.olda.tasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Clock
import java.time.ZoneId
import java.util.TimeZone

/**
 * The clock the whole app shares has to notice when the phone changes time zone.
 *
 * Phase 3 books an alarm at an absolute instant worked out from the local time, and re-arms on
 * `TIMEZONE_CHANGED` for exactly that reason — but a clock that captured its zone when the process
 * started would recompute the same wrong instant and the re-arm would do nothing.
 */
class LiveZoneClockTest {

    @Test
    fun theZoneIsReadEachTimeItIsAsked() {
        val original = TimeZone.getDefault()
        try {
            val clock = liveZoneClock()

            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Prague"))
            assertEquals(ZoneId.of("Europe/Prague"), clock.zone)

            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals("a flight must move the clock", ZoneId.of("America/New_York"), clock.zone)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    /** The trap this exists to avoid, written down so nobody quietly goes back to it. */
    @Test
    fun theStandardSystemClockCapturesItsZoneAndKeepsIt() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Prague"))
            val captured = Clock.systemDefaultZone()

            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            assertNotEquals(ZoneId.of("America/New_York"), captured.zone)
            assertEquals(ZoneId.of("Europe/Prague"), captured.zone)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun itStillTellsTheTime() {
        val clock = liveZoneClock()
        val before = System.currentTimeMillis()

        val now = clock.millis()

        assert(now >= before) { "the clock must read the real time" }
    }
}

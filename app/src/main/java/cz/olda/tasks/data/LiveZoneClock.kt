package cz.olda.tasks.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The clock the whole app shares: the real time, and **the phone's time zone as it is now**.
 *
 * `Clock.systemDefaultZone()` looks like the obvious choice and is a trap here. It captures the
 * zone when it is created, so a clock made at app start keeps that zone for the life of the
 * process. Phase 3 turns a task's local time into an absolute instant and books an alarm at it,
 * and re-arms on `TIMEZONE_CHANGED` precisely so that a flight moves the reminder — with a
 * captured zone that re-arm would recompute the same wrong instant and change nothing.
 *
 * Reading [ZoneId.systemDefault] per call costs nothing worth measuring: it is a field read behind
 * a volatile, and the app asks for it a handful of times a minute at most.
 */
fun liveZoneClock(): Clock = LiveZoneClock

private object LiveZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId?): Clock = if (zone == null) this else system(zone)

    override fun instant(): Instant = Instant.ofEpochMilli(millis())

    override fun millis(): Long = System.currentTimeMillis()

    override fun toString(): String = "LiveZoneClock"
}

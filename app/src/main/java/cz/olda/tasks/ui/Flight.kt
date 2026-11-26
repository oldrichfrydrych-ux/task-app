package cz.olda.tasks.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.olda.tasks.ui.quickadd.SavedTask
import java.time.LocalDate

/**
 * A task on its way to the tab that holds it.
 *
 * [id] is what makes two identical saves two flights: without it this is just a title and a
 * destination, so saving the same thing twice would change no state and the pill would never
 * start over. The pill's composition is keyed on it.
 */
data class Flight(val id: Long, val label: String, val destination: Destination)

/**
 * Holds the one task currently in flight, and exists so it can be driven from a test.
 *
 * Catching a real flight through the real sheet is not possible on a device: the save is an
 * ordinary asynchronous write running in real time, while the pill is an animation running
 * on the test clock, and holding one still stalls the other - the pill has always landed
 * before an assertion can see it. With this, the shell's own rules (one at a time, cancelled
 * by the add button, landing on the right tab) are tested directly and deterministically.
 */
@Stable
class FlightController {
    var current: Flight? by mutableStateOf(null)
        private set

    private var nextId = 1L

    /** Replaces whatever was in flight: there is never more than one. */
    fun start(label: String, destination: Destination) {
        current = Flight(nextId++, label, destination)
    }

    fun cancel() {
        current = null
    }
}

/**
 * Sends the pill for a task that was just saved to the tab that will be showing it.
 *
 * This is the whole of what the shell does when the sheet reports a save, pulled out here so
 * it can be tested without a device: a regression in the title, or in the date rule, would
 * otherwise show up only as a pill that lies, and only if Olda happened to notice.
 */
fun FlightController.startFor(saved: SavedTask, today: LocalDate) =
    start(saved.title, arrivalTab(saved, today))

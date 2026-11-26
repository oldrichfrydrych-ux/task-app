package cz.olda.tasks.ui

import cz.olda.tasks.ui.quickadd.SavedTask
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one line that turns a saved task into a flying pill. It used to live in the shell,
 * where only a device test could reach it and none did; here it is ordinary code.
 */
class FlightControllerTest {

    private val today = LocalDate.of(2026, 11, 25)

    @Test
    fun itCarriesTheTitleToTheTabThatHoldsTheTask() {
        val flights = FlightController()
        flights.startFor(SavedTask("Zavolat na urad", today, toInbox = true), today)
        assertEquals("Zavolat na urad", flights.current?.label)
        assertEquals(Destination.Today, flights.current?.destination)
    }

    @Test
    fun anUndatedInboxTaskFliesToInbox() {
        val flights = FlightController()
        flights.startFor(SavedTask("Koupit mleko", null, toInbox = true), today)
        assertEquals(Destination.Inbox, flights.current?.destination)
    }

    @Test
    fun cancelEmptiesIt() {
        val flights = FlightController()
        flights.startFor(SavedTask("Koupit mleko", null, toInbox = true), today)
        flights.cancel()
        assertNull(flights.current)
    }

    /**
     * Two identical saves are still two flights. Without this the second one changes no
     * state at all, so the pill never restarts - and with it, the pill's composition can be
     * keyed on the id, which is what makes the restart actually happen.
     */
    @Test
    fun eachFlightIsItsOwnFlightEvenWhenItLooksTheSame() {
        val flights = FlightController()
        val task = SavedTask("Koupit mleko", null, toInbox = true)
        flights.startFor(task, today)
        val first = flights.current
        flights.startFor(task, today)
        assertNotEquals("the second save reused the first flight", first, flights.current)
        assertNotEquals(first?.id, flights.current?.id)
    }
}

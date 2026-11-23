package cz.olda.tasks.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationTest {

    @Test
    fun aKnownNameComesBack() {
        assertEquals(Destination.Inbox, Destination.ofName("Inbox"))
    }

    /**
     * The selected tab is saved by name. A name saved by an older build may no longer
     * exist, and the app must open on Today rather than crash.
     */
    @Test
    fun anUnknownNameFallsBackToToday() {
        assertEquals(Destination.Today, Destination.ofName("Someday"))
        assertEquals(Destination.Today, Destination.ofName(""))
    }
}

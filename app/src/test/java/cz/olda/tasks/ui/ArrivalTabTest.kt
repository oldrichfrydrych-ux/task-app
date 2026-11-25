package cz.olda.tasks.ui

import cz.olda.tasks.ui.quickadd.SavedTask
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which tab a saved task lands on. The rules have to match what the screens actually query,
 * or the pill flies to a tab that does not show the task it is carrying.
 */
class ArrivalTabTest {

    private val today = LocalDate.of(2026, 11, 25)

    private fun saved(date: LocalDate?, toInbox: Boolean = true) =
        SavedTask("Zavolat na urad", date, toInbox)

    @Test
    fun aTaskForTodayGoesToToday() {
        assertEquals(Destination.Today, arrivalTab(saved(today), today))
    }

    /** Overdue tasks are listed on Today, under Overdue - never on Upcoming. */
    @Test
    fun anOverdueTaskGoesToToday() {
        assertEquals(Destination.Today, arrivalTab(saved(today.minusDays(3)), today))
    }

    @Test
    fun aLaterTaskGoesToUpcoming() {
        assertEquals(Destination.Upcoming, arrivalTab(saved(today.plusDays(1)), today))
    }

    @Test
    fun anUndatedTaskInInboxGoesToInbox() {
        assertEquals(Destination.Inbox, arrivalTab(saved(null), today))
    }

    @Test
    fun anUndatedTaskInSomeOtherListGoesToLists() {
        assertEquals(Destination.Lists, arrivalTab(saved(null, toInbox = false), today))
    }

    /** A dated task shows on Today or Upcoming whichever list it belongs to. */
    @Test
    fun aDateBeatsTheList() {
        assertEquals(Destination.Today, arrivalTab(saved(today, toInbox = false), today))
    }
}

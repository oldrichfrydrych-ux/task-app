package cz.olda.tasks.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gestures the bottom bar's icons make when they are tapped.
 *
 * The drawing of them cannot be tested - a `graphicsLayer` transform leaves nothing in the
 * semantics tree, and this project has deleted two tests that could not fail for exactly that
 * reason. What can be tested is the shape of each gesture, which is where the design lives: that
 * the moving part is the part that means something, and that everything comes back to rest.
 */
class TabIconMotionTest {

    @Test
    fun everyIconIsStillBeforeItIsTapped() {
        Destination.entries.forEach { assertAtRest(it, tabIconTransform(it, 0f)) }
        assertPageAtRest(calendarPageTurn(0f))
        assertTrayAtRest(trayDrop(0f))
        (0..2).forEach { assertEquals("row $it starts out of place", 0f, listRowSlide(0f, it), 0.01f) }
    }

    /**
     * A spring settles on 1, so whatever the gesture did in between has to be undone by then -
     * otherwise a tapped tab would stay tilted, shifted or half-faded for good.
     */
    @Test
    fun everyIconIsStillAgainWhenTheSpringHasSettled() {
        Destination.entries.forEach { assertAtRest(it, tabIconTransform(it, 1f)) }
        assertPageAtRest(calendarPageTurn(1f))
        assertTrayAtRest(trayDrop(1f))
        (0..2).forEach { assertEquals("row $it never came home", 0f, listRowSlide(1f, it), 0.01f) }
    }

    /** The clock is the one icon whose whole face moves, because that is what a clock does. */
    @Test
    fun theClockSweepsAWholeTurnAndNothingElseTurns() {
        assertEquals(180f, tabIconTransform(Destination.Upcoming, 0.5f).rotationZ, 0.01f)
        assertEquals(360f, tabIconTransform(Destination.Upcoming, 1f).rotationZ, 0.01f)
        listOf(Destination.Today, Destination.Inbox, Destination.Lists).forEach {
            assertEquals("$it turns, which says nothing about what it draws", 0f, tabIconTransform(it, 0.5f).rotationZ, 0.01f)
        }
    }

    /** Mid-turn the page that is leaving and the page that is arriving are in different places. */
    @Test
    fun theCalendarTurnsItsPageOutAndANewOneIn() {
        val mid = calendarPageTurn(0.5f)
        assertTrue("the old page has not moved off: $mid", mid.leavingX < -1f)
        assertTrue("the new page is not on its way in: $mid", mid.arrivingX > 1f)
        assertTrue("both pages are solid at once: $mid", mid.leavingAlpha < 1f || mid.arrivingAlpha < 1f)
    }

    @Test
    fun somethingFallsIntoTheTrayAndTheTrayGivesWay() {
        val early = trayDrop(0.3f)
        val landing = trayDrop(0.75f)
        assertTrue("nothing is falling: $early", early.itemAlpha > 0f)
        assertTrue("it is not falling downwards: $early -> $landing", landing.itemY > early.itemY)
        assertTrue("the tray does not give under it", trayDrop(0.6f).dipDp > 0f)
    }

    /** One after another: the rows cannot all arrive at the same moment, or it is one block moving. */
    @Test
    fun theListsRowsArriveOneAfterAnother() {
        val early = (0..2).map { listRowSlide(0.25f, it) }
        assertTrue("the rows move as one block: $early", early.toSet().size == 3)
        assertTrue("the top row is not the first home: $early", early[0] < early[2])
    }

    @Test
    fun everyIconActuallyDoesSomethingInTheMiddleOfItsGesture() {
        assertNotEquals(IconTransform(), tabIconTransform(Destination.Upcoming, 0.5f))
        assertNotEquals(calendarPageTurn(0f), calendarPageTurn(0.5f))
        assertNotEquals(trayDrop(0f), trayDrop(0.5f))
        assertNotEquals(listRowSlide(0f, 1), listRowSlide(0.5f, 1))
    }

    /**
     * A spring overshoots past its target before it settles, so every curve is asked for a value
     * beyond 1 as well: nothing may fly off or turn inside out on the way back.
     */
    @Test
    fun nothingGoesWildWhenTheSpringOvershoots() {
        Destination.entries.forEach {
            val over = tabIconTransform(it, 1.2f)
            assertTrue("$it jumps too far past the end: ${over.translationYDp}", kotlin.math.abs(over.translationYDp) <= 8f)
            assertTrue("$it scales out of range: $over", over.scaleX in 0.7f..1.3f && over.scaleY in 0.7f..1.3f)
        }
        val page = calendarPageTurn(1.2f)
        assertTrue("a page flies off past the end: $page", kotlin.math.abs(page.leavingX) <= 12f && kotlin.math.abs(page.arrivingX) <= 12f)
        assertTrue("the tray is still sunk past the end", kotlin.math.abs(trayDrop(1.2f).dipDp) <= 4f)
        (0..2).forEach { assertTrue("row $it is adrift past the end", kotlin.math.abs(listRowSlide(1.2f, it)) <= 12f) }
    }

    /** Upright, in its place, its own size - and square with where it started, for the one that turns. */
    private fun assertAtRest(destination: Destination, t: IconTransform) {
        assertEquals("$destination is out of place", 0f, t.translationYDp, 0.01f)
        assertEquals("$destination is stretched", 1f, t.scaleX, 0.01f)
        assertEquals("$destination is squashed", 1f, t.scaleY, 0.01f)
        val turns = t.rotationZ / 360f
        assertEquals("$destination stopped part-way through a turn", Math.round(turns).toFloat(), turns, 0.01f)
    }

    /**
     * At rest the calendar shows one page, squarely in its frame - whichever of the two it is.
     * Before the turn that is the page that will leave; after it, the one that arrived.
     */
    private fun assertPageAtRest(page: PageTurn) {
        val showing = listOf(page.leavingX to page.leavingAlpha, page.arrivingX to page.arrivingAlpha)
            .filter { it.second > 0.01f }
        assertEquals("exactly one page should be showing: $page", 1, showing.size)
        assertEquals("the page showing is out of its frame: $page", 0f, showing.single().first, 0.01f)
        assertEquals("the page showing is faded: $page", 1f, showing.single().second, 0.01f)
    }

    private fun assertTrayAtRest(tray: TrayDrop) {
        assertEquals("the tray is sunk", 0f, tray.dipDp, 0.01f)
        assertEquals("something is hanging over the tray at rest", 0f, tray.itemAlpha, 0.01f)
    }
}

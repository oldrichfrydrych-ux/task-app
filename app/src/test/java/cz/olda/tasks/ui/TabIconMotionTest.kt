package cz.olda.tasks.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four gestures the bottom bar's icons make when they are tapped.
 *
 * The drawing of them cannot be tested - a `graphicsLayer` transform leaves nothing in the
 * semantics tree, and this project has deleted two tests that could not fail for exactly that
 * reason. What can be tested is the shape of each gesture, which is where the design lives: that
 * each icon does something of its own, and that all of them come back to rest.
 */
class TabIconMotionTest {

    private val rest = IconTransform()

    @Test
    fun everyIconIsStillBeforeItIsTapped() {
        Destination.entries.forEach { assertAtRest(it, tabIconTransform(it, 0f)) }
    }

    /**
     * A spring settles on 1, so whatever the gesture did in between has to be undone by then -
     * otherwise a tapped tab would stay tilted or shifted for good.
     */
    @Test
    fun everyIconIsStillAgainWhenTheSpringHasSettled() {
        Destination.entries.forEach { assertAtRest(it, tabIconTransform(it, 1f)) }
    }

    /**
     * Upright, in its place, its own size - and, for the one icon that turns, square with where
     * it started. Compared with a tolerance rather than for equality: a curve that is flat at
     * rest still comes back as -0.0 from a multiplication, which is a different float from 0.0.
     */
    private fun assertAtRest(destination: Destination, t: IconTransform) {
        assertEquals("$destination is not upright", 0f, t.rotationX, 0.01f)
        assertEquals("$destination is out of place", 0f, t.translationYDp, 0.01f)
        assertEquals("$destination is stretched", 1f, t.scaleX, 0.01f)
        assertEquals("$destination is squashed", 1f, t.scaleY, 0.01f)
        val turns = t.rotationZ / 360f
        assertEquals("$destination stopped part-way through a turn", Math.round(turns).toFloat(), turns, 0.01f)
    }

    @Test
    fun everyIconActuallyMovesInTheMiddleOfItsGesture() {
        Destination.entries.forEach {
            assertNotEquals("$it does nothing when tapped", rest, tabIconTransform(it, 0.5f))
        }
    }

    /** "Specific to the icon" is the whole point: no two tabs may answer a tap the same way. */
    @Test
    fun noTwoIconsMakeTheSameGesture() {
        val halfway = Destination.entries.map { tabIconTransform(it, 0.5f) }
        assertEquals("two tabs move alike", halfway.size, halfway.toSet().size)
    }

    @Test
    fun theClockSweepsAWholeTurn() {
        assertEquals(180f, tabIconTransform(Destination.Upcoming, 0.5f).rotationZ, 0.01f)
        assertEquals(360f, tabIconTransform(Destination.Upcoming, 1f).rotationZ, 0.01f)
    }

    /**
     * A spring overshoots past its target before it settles, so every curve is asked for a value
     * beyond 1 as well: at 20% past the end nothing may be wilder than it was at its peak.
     */
    @Test
    fun nothingGoesWildWhenTheSpringOvershoots() {
        Destination.entries.forEach {
            val over = tabIconTransform(it, 1.2f)
            assertTrue("$it tilts too far past the end: ${over.rotationX}", kotlin.math.abs(over.rotationX) <= 40f)
            assertTrue("$it jumps too far past the end: ${over.translationYDp}", kotlin.math.abs(over.translationYDp) <= 8f)
            assertTrue("$it scales out of range: ${over.scaleX}, ${over.scaleY}", over.scaleX in 0.7f..1.3f && over.scaleY in 0.7f..1.3f)
        }
    }
}

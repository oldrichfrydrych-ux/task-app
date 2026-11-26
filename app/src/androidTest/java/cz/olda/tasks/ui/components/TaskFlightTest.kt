package cz.olda.tasks.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cz.olda.tasks.ui.theme.AppTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The pill that carries a saved task to the tab holding it. Everything here measures its
 * real position: it moves by layout offset precisely so a test can see where it is.
 */
class TaskFlightTest {

    @get:Rule
    val rule = createComposeRule()

    @After
    fun letTheClockRunAgain() {
        rule.mainClock.autoAdvance = true
    }

    /** Roughly where the add button and a tab sit in the floating bar, in pixels. */
    private val from = Rect(960f, 2480f, 1110f, 2630f)
    private val to = Rect(300f, 2480f, 450f, 2630f)

    private var finishes = 0

    private fun show(
        label: String = "Zavolat na urad",
        animate: Boolean = true,
    ) {
        rule.setContent {
            AppTheme(animationsEnabled = animate) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TaskFlight(
                        label = label,
                        from = from,
                        to = to,
                        onFinished = { finishes++ },
                    )
                }
            }
        }
    }

    /**
     * The real geometry: the add button sits near the right edge of the screen and the tabs
     * near the left, so both ends of the flight are close to an edge. Measured from the
     * actual screen rather than hardcoded, so this says the same thing on any phone.
     */
    private fun showAcrossTheWholeScreen(label: String) {
        rule.setContent {
            AppTheme(animationsEnabled = true) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val right = constraints.maxWidth.toFloat()
                    val bottom = constraints.maxHeight.toFloat()
                    TaskFlight(
                        label = label,
                        from = Rect(right - 170f, bottom - 170f, right - 20f, bottom - 20f),
                        to = Rect(20f, bottom - 170f, 170f, bottom - 20f),
                        onFinished = { finishes++ },
                    )
                }
            }
        }
    }

    private fun pillBounds(): Rect = rule.onNodeWithTag("task-flight").boundsInRootPx(rule.density)

    private fun pillCentre(): Pair<Float, Float> {
        val b = pillBounds()
        return (b.left + b.right) / 2f to (b.top + b.bottom) / 2f
    }

    @Test
    fun itStartsAtTheSourceAndEndsAtTheTarget() {
        rule.mainClock.autoAdvance = false
        show()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val (startX, _) = pillCentre()
        assertTrue(
            "the pill did not start at the add button: x=$startX, expected near ${from.center.x}",
            kotlin.math.abs(startX - from.center.x) < 60f,
        )

        rule.mainClock.advanceTimeBy(3000)
        val (endX, endY) = pillCentre()
        assertTrue(
            "the pill did not reach the tab: ($endX, $endY), expected near ${to.center}",
            kotlin.math.abs(endX - to.center.x) < 60f && kotlin.math.abs(endY - to.center.y) < 60f,
        )
    }

    /**
     * The add button and every tab sit in the same 56dp bar. Travelling straight between
     * them would be a flicker half hidden behind the tabs it passes, so the pill has to
     * leave the bar on the way.
     */
    @Test
    fun itRisesWellAboveTheBarOnTheWay() {
        rule.mainClock.autoAdvance = false
        show()
        rule.mainClock.advanceTimeByFrame()
        var highest = Float.MAX_VALUE
        repeat(30) {
            if (rule.onAllNodesWithTag("task-flight").fetchSemanticsNodes().isNotEmpty()) {
                highest = minOf(highest, pillCentre().second)
            }
            rule.mainClock.advanceTimeBy(16)
        }
        val barTop = minOf(from.top, to.top)
        val rise = barTop - highest
        val expected = with(rule.density) { 100.dp.toPx() }
        assertTrue(
            "the pill only rose ${"%.0f".format(rise)}px above the bar; it has to clear it",
            rise > expected,
        )
    }

    /** Review Focus 5: Czech titles run long and this is a pill, not a paragraph. */
    @Test
    fun aLongCzechTitleStaysOnePill() {
        rule.mainClock.autoAdvance = false
        show(label = "zkontrolovat a preobjednat dokumentaci k internimu auditu za listopad")
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val bounds = pillBounds()
        val widest = with(rule.density) { 260.dp.toPx() }
        val tallest = with(rule.density) { 64.dp.toPx() }
        assertTrue("the pill grew to ${bounds.width}px wide", bounds.width <= widest)
        assertTrue("the pill grew to ${bounds.height}px tall", bounds.height <= tallest)
    }

    /**
     * A long title on a pill launched from the add button would hang off the right edge of
     * the screen, sliced in half at the very moment it is supposed to be seen leaving the
     * button - and sliced at the other end when it lands on the leftmost tab.
     */
    @Test
    fun itStaysOnTheScreenAtBothEnds() {
        rule.mainClock.autoAdvance = false
        showAcrossTheWholeScreen("zkontrolovat dokumentaci k internimu auditu za listopad")
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val screen = rule.onRoot().boundsInRootPx(rule.density)

        val start = pillBounds()
        assertTrue("the pill started ${-start.left}px off the left edge", start.left >= -1f)
        assertTrue(
            "the pill started ${start.right - screen.right}px off the right edge",
            start.right <= screen.right + 1f,
        )

        rule.mainClock.advanceTimeBy(3000)
        val end = pillBounds()
        assertTrue("the pill landed ${-end.left}px off the left edge", end.left >= -1f)
        assertTrue(
            "the pill landed ${end.right - screen.right}px off the right edge",
            end.right <= screen.right + 1f,
        )
    }

    /** Review Focus 1. */
    @Test
    fun nothingIsDrawnWithAnimationsOff() {
        show(animate = false)
        rule.waitForIdle()
        rule.onNodeWithTag("task-flight").assertDoesNotExist()
    }

    @Test
    fun itReportsFinishedOnceAndOnlyOnce() {
        finishes = 0
        rule.mainClock.autoAdvance = false
        show()
        rule.mainClock.advanceTimeBy(3000)
        assertEquals(1, finishes)
        rule.mainClock.advanceTimeBy(3000)
        assertEquals("finished more than once", 1, finishes)
    }
}

/**
 * Where the node really is, in pixels, whether or not it is on screen.
 *
 * The framework's own unclipped bounds, converted - not `boundsInRoot`, which is clipped by
 * every ancestor and so would quietly hide a pill hanging off the edge of the screen,
 * passing by luck on a wide phone and failing on a narrow one.
 */
private fun SemanticsNodeInteraction.boundsInRootPx(density: Density): Rect {
    val b = getUnclippedBoundsInRoot()
    return with(density) { Rect(b.left.toPx(), b.top.toPx(), b.right.toPx(), b.bottom.toPx()) }
}

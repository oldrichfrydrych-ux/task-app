package cz.olda.tasks.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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

    private fun pillCentre(): Pair<Float, Float> {
        val b = rule.onNodeWithTag("task-flight").getUnclippedBoundsInRootPx()
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
        val bounds = rule.onNodeWithTag("task-flight").getUnclippedBoundsInRootPx()
        val widest = with(rule.density) { 260.dp.toPx() }
        val tallest = with(rule.density) { 64.dp.toPx() }
        assertTrue("the pill grew to ${bounds.width}px wide", bounds.width <= widest)
        assertTrue("the pill grew to ${bounds.height}px tall", bounds.height <= tallest)
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

private fun SemanticsNodeInteraction.getUnclippedBoundsInRootPx(): Rect =
    fetchSemanticsNode().boundsInRoot

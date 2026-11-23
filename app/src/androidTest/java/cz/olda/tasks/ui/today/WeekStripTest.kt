package cz.olda.tasks.ui.today

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import cz.olda.tasks.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class WeekStripTest {
    @get:Rule
    val rule = createComposeRule()

    private val monday = LocalDate.of(2026, 11, 23)

    private fun show(dots: Map<LocalDate, List<String>>, onPick: (LocalDate) -> Unit = {}) {
        rule.setContent {
            AppTheme { WeekStrip(monday, monday, dots, onPick) }
        }
    }

    @Test fun showsOneDotPerListColourOnItsOwnDayOnly() {
        show(
            mapOf(
                monday to listOf("#E85A4F"),
                monday.plusDays(2) to listOf("#E85A4F", "#2A9D8F"),
            ),
        )
        rule.onAllNodesWithTag("dot-$monday", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("dot-${monday.plusDays(2)}", useUnmergedTree = true).assertCountEquals(2)
        rule.onAllNodesWithTag("dot-${monday.plusDays(1)}", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun capsTheDotsAtThree() {
        show(mapOf(monday to listOf("#111111", "#222222", "#333333", "#444444", "#555555")))
        rule.onAllNodesWithTag("dot-$monday", useUnmergedTree = true).assertCountEquals(3)
    }

    @Test fun anUnparsableColourStillShowsADot() {
        show(mapOf(monday to listOf("not a colour")))
        rule.onAllNodesWithTag("dot-$monday", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun tappingADayReportsThatDate() {
        var picked: LocalDate? = null
        show(emptyMap()) { picked = it }
        rule.onNodeWithTag("day-2026-11-26").performClick()
        assertEquals(LocalDate.of(2026, 11, 26), picked)
    }
}

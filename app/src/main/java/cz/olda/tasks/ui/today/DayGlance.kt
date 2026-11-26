package cz.olda.tasks.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.ui.theme.parseHex

/** The bar is a shape, not a row of numbers, so it stays thin. */
private val BarHeight = 8.dp

/**
 * Today at a glance: one bar split by list colour across the day's [open] tasks, and under it how
 * many of the day's tasks are already done.
 *
 * The mockups show a second number here - how much of the day is still free. That one needs a
 * length for every task, and nothing in the app writes one yet, so it is left out rather than
 * invented (`docs/superpowers/plans/2026-11-26-look-redesign.md`).
 *
 * Nothing is drawn on a day with no tasks at all: there would be nothing to glance at, and the
 * empty state already says so.
 */
@Composable
fun DayGlance(
    open: List<TaskWithList>,
    doneCount: Int,
    modifier: Modifier = Modifier,
) {
    if (open.isEmpty() && doneCount == 0) return

    // One segment per list, in the order the lists first appear, so the bar does not reshuffle
    // itself every time the database re-emits the same day.
    val shares = remember(open) {
        open.groupBy { it.listName }.map { (name, rows) -> Triple(name, rows.first().listColorHex, rows.size) }
    }
    val fallback = MaterialTheme.colorScheme.onSurfaceVariant
    val byList = shares.joinToString(", ") { (name, _, count) -> "$name $count" }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (shares.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BarHeight)
                    .semantics { contentDescription = byList }
                    .testTag("glance-bar"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                shares.forEach { (_, hex, count) ->
                    Box(
                        modifier = Modifier
                            .weight(count.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(BarHeight / 2))
                            .background(parseHex(hex) ?: fallback),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.glance_done, doneCount, doneCount + open.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("glance-done"),
        )
    }
}

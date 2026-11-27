package cz.olda.tasks.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.ui.theme.AppMotion
import cz.olda.tasks.ui.theme.LocalAnimationsEnabled
import kotlinx.coroutines.delay

/**
 * How long a completed row stays visible, filled and struck through, before it collapses.
 * Short enough not to delay anything, long enough to read as "done" rather than as "gone".
 */
private const val HoldMillis = 120L

/** A row held past its data so it can fold, and where in the list it was when it left. */
private class Folding(val item: TaskWithList, val index: Int)

/** What this section has seen, outside snapshot state so writing it costs no recomposition. */
private class RowMemory {
    var last: List<TaskWithList> = emptyList()

    /**
     * The ids that were in the first non-empty list. Every screen composes its sections with
     * an empty list before Room answers, so "has this section settled yet" cannot be a flag
     * set after the first composition: it would mark the real first load as new and unfurl
     * the whole list. Belonging to that first load is a property of the row, not of a moment.
     */
    val firstLoad = mutableSetOf<Long>()
    var loaded = false
}

/**
 * A header (tagged with [headerModifier]) and its rows, each showing its date when [showDate]
 * and its list name unless [showList] is off - which is for screens where every row is in the
 * same list, such as the Inbox.
 * Renders nothing at all when there is nothing to show.
 *
 * A completed row does not simply vanish. Completing writes to the repository, which re-emits
 * this list without that row, so the row has to outlive its own data to animate at all: the
 * section keeps drawing it, as done, until it has folded away. Only completions are held -
 * rows leave for many other reasons, and folding a search result on every keystroke would be
 * absurd - which is what [LocalCompletionTracker] is for. The header is held too, so it cannot
 * disappear out from over a row that is still folding.
 */
@Composable
fun TaskListSection(
    title: String?,
    items: List<TaskWithList>,
    onToggleDone: (Long) -> Unit,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    showDate: Boolean = false,
    showList: Boolean = true,
) {
    val animate = LocalAnimationsEnabled.current
    val tracker = LocalCompletionTracker.current
    val folding = remember { mutableStateMapOf<Long, Folding>() }
    val memory = remember { RowMemory() }

    remember(items) {
        val present = items.mapTo(mutableSetOf()) { it.task.id }
        val completed = tracker.justCompleted
        if (animate && completed != null && completed !in present) {
            val wasAt = memory.last.indexOfFirst { it.task.id == completed }
            if (wasAt >= 0) folding[completed] = Folding(memory.last[wasAt], wasAt)
        }
        // A row that came back - undo - is no longer folding.
        folding.keys.filter { it in present }.forEach { folding.remove(it) }
        if (!memory.loaded && items.isNotEmpty()) {
            memory.loaded = true
            memory.firstLoad.addAll(present)
        }
        memory.last = items
        Unit
    }

    if (items.isEmpty() && folding.isEmpty()) return

    // One ordered list, with each held row spliced back where it was. Drawing the held rows
    // after the live ones would send the row Olda just ticked to the bottom of its section
    // before it folded, which only looks right when it was the last row anyway.
    val rows = buildList {
        items.forEach { add(Folding(it, -1)) }
        folding.values.sortedBy { it.index }.forEach { held ->
            add(held.index.coerceIn(0, size), held)
        }
    }

    // One date column for the whole section, as wide as its widest date, so the titles start in
    // the same place instead of stepping in and out with the length of each row's date. A row
    // cannot work this out by itself: what decides it is what its neighbours say.
    val measurer = rememberTextMeasurer()
    val timeStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val timeTexts = rows.map { rowTimeText(it.item.task, showDate) }
    val timeColumn = remember(timeTexts, timeStyle, density) {
        val widest = timeTexts.filterNotNull()
            .maxOfOrNull { measurer.measure(it, timeStyle, softWrap = false).size.width }
            ?: 0
        if (widest > 0) with(density) { widest.toDp() } else null
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = headerModifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        rows.forEach { row ->
            val id = row.item.task.id
            val isFolding = row.index >= 0
            key(id) {
                FoldingRow(
                    item = row.item,
                    folding = isFolding,
                    entering = animate && !isFolding && id !in memory.firstLoad,
                    showDate = showDate,
                    showList = showList,
                    timeColumn = timeColumn,
                    onToggleDone = { if (!isFolding) onToggleDone(id) },
                    onClick = { if (!isFolding) onClick(id) },
                    onGone = {
                        folding.remove(id)
                        // Stop remembering the completion once its row has gone, or the same
                        // task restored and later re-filed would fold on a departure that is
                        // not a completion.
                        if (tracker.justCompleted == id) tracker.justCompleted = null
                    },
                )
            }
        }
    }
}

@Composable
private fun FoldingRow(
    item: TaskWithList,
    folding: Boolean,
    entering: Boolean,
    showDate: Boolean,
    showList: Boolean,
    timeColumn: Dp?,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    onGone: () -> Unit,
) {
    val state = remember { MutableTransitionState(!entering) }

    LaunchedEffect(folding) {
        if (folding) {
            // Fill the circle and strike the title first; collapse once that has registered.
            delay(HoldMillis)
            state.targetState = false
        } else {
            state.targetState = true
        }
    }

    if (folding && state.isIdle && !state.currentState) {
        SideEffect { onGone() }
    }

    AnimatedVisibility(
        visibleState = state,
        enter = expandVertically(AppMotion.spatial()) + fadeIn(AppMotion.effects()),
        exit = shrinkVertically(AppMotion.fastEffects()) + fadeOut(AppMotion.fastEffects()),
    ) {
        TaskRow(
            item = item,
            onToggleDone = onToggleDone,
            onClick = onClick,
            showDate = showDate,
            showList = showList,
            timeColumn = timeColumn,
            showAsDone = folding,
        )
    }
}

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/** Remembers which rows were on screen last, outside snapshot state so writing it is free. */
private class RowMemory {
    var last: List<TaskWithList> = emptyList()
}

/**
 * A header (tagged with [headerModifier]) and its rows, each showing its date when [showDate].
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
) {
    val animate = LocalAnimationsEnabled.current
    val tracker = LocalCompletionTracker.current
    val folding = remember { mutableStateMapOf<Long, TaskWithList>() }
    val memory = remember { RowMemory() }

    // Rows that arrive later should animate in; the ones already there on the first pass
    // should not, or every tab switch would animate the whole list in again.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }

    remember(items) {
        val present = items.mapTo(mutableSetOf()) { it.task.id }
        val completed = tracker.justCompleted
        if (animate && completed != null && completed !in present) {
            memory.last.firstOrNull { it.task.id == completed }?.let { folding[completed] = it }
        }
        // A row that came back - undo - is no longer folding.
        folding.keys.filter { it in present }.forEach { folding.remove(it) }
        memory.last = items
        Unit
    }

    if (items.isEmpty() && folding.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = headerModifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        items.forEach { item ->
            key(item.task.id) {
                FoldingRow(
                    item = item,
                    folding = false,
                    entering = animate && settled,
                    showDate = showDate,
                    onToggleDone = { onToggleDone(item.task.id) },
                    onClick = { onClick(item.task.id) },
                    onGone = {},
                )
            }
        }
        folding.values.toList().forEach { item ->
            key(item.task.id) {
                FoldingRow(
                    item = item,
                    folding = true,
                    entering = false,
                    showDate = showDate,
                    onToggleDone = {},
                    onClick = {},
                    onGone = { folding.remove(item.task.id) },
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
        exit = shrinkVertically(AppMotion.spatial()) + fadeOut(AppMotion.effects()),
    ) {
        TaskRow(
            item = item,
            onToggleDone = onToggleDone,
            onClick = onClick,
            showDate = showDate,
            showAsDone = folding,
        )
    }
}

package cz.olda.tasks.ui.quickadd

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
import cz.olda.tasks.ui.task.labelOf
import cz.olda.tasks.ui.components.formatMinuteOfDay
import cz.olda.tasks.ui.theme.AppMotion
import cz.olda.tasks.ui.theme.LocalAnimationsEnabled
import cz.olda.tasks.ui.theme.parseHex
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The string for a priority from 1 (High) to 4 (None). */
@Composable
internal fun priorityLabel(priority: Int): String = stringResource(
    when (priority) {
        1 -> R.string.priority_1
        2 -> R.string.priority_2
        3 -> R.string.priority_3
        else -> R.string.priority_4
    },
)

/**
 * The chips under the quick-add field: the date, the time, the list, the priority, a repeat when
 * one was recognised, and one per tag. Each shows what the parser understood and asks the caller
 * to open its picker; the list chip carries its list's own colour as a dot, while the text stays
 * in the normal text colour so it never depends on that colour's contrast.
 *
 * A chip pops when its value changes, so a value the parser filled in while Olda typed is
 * noticed. With Android's "remove animations" on, the value simply changes.
 */
@Composable
fun QuickAddChips(
    state: QuickAddState,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onPickList: () -> Unit,
    onPickPriority: () -> Unit,
    onPickRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = state.lists.firstOrNull { it.id == state.listId }
    val dateText = state.dueDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        ?: stringResource(R.string.no_date)
    val timeText = state.dueMinute?.let { formatMinuteOfDay(it) } ?: stringResource(R.string.no_time)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PoppingChip(value = dateText, tag = "chip-date", onClick = onPickDate) { Text(dateText) }
            PoppingChip(value = timeText, tag = "chip-time", onClick = onPickTime) { Text(timeText) }
            PoppingChip(value = list?.name.orEmpty(), tag = "chip-list", onClick = onPickList, leadingIcon = {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(list?.let { parseHex(it.colorHex) } ?: Color.Transparent),
                )
            }) { Text(list?.name ?: "") }
            PoppingChip(
                value = state.priority.toString(),
                tag = "chip-priority",
                onClick = onPickPriority,
            ) { Text(stringResource(R.string.chip_priority_value, priorityLabel(state.priority))) }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Always here, because it is how a repeat is set as well as how one is read. Until
            // Phase 3 it appeared only once the parser had found a repeat, and tapping it cleared
            // the repeat and took the chip with it, leaving nothing on screen to bring it back.
            val repeatText = when {
                // His own words, not the stored rule: the rule cannot reconstruct his spelling.
                state.recurrenceText != null -> stringResource(R.string.chip_repeat_value, state.recurrenceText)
                state.recurrence != null -> stringResource(R.string.chip_repeat_value, labelOf(state.recurrence))
                else -> stringResource(R.string.repeat_chip_empty)
            }
            PoppingChip(value = repeatText, tag = "chip-repeat", onClick = onPickRepeat) {
                Text(repeatText)
            }
            state.tagNames.forEach { tag ->
                PoppingChip(value = tag, tag = "chip-tag-$tag", onClick = {}) { Text("@$tag") }
            }
        }
    }
}

/** An assist chip that pops once whenever [value] changes, so a parsed value announces itself. */
@Composable
private fun PoppingChip(
    value: String,
    tag: String,
    onClick: () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
    label: @Composable () -> Unit,
) {
    val animate = LocalAnimationsEnabled.current
    var popped by remember { mutableStateOf(true) }
    LaunchedEffect(value) {
        if (animate) {
            popped = false
            popped = true
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (popped) 1f else 0.88f,
        animationSpec = AppMotion.fastSpatial(),
        label = "chip-pop-$tag",
    )
    AssistChip(
        onClick = onClick,
        label = label,
        leadingIcon = leadingIcon,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .testTag(tag),
    )
}

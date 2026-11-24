package cz.olda.tasks.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
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
 * The date, list and priority chips under the quick-add field. Each shows its current value and
 * asks the caller to open its picker. The list chip carries its list's own colour as a dot; the
 * text stays in the normal text colour, so it never depends on that colour's contrast.
 */
@Composable
fun QuickAddChips(
    state: QuickAddState,
    onPickDate: () -> Unit,
    onPickList: () -> Unit,
    onPickPriority: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = state.lists.firstOrNull { it.id == state.listId }
    val dateText = state.dueDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        ?: stringResource(R.string.no_date)
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onPickDate,
            label = { Text(dateText) },
            modifier = Modifier.testTag("chip-date"),
        )
        AssistChip(
            onClick = onPickList,
            label = { Text(list?.name ?: "") },
            leadingIcon = {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(list?.let { parseHex(it.colorHex) } ?: Color.Transparent),
                )
            },
            modifier = Modifier.testTag("chip-list"),
        )
        AssistChip(
            onClick = onPickPriority,
            label = { Text(stringResource(R.string.chip_priority_value, priorityLabel(state.priority))) },
            modifier = Modifier.testTag("chip-priority"),
        )
    }
}

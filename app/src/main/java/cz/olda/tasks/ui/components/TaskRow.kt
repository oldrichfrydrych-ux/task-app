package cz.olda.tasks.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.ui.theme.parseHex
import java.util.Calendar

/**
 * The compact rail: start time on the left, title in the middle, list name on the right in the
 * list's colour. No fixed heights, so at large font sizes the row grows instead of clipping; the
 * title takes whatever width is left and wraps to two lines.
 *
 * A [TaskWithList.listColorHex] that cannot be parsed falls back to the theme's secondary text
 * colour rather than crashing.
 */
@Composable
fun TaskRow(
    item: TaskWithList,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = item.task
    val listColor = parseHex(item.listColorHex) ?: MaterialTheme.colorScheme.onSurfaceVariant
    val completeLabel = stringResource(R.string.complete_task)
    val timeText = task.dueMinute?.let { formatMinuteOfDay(it) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("task-${task.id}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(start = 4.dp, end = 12.dp),
        ) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { onToggleDone() },
                modifier = Modifier.semantics { contentDescription = completeLabel },
            )
            if (timeText != null) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp).testTag("task-time-${task.id}"),
                )
            }
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            Text(
                text = item.listName,
                style = MaterialTheme.typography.labelMedium,
                color = listColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp).widthIn(max = 120.dp),
            )
        }
    }
}

/** Formats minutes since midnight with the device's locale and 12/24-hour setting. */
@Composable
private fun formatMinuteOfDay(minute: Int): String {
    val context = LocalContext.current
    return remember(minute, context) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
        }
        DateFormat.getTimeFormat(context).format(cal.time)
    }
}

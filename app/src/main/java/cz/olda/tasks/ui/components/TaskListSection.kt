package cz.olda.tasks.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.olda.tasks.data.TaskWithList

/** A header (tagged with [headerModifier]) and its rows, each showing its date when [showDate]. Renders nothing at all when [items] is empty. */
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
    if (items.isEmpty()) return
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
            TaskRow(
                item = item,
                onToggleDone = { onToggleDone(item.task.id) },
                onClick = { onClick(item.task.id) },
                showDate = showDate,
            )
        }
    }
}

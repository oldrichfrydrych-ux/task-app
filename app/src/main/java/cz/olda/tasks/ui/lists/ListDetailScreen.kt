package cz.olda.tasks.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.olda.tasks.R
import cz.olda.tasks.ui.components.EmptyStateText
import cz.olda.tasks.ui.theme.AppMotion
import cz.olda.tasks.ui.theme.LocalAnimationsEnabled
import cz.olda.tasks.ui.components.ConfirmDeleteDialog
import cz.olda.tasks.ui.components.ScreenHeader
import cz.olda.tasks.ui.AppIcons
import cz.olda.tasks.ui.components.TaskListSection
import cz.olda.tasks.ui.components.rememberCompletionUndo

/** One list: its loose tasks first, then each heading with the tasks filed under it. */
@Composable
fun ListDetailScreen(
    viewModel: ListDetailViewModel,
    onOpenTask: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val complete = rememberCompletionUndo(
        onComplete = viewModel::complete,
        onUndo = viewModel::undoCompletion,
    )
    var addingHeading by rememberSaveable { mutableStateOf(false) }
    var deletingHeadingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renamingHeadingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(modifier = modifier.fillMaxSize().testTag("screen-ListDetail")) {
        val list = state.list
        ScreenHeader(
            title = list?.name,
            onBack = onBack,
            backModifier = Modifier.testTag("list-back"),
            titleModifier = Modifier.testTag("list-title"),
            trailing = {
                if (list != null) {
                    TextButton(onClick = { addingHeading = true }, modifier = Modifier.testTag("add-heading")) {
                        Text(stringResource(R.string.add_heading))
                    }
                }
            },
        )
        // Nothing below shows until the list has loaded, so no empty message flashes on start.
        if (state.list != null) {
            // Fades up rather than popping in behind the screen transition. A draw step, not
            // a gate: the content is laid out and tappable from the first frame it exists.
            // The branch itself only exists once the list has loaded, so the value has to be
            // flipped after the first composition; a fixed target would start already there.
            var arrived by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { arrived = true }
            val animated by animateFloatAsState(
                targetValue = if (arrived) 1f else 0f,
                animationSpec = AppMotion.fastEffects(),
                label = "list-detail-arrival",
            )
            val arrival = if (LocalAnimationsEnabled.current) animated else 1f
            Column(
                modifier = Modifier
                    .graphicsLayer { alpha = arrival }
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TaskListSection(null, state.unheaded, complete, onOpenTask, showDate = true, showList = false)
                state.sections.forEach { section ->
                    val heading = section.heading
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().testTag("heading-${heading.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = heading.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .testTag("heading-title-${heading.id}"),
                            )
                            TextButton(
                                onClick = { renamingHeadingId = heading.id },
                                modifier = Modifier.testTag("heading-rename-${heading.id}"),
                            ) { Text(stringResource(R.string.rename)) }
                            TextButton(
                                onClick = { deletingHeadingId = heading.id },
                                modifier = Modifier.testTag("heading-delete-${heading.id}"),
                            ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                        }
                        TaskListSection(null, section.tasks, complete, onOpenTask, showDate = true, showList = false)
                    }
                }
                if (state.unheaded.isEmpty() && state.sections.all { it.tasks.isEmpty() }) {
                    EmptyStateText(
                        text = stringResource(R.string.list_empty),
                        modifier = Modifier.testTag("list-empty"),
                    )
                }
            }
        }
    }

    if (addingHeading) {
        HeadingNameDialog(
            title = stringResource(R.string.add_heading),
            initial = "",
            onSave = { viewModel.addHeading(it); addingHeading = false },
            onDismiss = { addingHeading = false },
        )
    }
    renamingHeadingId?.let { id ->
        val current = state.sections.firstOrNull { it.heading.id == id }?.heading
        if (current == null) {
            renamingHeadingId = null
        } else {
            HeadingNameDialog(
                title = stringResource(R.string.rename_heading),
                initial = current.name,
                onSave = { viewModel.renameHeading(id, it); renamingHeadingId = null },
                onDismiss = { renamingHeadingId = null },
            )
        }
    }
    deletingHeadingId?.let { id ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_heading_title),
            message = stringResource(R.string.delete_heading_keeps_tasks),
            onConfirm = { viewModel.deleteHeading(id); deletingHeadingId = null },
            onDismiss = { deletingHeadingId = null },
        )
    }
}

@Composable
private fun HeadingNameDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.heading_name_hint)) },
                singleLine = true,
                modifier = Modifier.testTag("heading-name-field"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("heading-save"),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

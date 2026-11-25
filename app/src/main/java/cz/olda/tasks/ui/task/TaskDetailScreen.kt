package cz.olda.tasks.ui.task

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.olda.tasks.R
import cz.olda.tasks.ui.theme.AppMotion
import cz.olda.tasks.ui.theme.LocalAnimationsEnabled
import cz.olda.tasks.data.entity.TaskEntity
import cz.olda.tasks.ui.AppIcons
import cz.olda.tasks.ui.components.formatMinuteOfDay
import cz.olda.tasks.ui.components.ConfirmDeleteDialog
import cz.olda.tasks.ui.components.ScreenHeader
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MILLIS_PER_DAY = 86_400_000L

/** One task: every field editable in place, its subtasks, and delete. */
@Composable
fun TaskDetailScreen(
    viewModel: TaskDetailViewModel,
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val task = state.task

    val animated by animateFloatAsState(
        targetValue = if (task != null) 1f else 0f,
        animationSpec = AppMotion.fastEffects(),
        label = "detail-arrival",
    )
    val arrival = if (LocalAnimationsEnabled.current) animated else 1f

    Column(modifier = modifier.fillMaxSize().testTag("screen-TaskDetail")) {
        ScreenHeader(
            title = stringResource(R.string.task_screen_title),
            titleModifier = Modifier.testTag("task-header"),
            onBack = onBack,
            backModifier = Modifier.testTag("task-back"),
        )
        // Nothing below shows until the task has loaded; a task that does not exist says so.
        if (task != null) {
            // Behind a screen transition, fields that simply appear once Room answers read as
            // popping into a screen that has already arrived. They fade instead. This is a
            // draw step, not a gate: they are composed, laid out and editable from the first
            // frame they exist, which is what the test below holds.
            TaskFields(
                task = task,
                state = state,
                viewModel = viewModel,
                onOpenTask = onOpenTask,
                onDelete = { confirmingDelete = true },
                modifier = Modifier.graphicsLayer { alpha = arrival },
            )
        } else if (state.loaded) {
            Text(
                text = stringResource(R.string.task_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).testTag("task-not-found"),
            )
        }
    }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_task_title),
            message = stringResource(R.string.delete_task_with_subtasks),
            onConfirm = {
                confirmingDelete = false
                scope.launch {
                    viewModel.delete()
                    onBack()
                }
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun TaskFields(
    task: TaskEntity,
    state: TaskDetailState,
    viewModel: TaskDetailViewModel,
    onOpenTask: (Long) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local text, seeded once: the field must not be rewritten by its own saves mid-typing.
    var title by rememberSaveable { mutableStateOf(task.title) }
    var notes by rememberSaveable { mutableStateOf(task.notes) }
    val completeLabel = stringResource(R.string.complete_task)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { viewModel.toggleDone() },
                modifier = Modifier.testTag("task-done").semantics { contentDescription = completeLabel },
            )
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    // An empty title is never saved; the stored one stays until a real one is typed.
                    if (it.isNotBlank()) viewModel.setTitle(it.trim())
                },
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                ),
                placeholder = { Text(stringResource(R.string.task_title_hint)) },
                isError = title.isBlank(),
                modifier = Modifier.weight(1f).testTag("task-title-field"),
            )
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it; viewModel.setNotes(it) },
            label = { Text(stringResource(R.string.task_notes)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("task-notes-field"),
        )

        // A subtask sits in its parent's list and heading, so it has no pickers of its own.
        if (task.parentTaskId == null) ListPicker(task, state, viewModel)
        DateField(task.dueDate, viewModel::setDueDate)
        TimeField(task.dueMinute, viewModel::setDueMinute)
        PriorityRow(task.priority, viewModel::setPriority)

        // One level only: a subtask has no subtasks section and nothing to add one with.
        if (task.parentTaskId == null) Subtasks(state, viewModel, onOpenTask)

        // Last on the page, away from the title and the back arrow: the most prominent control is no longer destructive.
        TextButton(onClick = onDelete, modifier = Modifier.testTag("task-delete")) {
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ListPicker(task: TaskEntity, state: TaskDetailState, viewModel: TaskDetailViewModel) {
    var listMenu by rememberSaveable { mutableStateOf(false) }
    var headingMenu by rememberSaveable { mutableStateOf(false) }
    val currentList = state.lists.firstOrNull { it.id == task.listId }
    val currentHeading = state.headings.firstOrNull { it.id == task.headingId }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(
                onClick = { listMenu = true },
                modifier = Modifier.fillMaxWidth().testTag("task-list-picker"),
            ) { Text(currentList?.name ?: "") }
            DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                state.lists.forEach { list ->
                    DropdownMenuItem(
                        text = { Text(list.name) },
                        onClick = { viewModel.setList(list.id); listMenu = false },
                        modifier = Modifier.testTag("task-list-option-${list.id}"),
                    )
                }
            }
        }
        if (state.headings.isNotEmpty()) {
            Box {
                OutlinedButton(
                    onClick = { headingMenu = true },
                    modifier = Modifier.fillMaxWidth().testTag("task-heading-picker"),
                ) { Text(currentHeading?.name ?: stringResource(R.string.no_heading)) }
                DropdownMenu(expanded = headingMenu, onDismissRequest = { headingMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.no_heading)) },
                        onClick = { viewModel.setHeading(null); headingMenu = false },
                        modifier = Modifier.testTag("task-heading-option-none"),
                    )
                    state.headings.forEach { heading ->
                        DropdownMenuItem(
                            text = { Text(heading.name) },
                            onClick = { viewModel.setHeading(heading.id); headingMenu = false },
                            modifier = Modifier.testTag("task-heading-option-${heading.id}"),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(epochDay: Long?, onChange: (Long?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val text = epochDay?.let {
        LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } ?: stringResource(R.string.no_date)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f).testTag("task-date")) {
            Text(text)
        }
        if (epochDay != null) {
            TextButton(onClick = { onChange(null) }, modifier = Modifier.testTag("task-date-clear")) {
                Text(stringResource(R.string.clear))
            }
        }
    }
    if (picking) {
        // The picker counts in UTC midnights, which is exactly an epoch day times a day of millis.
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = epochDay?.let { it * MILLIS_PER_DAY })
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onChange(Math.floorDiv(it, MILLIS_PER_DAY)) }
                        picking = false
                    },
                    modifier = Modifier.testTag("task-date-save"),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(minute: Int?, onChange: (Int?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val text = minute?.let { formatMinuteOfDay(it) } ?: stringResource(R.string.no_time)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f).testTag("task-time")) {
            Text(text)
        }
        if (minute != null) {
            TextButton(onClick = { onChange(null) }, modifier = Modifier.testTag("task-time-clear")) {
                Text(stringResource(R.string.clear))
            }
        }
    }
    if (picking) {
        val pickerState = rememberTimePickerState(
            initialHour = (minute ?: (9 * 60)) / 60,
            initialMinute = (minute ?: (9 * 60)) % 60,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { picking = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = { onChange(pickerState.hour * 60 + pickerState.minute); picking = false },
                    modifier = Modifier.testTag("task-time-save"),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PriorityRow(priority: Int, onChange: (Int) -> Unit) {
    val labels = listOf(
        1 to R.string.priority_1,
        2 to R.string.priority_2,
        3 to R.string.priority_3,
        4 to R.string.priority_4,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.priority),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach { (value, label) ->
                FilterChip(
                    selected = priority == value,
                    onClick = { onChange(value) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.testTag("task-priority-$value"),
                )
            }
        }
    }
}

@Composable
private fun Subtasks(state: TaskDetailState, viewModel: TaskDetailViewModel, onOpenTask: (Long) -> Unit) {
    var newTitle by rememberSaveable { mutableStateOf("") }
    val completeLabel = stringResource(R.string.complete_task)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.subtasks),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.subtasks.forEach { sub ->
            Row(
                modifier = Modifier.fillMaxWidth().testTag("subtask-${sub.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = sub.done,
                    onCheckedChange = { viewModel.toggleSubtaskDone(sub.id) },
                    modifier = Modifier.testTag("subtask-done-${sub.id}").semantics { contentDescription = completeLabel },
                )
                Text(
                    text = sub.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (sub.done) TextDecoration.LineThrough else null,
                    // Tapping the title opens the subtask, the only way to reach an undated one.
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable { onOpenTask(sub.id) }
                        .wrapContentHeight(Alignment.CenterVertically)
                        .testTag("subtask-title-${sub.id}"),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                placeholder = { Text(stringResource(R.string.add_subtask_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("subtask-field"),
            )
            TextButton(
                onClick = { viewModel.addSubtask(newTitle); newTitle = "" },
                enabled = newTitle.isNotBlank(),
                modifier = Modifier.testTag("subtask-add"),
            ) { Text(stringResource(R.string.add)) }
        }
    }
}

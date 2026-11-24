package cz.olda.tasks.ui.quickadd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.olda.tasks.R
import cz.olda.tasks.ui.AppViewModelProvider
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/** Which picker is open. Saved, so a rotation does not close it. */
private enum class Picker { None, Date, List, Priority }

/** One line of text, plus a date, list and priority, becomes a task. Phase 2 teaches it Czech and English dates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    viewModel: QuickAddViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sheetState = rememberModalBottomSheetState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The field keeps its own saved text, so typing never waits on the ViewModel, and the draft
    // in the ViewModel is the same text; closing the sheet throws both away.
    var text by rememberSaveable { mutableStateOf(viewModel.state.value.title) }
    var picker by rememberSaveable { mutableStateOf(Picker.None) }

    fun close() {
        viewModel.clear()
        onDismiss()
    }

    fun save() {
        if (!state.canSave) return
        scope.launch {
            viewModel.save()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = ::close, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        viewModel.setTitle(it)
                    },
                    placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick-add-field")
                        .focusRequester(focusRequester),
                )
                TextButton(
                    onClick = ::save,
                    enabled = state.canSave,
                    modifier = Modifier.testTag("quick-add-save"),
                ) { Text(stringResource(R.string.save)) }
            }
            QuickAddChips(
                state = state,
                onPickDate = { picker = Picker.Date },
                onPickList = { picker = Picker.List },
                onPickPriority = { picker = Picker.Priority },
            )
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    when (picker) {
        Picker.None -> Unit
        Picker.Date -> DatePickerSheetDialog(
            current = state.dueDate,
            onPick = { viewModel.setDate(it); picker = Picker.None },
            onDismiss = { picker = Picker.None },
        )
        Picker.List -> ChoiceDialog(
            title = stringResource(R.string.quick_add_choose_list),
            choices = state.lists.map { Choice(it.id, it.name, "quick-add-list-option-${it.id}") },
            selected = state.listId,
            onPick = { viewModel.setList(it); picker = Picker.None },
            onDismiss = { picker = Picker.None },
        )
        Picker.Priority -> ChoiceDialog(
            title = stringResource(R.string.quick_add_choose_priority),
            choices = (1..4).map { Choice(it.toLong(), priorityLabel(it), "quick-add-priority-option-$it") },
            selected = state.priority.toLong(),
            onPick = { viewModel.setPriority(it.toInt()); picker = Picker.None },
            onDismiss = { picker = Picker.None },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheetDialog(current: LocalDate?, onPick: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    // The picker counts in UTC midnights, which is exactly an epoch day times a day of millis.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = (current ?: LocalDate.now()).toEpochDay() * MILLIS_PER_DAY,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let {
                        onPick(LocalDate.ofEpochDay(Math.floorDiv(it, MILLIS_PER_DAY)))
                    } ?: onDismiss()
                },
                modifier = Modifier.testTag("quick-add-date-save"),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = { onPick(null) }, modifier = Modifier.testTag("quick-add-date-clear")) {
                    Text(stringResource(R.string.clear))
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) { DatePicker(state = pickerState) }
}

private data class Choice(val value: Long, val label: String, val tag: String)

/** A short list of options in a dialog; the chosen one is marked in bold. */
@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<Choice>,
    selected: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { choice ->
                    Text(
                        text = choice.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (choice.value == selected) FontWeight.Bold else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onPick(choice.value) }
                            .padding(vertical = 12.dp)
                            .testTag(choice.tag),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

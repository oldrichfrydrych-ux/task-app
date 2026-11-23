package cz.olda.tasks.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
import cz.olda.tasks.data.entity.GroupEntity
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.ui.theme.parseHex

/**
 * Creates a list ([initial] null) or edits one. The colour is a read-only dot for now and is
 * handed back unchanged: null for a new list, meaning "not chosen", so `createList` picks the
 * next preset. The colour picker is dropped in here later without changing [onSave].
 *
 * [groups] fills the group picker and also works when editing. [onDelete] is null when the list cannot be
 * deleted; the sheet asks for confirmation itself and says where the tasks go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListEditSheet(
    initial: ListEntity?,
    onSave: (name: String, colorHex: String?, groupId: Long?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    groups: List<GroupEntity> = emptyList(),
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var groupId by rememberSaveable { mutableStateOf(initial?.groupId) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val colorHex = initial?.colorHex
    val canSave = name.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                colorHex?.let(::parseHex)?.let { color ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(color)
                            .testTag("list-colour-dot"),
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.list_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("list-name-field"),
                )
            }
            if (groups.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.list_group_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GroupOption(stringResource(R.string.list_no_group), groupId == null, "none") { groupId = null }
                groups.forEach { group ->
                    GroupOption(group.name, groupId == group.id, group.id.toString()) { groupId = group.id }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (onDelete != null) {
                    TextButton(
                        onClick = { confirmingDelete = true },
                        modifier = Modifier.testTag("list-delete"),
                    ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                Button(
                    onClick = { onSave(name.trim(), colorHex, groupId) },
                    enabled = canSave,
                    modifier = Modifier.testTag("list-save"),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }

    if (confirmingDelete && onDelete != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_list_title),
            message = stringResource(R.string.delete_list_moves_tasks),
            onConfirm = onDelete,
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun GroupOption(label: String, selected: Boolean, tagSuffix: String, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag("list-group-option-$tagSuffix"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 12.dp))
    }
}


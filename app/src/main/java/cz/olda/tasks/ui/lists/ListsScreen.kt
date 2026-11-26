package cz.olda.tasks.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.olda.tasks.R
import cz.olda.tasks.ui.components.ConfirmDeleteDialog
import cz.olda.tasks.ui.components.ScreenHeader
import cz.olda.tasks.ui.AppIcons
import cz.olda.tasks.data.entity.GroupEntity
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.ui.theme.parseHex
import cz.olda.tasks.ui.theme.textShade

/** Groups as headers, their lists as rows with a colour dot and the number of open tasks. */
@Composable
fun ListsScreen(
    viewModel: ListsViewModel,
    onOpenList: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val counts by viewModel.openCounts.collectAsStateWithLifecycle()
    val groups = state

    // Ids rather than entities, so an edit survives rotation and always shows the latest row.
    var creatingList by rememberSaveable { mutableStateOf(false) }
    var editingListId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creatingGroup by rememberSaveable { mutableStateOf(false) }
    var editingGroupId by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen-Lists")
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = stringResource(R.string.lists),
            titleModifier = Modifier.testTag("lists-header"),
            trailing = {
                IconButton(onClick = onOpenSearch, modifier = Modifier.testTag("open-search")) {
                    Icon(AppIcons.Search, contentDescription = stringResource(R.string.search))
                }
            },
        )
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { creatingList = true }, modifier = Modifier.testTag("new-list")) {
                    Text(stringResource(R.string.new_list))
                }
                TextButton(onClick = { creatingGroup = true }, modifier = Modifier.testTag("new-group")) {
                    Text(stringResource(R.string.new_group))
                }
            }
            groups?.forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.group?.let { group ->
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { editingGroupId = group.id }
                                .padding(horizontal = 4.dp, vertical = 12.dp)
                                .testTag("group-${group.id}"),
                        )
                    }
                    entry.lists.forEach { list ->
                        ListRow(
                            list = list,
                            openCount = counts?.let { it[list.id] ?: 0 },
                            onOpen = { onOpenList(list.id) },
                            onEdit = { editingListId = list.id },
                        )
                    }
                }
            }
            if (groups != null && groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.lists_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("lists-empty"),
                )
            }
        }
    }

    val allGroups = groups.orEmpty().mapNotNull { it.group }
    val editingList = groups.orEmpty().flatMap { it.lists }.firstOrNull { it.id == editingListId && !it.isInbox }
    if (creatingList) {
        ListEditSheet(
            initial = null,
            onSave = { name, colorHex, groupId ->
                viewModel.createList(name, colorHex, groupId)
                creatingList = false
            },
            onDelete = null,
            onDismiss = { creatingList = false },
            groups = allGroups,
        )
    }
    if (editingList != null) {
        ListEditSheet(
            initial = editingList,
            onSave = { name, colorHex, groupId ->
                if (name != editingList.name) viewModel.renameList(editingList.id, name)
                if (colorHex != null && colorHex != editingList.colorHex) viewModel.recolourList(editingList.id, colorHex)
                if (groupId != editingList.groupId) viewModel.moveListToGroup(editingList.id, groupId)
                editingListId = null
            },
            onDelete = {
                viewModel.deleteList(editingList.id)
                editingListId = null
            },
            onDismiss = { editingListId = null },
            groups = allGroups,
        )
    }
    if (creatingGroup) {
        GroupEditDialog(
            initial = null,
            onSave = { viewModel.createGroup(it); creatingGroup = false },
            onDelete = null,
            onDismiss = { creatingGroup = false },
        )
    }
    val editingGroup = allGroups.firstOrNull { it.id == editingGroupId }
    if (editingGroup != null) {
        GroupEditDialog(
            initial = editingGroup,
            onSave = { viewModel.renameGroup(editingGroup.id, it); editingGroupId = null },
            onDelete = { viewModel.deleteGroup(editingGroup.id); editingGroupId = null },
            onDismiss = { editingGroupId = null },
        )
    }
}

@Composable
private fun ListRow(
    list: ListEntity,
    openCount: Int?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colour = parseHex(list.colorHex) ?: MaterialTheme.colorScheme.onSurfaceVariant
    // The tile is the list's colour at a sixth of its strength, with the initial in the shade
    // that keeps text readable on it. The mockups put a per-list icon here; `lists.icon` exists
    // but nothing writes it, so every tile would have carried the same glyph.
    val tileColour = colour.copy(alpha = 0.16f)
    val initialColour = textShade(colour, darkTheme)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .testTag("list-row-${list.id}"),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tileColour),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = list.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = initialColour,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (openCount != null) {
                    Text(
                        text = stringResource(R.string.list_open_count, openCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.testTag("list-count-${list.id}"),
                    )
                }
            }
            TextButton(onClick = onEdit, modifier = Modifier.testTag("list-edit-${list.id}")) {
                Text(stringResource(R.string.edit_list))
            }
        }
    }
}

/** Names a new group, or renames or deletes one; deleting leaves its lists in place. */
@Composable
private fun GroupEditDialog(
    initial: GroupEntity?,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_group else R.string.edit_group)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.group_name_hint)) },
                singleLine = true,
                modifier = Modifier.testTag("group-name-field"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("group-save"),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { confirmingDelete = true }, modifier = Modifier.testTag("group-delete")) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
    if (confirmingDelete && onDelete != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_group_title),
            message = stringResource(R.string.delete_group_keeps_lists),
            onConfirm = onDelete,
            onDismiss = { confirmingDelete = false },
        )
    }
}

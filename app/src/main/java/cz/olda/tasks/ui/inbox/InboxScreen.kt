package cz.olda.tasks.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.olda.tasks.R
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.ui.components.EmptyStateText
import cz.olda.tasks.ui.components.ScreenHeader
import cz.olda.tasks.ui.components.TaskListSection
import cz.olda.tasks.ui.components.rememberCompletionUndo

/** The tasks that have not been filed into a list yet. */
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tasks by viewModel.state.collectAsStateWithLifecycle()
    val complete = rememberCompletionUndo(
        onComplete = viewModel::complete,
        onUndo = viewModel::undoCompletion,
    )
    InboxContent(tasks = tasks, onToggleDone = complete, onOpenTask = onOpenTask, modifier = modifier)
}

/** The stateless body of [InboxScreen]; [tasks] is null until the first load. */
@Composable
fun InboxContent(
    tasks: List<TaskWithList>?,
    onToggleDone: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen-Inbox")
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = stringResource(R.string.inbox), titleModifier = Modifier.testTag("inbox-header"))
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TaskListSection(null, tasks.orEmpty(), onToggleDone, onOpenTask, showDate = true, showList = false)
            if (tasks != null && tasks.isEmpty()) {
                EmptyStateText(
                    text = stringResource(R.string.inbox_empty),
                    modifier = Modifier.testTag("inbox-empty"),
                )
            }
        }
    }
}

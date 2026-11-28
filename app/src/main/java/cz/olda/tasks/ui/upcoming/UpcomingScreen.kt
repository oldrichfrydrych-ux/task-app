package cz.olda.tasks.ui.upcoming

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
import cz.olda.tasks.data.UpcomingDay
import cz.olda.tasks.ui.components.EmptyStateText
import cz.olda.tasks.ui.components.ScreenHeader
import cz.olda.tasks.ui.components.TaskListSection
import cz.olda.tasks.ui.components.rememberCompletionUndo
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Open tasks due after today, a section per day with the date as its header. */
@Composable
fun UpcomingScreen(
    viewModel: UpcomingViewModel,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val complete = rememberCompletionUndo(
        onComplete = viewModel::complete,
        onUndo = viewModel::undoCompletion,
    )
    UpcomingContent(days = state.days, onToggleDone = complete, onOpenTask = onOpenTask, modifier = modifier)
}

/** The stateless body of [UpcomingScreen]; [days] is null until the first load. */
@Composable
fun UpcomingContent(
    days: List<UpcomingDay>?,
    onToggleDone: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen-Upcoming")
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = stringResource(R.string.upcoming), titleModifier = Modifier.testTag("upcoming-header"))
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            days?.forEach { day ->
                TaskListSection(
                    title = day.date.format(formatter),
                    items = day.tasks,
                    onToggleDone = onToggleDone,
                    onClick = onOpenTask,
                    headerModifier = Modifier.testTag("upcoming-day-${day.date}"),
                )
            }
            if (days != null && days.isEmpty()) {
                EmptyStateText(
                    text = stringResource(R.string.upcoming_empty),
                    modifier = Modifier.testTag("upcoming-empty"),
                )
            }
        }
    }
}

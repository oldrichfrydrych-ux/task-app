package cz.olda.tasks.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.olda.tasks.R
import cz.olda.tasks.ui.components.TaskListSection
import cz.olda.tasks.ui.components.rememberCompletionUndo
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One day's tasks under a week strip; overdue and tomorrow appear only while the day is today. */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val complete = rememberCompletionUndo(
        onComplete = viewModel::toggleDone,
        onUndo = viewModel::undoCompletion,
    )
    val sections = state.sections

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen-Today")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (state.isToday) {
                stringResource(R.string.today)
            } else {
                state.shownDay.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()),
                )
            },
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("today-header"),
        )
        WeekStrip(
            weekStart = state.weekStart,
            shownDay = state.shownDay,
            dots = state.dots,
            onPickDay = viewModel::showDay,
        )
        TaskListSection(stringResource(R.string.section_overdue), sections.overdue, complete, onOpenTask)
        TaskListSection(null, sections.timed, complete, onOpenTask)
        TaskListSection(stringResource(R.string.section_anytime), sections.anytime, complete, onOpenTask)
        TaskListSection(stringResource(R.string.section_tomorrow), sections.tomorrow, complete, onOpenTask)
    }
}

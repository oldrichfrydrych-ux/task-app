package cz.olda.tasks.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
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
import java.time.LocalDate
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
        onComplete = viewModel::complete,
        onUndo = viewModel::undoCompletion,
    )
    TodayContent(
        state = state,
        onToggleDone = complete,
        onOpenTask = onOpenTask,
        onPickDay = viewModel::showDay,
        onBackToToday = viewModel::showToday,
        modifier = modifier,
    )
}

/** The stateless body of [TodayScreen]; shows no empty text until [TodayState.isLoaded]. */
@Composable
fun TodayContent(
    state: TodayState,
    onToggleDone: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onPickDay: (LocalDate) -> Unit,
    onBackToToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = state.sections

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen-Today")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state.isToday) {
                    stringResource(R.string.today)
                } else {
                    state.shownDay.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()),
                    )
                },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).testTag("today-header"),
            )
            if (!state.isToday) {
                TextButton(
                    onClick = onBackToToday,
                    modifier = Modifier.testTag("back-to-today"),
                ) { Text(stringResource(R.string.back_to_today)) }
            }
        }
        WeekStrip(
            weekStart = state.weekStart,
            shownDay = state.shownDay,
            dots = state.dots,
            onPickDay = onPickDay,
        )
        TaskListSection(stringResource(R.string.section_overdue), sections.overdue, onToggleDone, onOpenTask)
        TaskListSection(null, sections.timed, onToggleDone, onOpenTask)
        TaskListSection(stringResource(R.string.section_anytime), sections.anytime, onToggleDone, onOpenTask)
        TaskListSection(stringResource(R.string.section_tomorrow), sections.tomorrow, onToggleDone, onOpenTask)
        if (state.isLoaded && listOf(sections.overdue, sections.timed, sections.anytime, sections.tomorrow).all { it.isEmpty() }) {
            Text(
                text = stringResource(if (state.isToday) R.string.today_empty else R.string.day_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("today-empty"),
            )
        }
    }
}

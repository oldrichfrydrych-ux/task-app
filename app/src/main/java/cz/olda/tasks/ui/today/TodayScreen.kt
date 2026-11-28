package cz.olda.tasks.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cz.olda.tasks.ui.components.RepeatsChip
import cz.olda.tasks.ui.components.FoldedRepeatsRow
import cz.olda.tasks.R
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.ui.components.EmptyStateText
import cz.olda.tasks.ui.components.RailCentre
import cz.olda.tasks.ui.components.ScreenHeader
import cz.olda.tasks.ui.components.TaskListSection
import cz.olda.tasks.ui.components.formatDueDay
import cz.olda.tasks.ui.components.rememberCompletionUndo
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The side margin the mockups use. Task rows get it too, rather than being pulled closer to the
 * edge to put their rings under the title: the same margin on every screen is what stops the rows
 * sliding sideways as Olda moves between tabs, and that is more noticeable than where the rail is.
 */
private val SideMargin = 20.dp

/** How far the rail line stops short of the first and last row's middle. */
private val RailInset = 22.dp

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
        onHideRepeats = viewModel::setHideRepeats,
        modifier = modifier,
    )
}

/**
 * The stateless body of [TodayScreen]; shows no empty text until [TodayState.isLoaded].
 *
 * [now] is a parameter rather than read inside, so a test can say what time it is and the now row
 * lands somewhere predictable.
 */
@Composable
fun TodayContent(
    state: TodayState,
    onToggleDone: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onPickDay: (LocalDate) -> Unit,
    onBackToToday: () -> Unit,
    onHideRepeats: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    now: LocalTime = LocalTime.now(),
) {
    val sections = state.sections
    // A peek at the hidden repeats lasts the visit and no longer: the screen's own state, not a
    // setting, so looking costs one tap and undoes itself.
    var peeking by rememberSaveable { mutableStateOf(false) }
    val nowMinute = now.hour * 60 + now.minute
    // The now line goes between the two tasks the time falls between, so the timed section is
    // split in two rather than having a line dropped on top of it. With no timed task there is
    // nothing for it to sit between, and on another day it would mark a moment that has nothing
    // to do with what is on screen.
    val showNow = state.isToday && sections.timed.isNotEmpty()
    val (beforeNow, afterNow) = if (showNow) {
        sections.timed.partition { (it.task.dueMinute ?: 0) <= nowMinute }
    } else {
        sections.timed to emptyList<TaskWithList>()
    }
    val tomorrow = state.shownDay.plusDays(1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen-Today")
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = if (state.isToday) {
                stringResource(R.string.today)
            } else {
                state.shownDay.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()),
                )
            },
            // The date belongs beside the word "Today", which on its own does not say which day
            // it is; on any other day the title is already the full date.
            subtitle = if (state.isToday) formatDueDay(state.shownDay.toEpochDay()) else null,
            titleModifier = Modifier.testTag("today-header"),
            trailing = {
                if (state.isToday) {
                    ViewSwitch()
                } else {
                    TextButton(
                        onClick = onBackToToday,
                        modifier = Modifier.testTag("back-to-today"),
                    ) { Text(stringResource(R.string.back_to_today)) }
                }
            },
        )
        Column(
            modifier = Modifier.padding(horizontal = SideMargin).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeekStrip(
                weekStart = state.weekStart,
                shownDay = state.shownDay,
                dots = state.dots,
                onPickDay = onPickDay,
            )
            // The day's own tasks only: overdue rows belong to days that have gone, and the done
            // count behind "of 7" counts this day alone.
            // The hidden repeats are counted too: the chip changes which rows are listed and
            // nothing that counts a day, or the "of 7" would shrink while the done count behind
            // its "2" stayed put, and one sentence would disagree with itself.
            DayGlance(
                open = sections.timed + sections.anytime + sections.hiddenRepeats,
                doneCount = state.doneCount,
            )
            RepeatsChip(hidden = state.hideRepeats, onToggle = onHideRepeats)
        }

        Box(modifier = Modifier.padding(horizontal = SideMargin).padding(top = 10.dp)) {
            // Drawn before the rows and so behind them. An open ring is filled with the page
            // colour, which is what stops the line showing through the middle of a circle.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = RailCentre - 1.dp, top = RailInset, bottom = RailInset),
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .testTag("today-rail"),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TaskListSection(stringResource(R.string.section_overdue), sections.overdue, onToggleDone, onOpenTask)
                TaskListSection(null, beforeNow, onToggleDone, onOpenTask)
                if (showNow) NowRow(nowMinute)
                TaskListSection(null, afterNow, onToggleDone, onOpenTask)
                TaskListSection(stringResource(R.string.section_anytime), sections.anytime, onToggleDone, onOpenTask)
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = SideMargin).padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sections.hiddenRepeats.isNotEmpty()) {
                FoldedRepeatsRow(
                    count = sections.hiddenRepeats.size,
                    expanded = peeking,
                    onToggle = { peeking = !peeking },
                )
                if (peeking) {
                    TaskListSection(null, sections.hiddenRepeats, onToggleDone, onOpenTask)
                }
            }
            // "Nothing due today" must never sit above a row saying two are hidden.
            if (state.isLoaded &&
                sections.hiddenRepeats.isEmpty() &&
                listOf(sections.overdue, sections.timed, sections.anytime).all { it.isEmpty() }
            ) {
                EmptyStateText(
                    text = stringResource(if (state.isToday) R.string.today_empty else R.string.day_empty),
                    modifier = Modifier.testTag("today-empty"),
                )
            }
            if (state.isToday && sections.tomorrow.isNotEmpty()) {
                TomorrowRow(
                    date = tomorrow,
                    count = sections.tomorrow.size,
                    onOpen = { onPickDay(tomorrow) },
                )
            }
        }
    }
}

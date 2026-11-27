package cz.olda.tasks.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.data.entity.TaskEntity
import cz.olda.tasks.ui.theme.ListPalette
import cz.olda.tasks.ui.theme.parseHex
import cz.olda.tasks.ui.theme.textShade
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

/** The priority dash at a row's leading edge: as tall as the ring it sits beside, and no taller. */
private val PriorityMarkWidth = 3.dp
private val PriorityMarkHeight = 18.dp

/**
 * The compact rail: a ring in the list's colour, the start time, the title, and the list's name
 * behind it in that colour. The row carries no card - it sits straight on the page, and on Today
 * the section draws the rail line down the ring column behind it.
 *
 * No fixed heights, so at large font sizes the row grows instead of clipping; the
 * title takes whatever width is left and wraps to two lines. When the title column would be
 * narrower than the longest word, time and list name drop under the title instead. A title
 * longer than about two lines at large font is truncated by design; the full title is on the
 * task detail screen.
 *
 * Priority 1 to 3 add a small coloured bar on the leading edge (overlaid, so it takes no width). With
 * [showDate] the due day goes in front of the time, for lists that are not grouped by day - without
 * its weekday, which on a date weeks past says nothing and costs the title a line.
 *
 * [showList] is for screens where every row has the same list - the Inbox, one list's own screen.
 * There the name is noise holding width the title needs. Search keeps it: that is where it tells
 * you something.
 *
 * [timeColumn] fixes the width of the left slot so a whole section's titles start in the same
 * place, which a row cannot work out alone - its neighbours' dates are what decide it. The
 * section measures them and hands the width down; a row given one reserves it even when it has
 * no time of its own, which is the point.
 *
 * A [TaskWithList.listColorHex] that cannot be parsed falls back to the theme's secondary text
 * colour rather than crashing.
 */
@Composable
fun TaskRow(
    item: TaskWithList,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    showList: Boolean = true,
    showAsDone: Boolean = false,
    timeColumn: Dp? = null,
) {
    val task = item.task
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // The list colour is its identity; text in it uses a shade that keeps 4.5:1 contrast.
    val listColor = parseHex(item.listColorHex)?.let { textShade(it, darkTheme) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    // The ring is a graphic, not text, so it keeps the list's raw colour; only text needs a shade.
    val ringColor = parseHex(item.listColorHex) ?: MaterialTheme.colorScheme.onSurfaceVariant
    // Date and time share the rail's left slot, stacked, so the width maths below covers both.
    val rowTime = rowTime(task, showDate)
    val priorityColor = priorityBarColor(task.priority, darkTheme)

    // A row that is folding away has already been written as done, but the copy still being
    // drawn is the one from before the write. showAsDone lets the fold show what happened.
    val done = task.done || showAsDone

    // Bold, as the mockups set it - and the same style the width maths below measures with, or a
    // long Czech word would be measured narrower than it is drawn and break mid-word.
    val titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)

    val checkbox: @Composable () -> Unit = {
        TaskRing(
            done = done,
            color = ringColor,
            onToggle = onToggleDone,
            modifier = Modifier.testTag("task-checkbox-${task.id}"),
            ringModifier = Modifier.testTag("task-ring-${task.id}"),
        )
    }
    val time: @Composable () -> Unit = {
        if (!rowTime.isEmpty) {
            Column(modifier = Modifier.testTag("task-time-${task.id}")) {
                if (rowTime.day != null) {
                    Text(
                        text = rowTime.day,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag("task-day-${task.id}"),
                    )
                }
                if (rowTime.clock != null) {
                    Text(
                        text = rowTime.clock,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag("task-clock-${task.id}"),
                    )
                }
            }
        }
    }
    val title: @Composable (Modifier) -> Unit = { m ->
        Text(
            text = task.title,
            style = titleStyle,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = m.padding(vertical = 8.dp).testTag("task-title-${task.id}"),
        )
    }
    val listName: @Composable (Modifier) -> Unit = { m ->
        if (showList) Text(
            text = item.listName,
            style = MaterialTheme.typography.labelMedium,
            color = listColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = m.testTag("task-list-${task.id}"),
        )
    }

    val measurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        // Stack when the rail would leave the title column narrower than its longest word, which
        // cannot wrap and would otherwise break mid-word. Depends on width and font scale alike.
        // Stacked, time and list name share one line when both fit on it, else one line each.
        val layout = remember(maxWidth, item, rowTime, showList, timeColumn, density) {
            val longestWord = task.title.split(Regex("[ ]+")).maxOf {
                measurer.measure(it, titleStyle).size.width
            }
            val gap = with(density) { 10.dp.roundToPx() }
            // A section's column, when it set one: the row is held to it whether its own date is
            // that wide or not, so measuring this row's own lines would be the wrong number. On
            // its own, the slot is as wide as the wider of the two stacked lines.
            val timeMeasured = timeColumn?.let { with(density) { it.roundToPx() } }
                ?: rowTime.lines.maxOfOrNull {
                    measurer.measure(it, typography.labelLarge, softWrap = false).size.width
                }
                ?: 0
            val timeWidth = if (timeMeasured > 0) timeMeasured + gap else 0
            // A row without its list name has that width, and the gap before it, back for the title.
            val listMeasured = if (showList) {
                measurer.measure(item.listName, typography.labelMedium, softWrap = false).size.width
            } else {
                0
            }
            val listWidth = minOf(listMeasured, with(density) { 120.dp.roundToPx() })
            val listGap = if (showList) with(density) { 10.dp.roundToPx() } else 0
            val fixed = with(density) { (4 + 48 + 12).dp.roundToPx() } + listGap + timeWidth + listWidth
            val width = with(density) { maxWidth.roundToPx() }
            val available = width - fixed
            // The longest word fitting is not the same as the title fitting: a two-line title can
            // still need a third line in a narrow column, and then it is ellipsized. Measuring it
            // capped at two lines in the width it would actually get answers that properly, and it
            // is what the stacked layout is for.
            val titleFits = available > 0 && !measurer.measure(
                text = task.title,
                style = titleStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                constraints = Constraints(maxWidth = available),
            ).hasVisualOverflow
            when {
                available >= longestWord && titleFits -> RowLayout.Rail
                !rowTime.isEmpty &&
                    timeMeasured + gap + listMeasured > width - with(density) { (4 + 48 + 12).dp.roundToPx() } ->
                    RowLayout.StackedSplit
                else -> RowLayout.Stacked
            }
        }
        val stacked = layout != RowLayout.Rail

        // No card of its own: in the mockups a row sits straight on the page and only the rail
        // is drawn behind it. The priority bar is an overlay rather than part of the Row, so it
        // takes no width and the stacking maths above is untouched.
        Box(modifier = Modifier.testTag("task-${task.id}")) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .heightIn(min = 48.dp)
                    .padding(start = 4.dp, end = 12.dp),
            ) {
                checkbox()
                if (stacked) {
                    Column(modifier = Modifier.weight(1f)) {
                        title(Modifier)
                        if (layout == RowLayout.StackedSplit) {
                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                time()
                                listName(Modifier)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(bottom = 8.dp),
                            ) {
                                time()
                                listName(Modifier.weight(1f, fill = false))
                            }
                        }
                    }
                } else {
                    if (timeColumn != null) {
                        // Held open even for a row with no time of its own, so its title starts
                        // where its neighbours' do.
                        Box(modifier = Modifier.width(timeColumn)) { time() }
                        Spacer(Modifier.width(10.dp))
                    } else if (!rowTime.isEmpty) {
                        time()
                        Spacer(Modifier.width(10.dp))
                    }
                    title(Modifier.weight(1f))
                    if (showList) {
                        Spacer(Modifier.width(10.dp))
                        listName(Modifier.widthIn(max = 120.dp))
                    }
                }
            }
            if (priorityColor != null) {
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // A short dash beside the ring, not a bar down the whole row. While rows were
                    // cards, a full-height bar on the card's edge was the obvious place for it;
                    // on the rail it became a second vertical line next to the rail's own, and
                    // the eye read the two as one broken thing. It keeps its colour and its
                    // height matches the ring beside it, so the row has one mark, not a stripe.
                    Box(
                        modifier = Modifier
                            .size(width = PriorityMarkWidth, height = PriorityMarkHeight)
                            .clip(CircleShape)
                            .background(priorityColor)
                            .testTag("task-priority-bar-${task.id}"),
                    )
                }
            }
        }
    }
}

/**
 * What a row shows in its left slot: the due day on one line and the time of day under it, either
 * of which may be missing. Two lines rather than one string, so the column is as wide as the wider
 * of them instead of as wide as both plus a space - which is width the title gets back.
 */
internal data class RowTime(val day: String?, val clock: String?) {
    val isEmpty: Boolean get() = day == null && clock == null

    /** Both lines, for measuring: the column has to hold the wider one. */
    val lines: List<String> get() = listOfNotNull(day, clock)
}

/**
 * Builds [RowTime] for a task. Shared with [TaskListSection], which measures every row's lines to
 * give a whole section one column width - so the two cannot be allowed to build them differently.
 */
@Composable
internal fun rowTime(task: TaskEntity, showDate: Boolean): RowTime = RowTime(
    day = if (showDate) task.dueDate?.let { formatDueDay(it, weekday = false) } else null,
    clock = task.dueMinute?.let { formatMinuteOfDay(it) },
)

/** Priority 1 to 3 are Red, Orange and Blue (docs/design.md, Colours); 4 has no colour. */
private fun priorityBarColor(priority: Int, darkTheme: Boolean): Color? {
    val name = when (priority) {
        1 -> "Red"
        2 -> "Orange"
        3 -> "Blue"
        else -> return null
    }
    val preset = ListPalette.first { it.name == name }
    return if (darkTheme) preset.dark else preset.light
}

/**
 * The due day in the device's language, short ("Mon 23 Nov"); the year only when it is not this
 * one. Without [weekday] it is "23 Nov" - which is what a task row uses, because the weekday of a
 * date weeks past tells you nothing and the name of the day is wide. Where the day itself is the
 * point, as in Today's row for tomorrow, it stays.
 */
@Composable
internal fun formatDueDay(epochDay: Long, weekday: Boolean = true): String {
    val locale = Locale.getDefault()
    return remember(epochDay, locale, weekday) {
        val day = LocalDate.ofEpochDay(epochDay)
        val prefix = if (weekday) "EEE" else ""
        val skeleton = if (day.year == LocalDate.now().year) "${prefix}d MMM" else "${prefix}d MMM y"
        day.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
    }
}

/** Formats minutes since midnight with the device's locale and 12/24-hour setting. */
@Composable
internal fun formatMinuteOfDay(minute: Int): String {
    val context = LocalContext.current
    return remember(minute, context) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
        }
        DateFormat.getTimeFormat(context).format(cal.time)
    }
}

/** How a row lays out its title, time and list name for the width it has. */
private enum class RowLayout { Rail, Stacked, StackedSplit }

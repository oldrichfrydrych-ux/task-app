package cz.olda.tasks.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.olda.tasks.ui.theme.parseHex
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Most dots shown under one day; more would not fit the column. */
private const val MaxDots = 4

/**
 * Seven days from [weekStart], each with a dot per list colour that has an open task that day.
 * The [shownDay] is filled in; tapping any day calls [onPickDay]. Each day is tagged "day-<date>".
 */
@Composable
fun WeekStrip(
    weekStart: LocalDate,
    shownDay: LocalDate,
    dots: Map<LocalDate, List<String>>,
    onPickDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0L..6L).map { weekStart.plusDays(it) }.forEach { date ->
            val isShown = date == shownDay
            val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            // One filled thing in the strip and it is the day you are looking at: an ink pill, as
            // the mockups have it. Coral is the add button's colour and means "do something",
            // which a date does not. The other six sit on the page with no fill at all, and the
            // weekend is dimmed so the working week reads first.
            val container = if (isShown) MaterialTheme.colorScheme.onBackground else Color.Transparent
            val content = when {
                isShown -> MaterialTheme.colorScheme.background
                isWeekend -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onBackground
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag("day-$date")
                    .clip(RoundedCornerShape(16.dp))
                    .background(container)
                    .semantics { selected = isShown }
                    .clickable(role = Role.Tab) { onPickDay(date) }
                    .heightIn(min = 64.dp)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = content,
                    maxLines = 1,
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.heightIn(min = 6.dp),
                ) {
                    dots[date].orEmpty().take(MaxDots).forEach { hex ->
                        Box(
                            modifier = Modifier
                                .testTag("dot-$date")
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(parseHex(hex) ?: content),
                        )
                    }
                }
            }
        }
    }
}

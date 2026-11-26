package cz.olda.tasks.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
import cz.olda.tasks.ui.components.RingTouchTarget
import cz.olda.tasks.ui.components.RowLeadingPadding
import cz.olda.tasks.ui.components.formatMinuteOfDay

/**
 * Where the clock has got to in the rail: a dot in the ring column, the time beside it, and a
 * line across the rest of the row.
 *
 * It lines up with the task rows by using their own ring column, so the dot sits exactly on the
 * rail. Only Today draws it, and only between the tasks the time falls between - a now line on a
 * day that is not today would be marking a moment that has nothing to do with what is on screen.
 *
 * The time is read out as "Now, 14:10" rather than as a bare number, so the dot and the line do
 * not have to be seen for the row to mean anything.
 */
@Composable
fun NowRow(minuteOfDay: Int, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.secondary
    val clock = formatMinuteOfDay(minuteOfDay)
    val spoken = "${stringResource(R.string.now)}, $clock"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .testTag("now-row")
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.padding(start = RowLeadingPadding).width(RingTouchTarget),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(line))
        }
        Text(
            text = clock,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f).height(2.dp).background(line))
    }
}

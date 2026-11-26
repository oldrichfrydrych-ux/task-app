package cz.olda.tasks.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R

/** Both segments stay comfortably tappable, so the switch does not shrink with its labels. */
private val SegmentHeight = 40.dp

/**
 * The List / Day switch from the top right of the mockups.
 *
 * Day is drawn but disabled, and that is the whole point of it being here: the day view needs a
 * length for every task, and nothing in the app writes one yet
 * (`docs/superpowers/plans/2026-11-26-look-redesign.md`). Leaving the switch out would have hidden
 * what Today is going to be; leaving Day live would have been a button that does nothing. It says
 * so out loud instead - greyed, not clickable, and with a description a screen reader can read.
 *
 * There is nothing to switch to, so this takes no state and no callback. The day half arriving is
 * what turns it into a real control.
 */
@Composable
fun ViewSwitch(modifier: Modifier = Modifier) {
    val unavailable = stringResource(R.string.view_day_unavailable)
    val switchLabel = stringResource(R.string.view_switch)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
            .semantics { contentDescription = switchLabel }
            .testTag("view-switch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Segment(
            label = stringResource(R.string.view_list),
            selected = true,
            modifier = Modifier.testTag("view-list"),
        )
        Segment(
            label = stringResource(R.string.view_day),
            selected = false,
            modifier = Modifier
                .semantics {
                    disabled()
                    contentDescription = unavailable
                }
                .testTag("view-day"),
        )
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent)
            .heightIn(min = SegmentHeight)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

package cz.olda.tasks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
import cz.olda.tasks.ui.AppIcons

/**
 * The ring's touch target, and so the width of a row's leading column. The rail line is drawn
 * down its centre, which is why this number is shared rather than written twice.
 */
internal val RingTouchTarget = 48.dp

/** A row's padding before its ring, so every row's ring column starts in the same place. */
internal val RowLeadingPadding = 4.dp

/** Where the rail line runs: down the middle of the ring column. */
internal val RailCentre = RowLeadingPadding + RingTouchTarget / 2

/** The circle itself, centred in the touch target. */
private val RingSize = 18.dp

/**
 * What completes a task: an open circle in its list's own colour, filled with a tick once it is
 * done. The colour is the list's raw one, not the text shade - this is a graphic, not text, and
 * the mockups mark the row with the list's identity here rather than with a grey box.
 *
 * The ring is 18dp but its touch target is [RingTouchTarget], so the row stays comfortable to
 * tick at any font size.
 *
 * [modifier] carries the row's checkbox test tag and so lands on the node that holds the toggle
 * action; [ringModifier] tags the drawn circle, which is what a pixel test can sample.
 */
@Composable
fun TaskRing(
    done: Boolean,
    color: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    ringModifier: Modifier = Modifier,
) {
    val completeLabel = stringResource(R.string.complete_task)
    Box(
        modifier = modifier
            .size(RingTouchTarget)
            .clip(CircleShape)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = completeLabel },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(
                modifier = ringModifier.size(RingSize).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    // The page colour, so the tick reads as a hole in the disc whichever
                    // list colour is under it.
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(12.dp),
                )
            }
        } else {
            // Filled with the page colour, not left transparent: on Today the rail line runs
            // straight through this column, and an open ring has to hide it rather than have it
            // cross the middle of the circle.
            Box(
                modifier = ringModifier
                    .size(RingSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .border(3.dp, color, CircleShape),
            )
        }
    }
}

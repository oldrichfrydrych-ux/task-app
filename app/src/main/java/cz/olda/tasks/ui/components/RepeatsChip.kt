package cz.olda.tasks.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
import cz.olda.tasks.ui.AppIcons

/**
 * The switch that takes repeating tasks off Today and Upcoming.
 *
 * Olda chose a labelled chip on 28 Nov over an icon in the header: the switch is rare enough that a
 * bare icon would need remembering, and Upcoming has no List/Day switch for one to pair with, so an
 * icon would have looked like two different features on two screens.
 *
 * It says what it will do when repeats are showing and what is true when they are not, which is why
 * the two states have different words rather than one label and a tick.
 */
@Composable
fun RepeatsChip(hidden: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = hidden,
        onClick = { onToggle(!hidden) },
        label = {
            Text(stringResource(if (hidden) R.string.repeats_hidden else R.string.repeats_hide))
        },
        leadingIcon = {
            Icon(
                imageVector = AppIcons.Repeat,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        modifier = modifier.testTag("repeats-chip"),
    )
}

package cz.olda.tasks.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
import cz.olda.tasks.ui.AppIcons
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * How a repeat is set, changed and removed. Phase 2 could only clear one; this is the picker it
 * said Phase 3 would build, and Olda chose this shape on 28 Nov over a sentence builder and a text
 * field read by the parser: "every day" and "every Monday" are nearly everything he will ever set,
 * and a list of rows makes each of them one tap while still showing what the app can do.
 *
 * Every row is labelled through [recurrenceLabel], the same function task detail reads its Repeat
 * row with, so the picker and the screen behind it can never disagree about what a rule means.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatPickerSheet(
    current: String?,
    anchor: LocalDate,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        RepeatPickerContent(
            current = current,
            anchor = anchor,
            onPick = { rule ->
                onPick(rule)
                onDismiss()
            },
        )
    }
}

@Composable
internal fun RepeatPickerContent(
    current: String?,
    anchor: LocalDate,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fromCurrent = current?.let { customOf(it) }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    var interval by rememberSaveable { mutableStateOf(fromCurrent?.first ?: 1) }
    var unit by rememberSaveable { mutableStateOf(fromCurrent?.second ?: RepeatUnit.DAYS) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            .testTag("repeat-picker"),
    ) {
        Text(
            text = stringResource(R.string.repeat_choose),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Option(
            text = stringResource(R.string.repeat_none),
            ticked = current == null,
            tag = "none",
            onClick = { onPick(null) },
        )
        presetRules(anchor).forEach { rule ->
            Option(
                text = labelOf(rule),
                ticked = current == rule,
                tag = rule,
                onClick = { onPick(rule) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Option(
            text = stringResource(R.string.repeat_custom),
            ticked = fromCurrent != null,
            tag = "custom",
            onClick = { customOpen = true },
        )

        if (customOpen) {
            CustomRow(
                interval = interval,
                unit = unit,
                onInterval = { interval = it },
                onUnit = { unit = it },
                onSave = { onPick(customRule(interval, unit)) },
            )
        }
    }
}

/** One row of the list: its wording, a tick when it is what the task has now, and a touch target. */
@Composable
private fun Option(text: String, ticked: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            // The tick is a graphic with no words, so what "this is the current one" means to a
            // screen reader - and to a test - is the row's own selected state.
            .semantics { selected = ticked }
            .testTag("repeat-option-$tag"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (ticked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (ticked) FontWeight.Bold else FontWeight.Normal,
        )
        if (ticked) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp).testTag("repeat-tick-$tag"),
            )
        }
    }
}

/** "Every [2] [weeks]", for the intervals the four rows above cannot say. */
@Composable
private fun CustomRow(
    interval: Int,
    unit: RepeatUnit,
    onInterval: (Int) -> Unit,
    onUnit: (RepeatUnit) -> Unit,
    onSave: () -> Unit,
) {
    var unitMenu by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.repeat_every), style = MaterialTheme.typography.bodyMedium)
            IconButton(
                onClick = { onInterval((interval - 1).coerceAtLeast(1)) },
                modifier = Modifier
                    .testTag("repeat-custom-fewer")
                    .semantics { contentDescription = "fewer" },
            ) { Text("−", style = MaterialTheme.typography.titleMedium) }
            Text(
                text = interval.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("repeat-custom-interval"),
            )
            IconButton(
                onClick = { onInterval(interval + 1) },
                modifier = Modifier
                    .testTag("repeat-custom-more")
                    .semantics { contentDescription = "more" },
            ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(
                onClick = { unitMenu = true },
                modifier = Modifier.testTag("repeat-custom-unit"),
            ) { Text(stringResource(unit.labelId())) }
            DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                RepeatUnit.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelId())) },
                        onClick = {
                            onUnit(option)
                            unitMenu = false
                        },
                        modifier = Modifier.testTag("repeat-custom-unit-${option.name}"),
                    )
                }
            }
        }
        TextButton(onClick = onSave, modifier = Modifier.testTag("repeat-custom-save")) {
            Text(stringResource(R.string.save))
        }
    }
}

private fun RepeatUnit.labelId(): Int = when (this) {
    RepeatUnit.DAYS -> R.string.repeat_unit_days
    RepeatUnit.WEEKS -> R.string.repeat_unit_weeks
    RepeatUnit.MONTHS -> R.string.repeat_unit_months
    RepeatUnit.YEARS -> R.string.repeat_unit_years
}

/** The same wording task detail uses, so the two can never disagree. */
@Composable
internal fun labelOf(rule: String): String {
    val label = recurrenceLabel(rule)
    return when {
        label.day != null -> stringResource(
            label.stringId,
            label.day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
        )
        label.interval != null -> stringResource(label.stringId, label.interval)
        else -> stringResource(label.stringId)
    }
}

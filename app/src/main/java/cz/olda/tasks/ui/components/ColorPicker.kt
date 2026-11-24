package cz.olda.tasks.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
import cz.olda.tasks.ui.theme.ListColor
import cz.olda.tasks.ui.theme.ListPalette
import cz.olda.tasks.ui.theme.contrastRatio
import cz.olda.tasks.ui.theme.parseHex
import cz.olda.tasks.ui.theme.toHex

private val LightCard = Color(0xFFFFFFFF)
private val DarkCard = Color(0xFF1C1B1F)
private const val MinTextContrast = 4.5f

/**
 * Picks a list colour: the 12 presets plus a custom `#RRGGBB` field. [onPick] is called only with
 * a valid colour, as uppercase `#RRGGBB`; a badly typed hex shows an error and reports nothing.
 * A colour that would be hard to read as text gets a warning, never a block.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf(selected) }
    // A blank [selected] means "nothing chosen yet": no error until the person has typed.
    var edited by rememberSaveable { mutableStateOf(false) }
    val typed = parseHex(text)
    val shown = typed ?: parseHex(selected)
    // A preset has its own dark shade; a custom colour is shown as it is on both cards.
    val preset = shown?.let { color -> ListPalette.firstOrNull { it.light.toHex() == color.toHex() } }
    val onDark = preset?.dark ?: shown
    val showError = typed == null && (edited || text.isNotEmpty())
    val lowContrast = typed != null && (
        contrastRatio(typed, LightCard) < MinTextContrast ||
            (onDark != null && contrastRatio(onDark, DarkCard) < MinTextContrast)
        )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ListPalette.forEach { entry ->
                val hex = entry.light.toHex()
                Swatch(entry, selected = hex == shown?.toHex()) {
                    text = hex
                    onPick(hex)
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                edited = true
                parseHex(input)?.let { onPick(it.toHex()) }
            },
            label = { Text(stringResource(R.string.color_picker_custom)) },
            placeholder = { Text(stringResource(R.string.color_picker_hex_hint)) },
            isError = showError,
            supportingText = if (showError) {
                { Text(stringResource(R.string.color_picker_hex_error), modifier = Modifier.testTag("hex-error")) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hex-field"),
        )
        if (shown != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PreviewCard(R.string.color_picker_preview_light, shown, LightCard, Color.Black, Modifier.weight(1f))
                PreviewCard(R.string.color_picker_preview_dark, onDark ?: shown, DarkCard, Color.White, Modifier.weight(1f))
            }
        }
        if (lowContrast) {
            Text(
                text = stringResource(R.string.color_picker_low_contrast),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("contrast-warning"),
            )
        }
    }
}

@Composable
private fun Swatch(entry: ListColor, selected: Boolean, onClick: () -> Unit) {
    val name = stringResource(colorNameRes(entry.name))
    val ring = if (selected) {
        Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(entry.light)
            .then(ring)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name }
            .testTag("swatch-${entry.light.toHex()}"),
    )
}

@Composable
private fun PreviewCard(@StringRes label: Int, colour: Color, card: Color, plain: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = plain)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(colour))
            Text(stringResource(R.string.color_picker_preview_sample), style = MaterialTheme.typography.bodyMedium, color = colour)
        }
    }
}

@StringRes
private fun colorNameRes(name: String): Int = when (name) {
    "Blue" -> R.string.color_blue
    "Orange" -> R.string.color_orange
    "Green" -> R.string.color_green
    "Purple" -> R.string.color_purple
    "Teal" -> R.string.color_teal
    "Red" -> R.string.color_red
    "Pink" -> R.string.color_pink
    "Amber" -> R.string.color_amber
    "Olive" -> R.string.color_olive
    "Brown" -> R.string.color_brown
    "Slate" -> R.string.color_slate
    else -> R.string.color_cyan
}

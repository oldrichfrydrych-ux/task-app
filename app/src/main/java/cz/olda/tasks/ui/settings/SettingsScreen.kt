package cz.olda.tasks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
import cz.olda.tasks.ui.components.ColorPicker
import cz.olda.tasks.ui.theme.ListPalette
import cz.olda.tasks.ui.theme.toHex

/** Settings. In Phase 1 it holds the colour section only: a picker to try colours in. Nothing is stored yet. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var colour by rememberSaveable { mutableStateOf(ListPalette.first().light.toHex()) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("screen-Settings"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_colours), style = MaterialTheme.typography.titleMedium)
        ColorPicker(selected = colour, onPick = { colour = it })
    }
}

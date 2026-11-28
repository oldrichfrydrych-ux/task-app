package cz.olda.tasks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.olda.tasks.R
import cz.olda.tasks.platform.openNotificationSettings
import cz.olda.tasks.ui.components.ColorPicker
import cz.olda.tasks.ui.theme.ListPalette
import cz.olda.tasks.ui.theme.toHex

/**
 * Settings: the colour section Phase 1 left here, plus — only when it is true — a line saying
 * Android is not letting reminders through.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // He may have changed Android's own switch while the app was away.
    LaunchedEffect(Unit) { viewModel.refresh() }
    SettingsContent(
        state = state,
        onOpenSystemSettings = { openNotificationSettings(context) },
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsState,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var colour by rememberSaveable { mutableStateOf(ListPalette.first().light.toHex()) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("screen-Settings"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.remindersBlocked) RemindersBlocked(onOpenSystemSettings)

        Text(stringResource(R.string.settings_colours), style = MaterialTheme.typography.titleMedium)
        ColorPicker(selected = colour, onPick = { colour = it })
    }
}

@Composable
private fun RemindersBlocked(onOpenSystemSettings: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag("settings-reminders-blocked"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.reminders_blocked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = onOpenSystemSettings) {
                Text(stringResource(R.string.open_notification_settings))
            }
        }
    }
}

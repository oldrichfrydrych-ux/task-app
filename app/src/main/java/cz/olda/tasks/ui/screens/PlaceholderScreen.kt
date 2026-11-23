package cz.olda.tasks.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import cz.olda.tasks.R
import cz.olda.tasks.ui.Destination

/** Stands in for a top-level screen until Phase 1 fills it in. */
@Composable
fun PlaceholderScreen(destination: Destination, modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(destination.label),
        tag = "screen-${destination.name}",
        modifier = modifier,
    )
}

/** Stands in for any screen that has no real content yet. */
@Composable
fun PlaceholderScreen(title: String, tag: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.placeholder_screen, title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

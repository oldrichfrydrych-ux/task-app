package cz.olda.tasks.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cz.olda.tasks.ui.theme.AppMotion
import cz.olda.tasks.ui.theme.LocalAnimationsEnabled

/**
 * "Nothing due today" and its siblings.
 *
 * It fades in rather than appearing, so completing the last task of a screen does not snap
 * the message into place the instant the list empties. It is a later sibling of the list in
 * every screen that uses it, so it is never drawn over a row that is still folding - it sits
 * below and slides up as the row collapses. The fade is not slower than the fold, though: at
 * about 230ms it is readable while the row still has a couple of hundred milliseconds of
 * collapsing left. The caller keeps passing its own test tag.
 */
@Composable
fun EmptyStateText(text: String, modifier: Modifier = Modifier) {
    val animate = LocalAnimationsEnabled.current
    val visible = remember { MutableTransitionState(!animate) }
    LaunchedEffect(Unit) { visible.targetState = true }

    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(AppMotion.slowEffects()),
        exit = fadeOut(AppMotion.effects()),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

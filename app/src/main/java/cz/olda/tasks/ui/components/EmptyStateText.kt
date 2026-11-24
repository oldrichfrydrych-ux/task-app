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
 * It fades rather than appears, and it fades slowly - slower than a row takes to fold - so
 * that completing the last task of a screen cannot put "Nothing due today" on top of the row
 * still folding away underneath it. The caller keeps passing its own test tag.
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

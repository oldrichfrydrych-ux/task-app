package cz.olda.tasks.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.olda.tasks.R
import cz.olda.tasks.ui.AppIcons

/**
 * The one header every screen uses: the title (null while it has not loaded, so nothing flashes) at one scale, an optional back arrow in front and
 * an optional action behind. It pads itself (16dp from the edge, or 4dp before a back arrow so the
 * arrow's own touch padding lines the title up), so a screen puts it above its content and
 * adds no padding of its own around it. Keep destructive actions out of [trailing].
 */
@Composable
fun ScreenHeader(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = backModifier) {
                Icon(AppIcons.Back, contentDescription = stringResource(R.string.back))
            }
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        trailing?.invoke(this)
    }
}

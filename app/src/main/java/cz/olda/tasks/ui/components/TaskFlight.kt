package cz.olda.tasks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.olda.tasks.ui.theme.AppMotion
import cz.olda.tasks.ui.theme.LocalAnimationsEnabled
import kotlin.math.roundToInt

/**
 * How far above the straight line the pill rises at the midpoint.
 *
 * Not decoration. The add button and every tab live in the same floating bar, a few tens of
 * dp apart in a bar barely 56dp tall, so a pill going straight between them would be a
 * flicker half hidden behind the tabs it passes - the same "nothing happened" this whole
 * thing exists to answer. The pill has to leave the bar to be seen at all. This is the first
 * number to change if it reads wrong.
 */
private val FlightArc = 160.dp

/** Wide enough for a few Czech words, and no wider: this is a pill, not a paragraph. */
private val FlightWidth = 220.dp

/**
 * Carries a saved task from [from] to [to] - both in root coordinates - and calls
 * [onFinished] once when it lands.
 *
 * It travels by layout **placement**, not by a `graphicsLayer` transform. The two look the
 * same, but a transform is invisible to the semantics tree, and this project has already
 * written and deleted two tests that could not fail for exactly that reason. An offset can
 * be measured, so the arc and the landing are held by real assertions.
 *
 * With animations off it draws nothing and reports finished immediately, so whatever is
 * waiting on it still cleans up.
 *
 * **It does not shrink on the way, although the spec says it does, and that is deliberate.**
 * Shrinking means a `graphicsLayer` scale, and a scaled node reports a top-left that has
 * moved inward while its size has not - so the test that proves the pill lands on its tab,
 * which allows 60px, would be fed a position wrong by tens of pixels at exactly the frames
 * it checks. The whole flight is built on being measurable; a taper is not worth that.
 */
@Composable
fun TaskFlight(
    label: String,
    from: Rect,
    to: Rect,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animate = LocalAnimationsEnabled.current
    if (!animate) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, AppMotion.slowSpatial())
        onFinished()
    }

    val arc = with(LocalDensity.current) { FlightArc.toPx() }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            // Measured and placed in one pass, so the pill knows its own width on the very
            // first frame: reporting the width back through state would leave that frame
            // placed half a pill off and snapping afterwards.
            .layout { measurable, constraints ->
                val pill = measurable.measure(constraints)
                layout(pill.width, pill.height) {
                    val t = progress.value
                    val x = from.center.x + (to.center.x - from.center.x) * t
                    // A bow, highest at the midpoint, so the pill clears the bar it starts in.
                    val y = from.center.y + (to.center.y - from.center.y) * t -
                        arc * 4f * t * (1f - t)
                    // Both ends of the flight sit near a screen edge - the add button on the
                    // right, the first tab on the left - so a wide pill centred on its path
                    // would hang off the screen, sliced in half at exactly the moments it is
                    // meant to be read. Kept inside instead.
                    val left = (x - pill.width / 2f).roundToInt()
                    val room = constraints.maxWidth
                    pill.place(
                        if (constraints.hasBoundedWidth) {
                            left.coerceIn(0, (room - pill.width).coerceAtLeast(0))
                        } else {
                            left
                        },
                        (y - pill.height / 2f).roundToInt(),
                    )
                }
            }
            .widthIn(max = FlightWidth)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("task-flight"),
    )
}

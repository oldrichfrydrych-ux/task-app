package cz.olda.tasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Every tab icon is this size, parts included, so the parts line up with each other. */
private val IconSize = 20.dp

/** Where a thing dropped into the inbox comes to rest: the tray's mouth, measured from the top. */
private val TrayMouth = 11.dp

/**
 * A tab's icon, at [progress] through its tap gesture.
 *
 * Three of the four are drawn from parts rather than as one shape, because that is the whole
 * difference between an icon that answers a tap and one that merely wobbles: the calendar flicks
 * its page out and pulls the next one in, the inbox takes something that falls into it, and a
 * wave runs through the Lists tiles. The clock is the exception - it turns as a whole, winding
 * back first, which is exactly what a clock does.
 *
 * At rest every one of them draws what it always drew.
 */
@Composable
fun TabIcon(
    destination: Destination,
    progress: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        Destination.Today -> CalendarIcon(progress, tint, modifier)
        Destination.Upcoming -> ClockIcon(progress, tint, modifier)
        Destination.Inbox -> InboxIcon(progress, tint, modifier)
        Destination.Lists -> ListsIcon(progress, tint, modifier)
    }
}

/** Winds back against the turn, then sweeps the whole way round. */
@Composable
private fun ClockIcon(progress: Float, tint: Color, modifier: Modifier) {
    val turn = tabIconTransform(Destination.Upcoming, progress)
    Part(AppIcons.Upcoming, tint, modifier.graphicsLayer { rotationZ = turn.rotationZ })
}

/** The dots are flicked out to the left, the next page's slide in, and the frame rocks after them. */
@Composable
private fun CalendarIcon(progress: Float, tint: Color, modifier: Modifier) {
    val page = calendarPageTurn(progress)
    Box(modifier = modifier.size(IconSize).clipToBounds(), contentAlignment = Alignment.Center) {
        Part(
            AppIcons.CalendarFrame,
            tint,
            Modifier.graphicsLayer { translationX = page.frameNudgeDp.dp.toPx() },
        )
        Part(
            AppIcons.CalendarDots,
            tint,
            Modifier.graphicsLayer {
                translationX = page.leavingX.dp.toPx()
                alpha = page.leavingAlpha
                scaleX = page.leavingScale
                scaleY = page.leavingScale
            },
        )
        Part(
            AppIcons.CalendarDots,
            tint,
            Modifier.graphicsLayer {
                translationX = page.arrivingX.dp.toPx()
                alpha = page.arrivingAlpha
            },
        )
    }
}

/** Something falls in from above; the tray squashes, gives way, rebounds and settles. */
@Composable
private fun InboxIcon(progress: Float, tint: Color, modifier: Modifier) {
    val drop = trayDrop(progress)
    Box(modifier = modifier.size(IconSize).clipToBounds(), contentAlignment = Alignment.TopCenter) {
        Part(
            AppIcons.Inbox,
            tint,
            Modifier.graphicsLayer {
                translationY = drop.dipDp.dp.toPx()
                scaleY = drop.trayScaleY
                // From the bottom, so the tray settles onto its own floor rather than shrinking
                // towards the middle of the bar.
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
        )
        if (drop.itemAlpha > 0f) {
            Box(
                modifier = Modifier
                    .offset(y = TrayMouth + drop.itemY.dp)
                    .size(width = 8.dp, height = 2.5.dp)
                    .graphicsLayer { alpha = drop.itemAlpha }
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

/**
 * Four tiles with a wave running through them, top-left to bottom-right.
 *
 * Drawn as four boxes rather than as [AppIcons.Lists] in one piece, because each tile has to be
 * punched in on its own. The numbers are that icon's own geometry, scaled from its 24-unit
 * viewport to [IconSize]: tiles of 7 units at 4 and 13, which is why they are not round numbers.
 */
@Composable
private fun ListsIcon(progress: Float, tint: Color, modifier: Modifier) {
    val unit = IconSize / 24f
    val tileSize = unit * 7f
    val near = unit * 4f
    val far = unit * 13f
    val places = listOf(near to near, far to near, near to far, far to far)
    Box(modifier = modifier.size(IconSize), contentAlignment = Alignment.TopStart) {
        places.forEachIndexed { index, (x, y) ->
            val pop = gridTilePop(progress, index)
            Box(
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(tileSize)
                    .graphicsLayer { scaleX = pop; scaleY = pop }
                    .clip(RoundedCornerShape(unit * 2f))
                    .background(tint),
            )
        }
    }
}

/** One piece of an icon, drawn at the icon's own size so every piece lands where it belongs. */
@Composable
private fun Part(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(IconSize))
}

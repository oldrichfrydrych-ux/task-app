package cz.olda.tasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
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
 * difference between an icon that answers a tap and one that merely wobbles: the calendar turns
 * its page while its frame stays put, the inbox takes something that falls into it, and the
 * list's rows flick one after another. The clock is the exception - it turns as a whole, which
 * is exactly what a clock does.
 *
 * At rest every one of them draws what it always drew: the parts are the same paths as the whole
 * icons, split (see [AppIcons.CalendarFrame] and friends).
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

/** The face sweeps a whole turn and ends where it started. */
@Composable
private fun ClockIcon(progress: Float, tint: Color, modifier: Modifier) {
    val turn = tabIconTransform(Destination.Upcoming, progress)
    Part(AppIcons.Upcoming, tint, modifier.graphicsLayer { rotationZ = turn.rotationZ })
}

/** The dots slide out to the left and the next page's slide in behind them; the frame stays. */
@Composable
private fun CalendarIcon(progress: Float, tint: Color, modifier: Modifier) {
    val page = calendarPageTurn(progress)
    Box(modifier = modifier.size(IconSize).clipToBounds(), contentAlignment = Alignment.Center) {
        Part(AppIcons.CalendarFrame, tint)
        Part(
            AppIcons.CalendarDots,
            tint,
            Modifier.offset(x = page.leavingX.dp).alpha(page.leavingAlpha),
        )
        Part(
            AppIcons.CalendarDots,
            tint,
            Modifier.offset(x = page.arrivingX.dp).alpha(page.arrivingAlpha),
        )
    }
}

/** Something falls in from above, and the tray gives under it and settles. */
@Composable
private fun InboxIcon(progress: Float, tint: Color, modifier: Modifier) {
    val drop = trayDrop(progress)
    Box(modifier = modifier.size(IconSize).clipToBounds(), contentAlignment = Alignment.TopCenter) {
        Part(AppIcons.Inbox, tint, Modifier.offset(y = drop.dipDp.dp))
        if (drop.itemAlpha > 0f) {
            Box(
                modifier = Modifier
                    .offset(y = TrayMouth + drop.itemY.dp)
                    .size(width = 8.dp, height = 2.5.dp)
                    .alpha(drop.itemAlpha)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

/** Each row flicks out to the left and back, top one first, the way a list is read. */
@Composable
private fun ListsIcon(progress: Float, tint: Color, modifier: Modifier) {
    val rows = listOf(AppIcons.ListRowTop, AppIcons.ListRowMiddle, AppIcons.ListRowBottom)
    Box(modifier = modifier.size(IconSize).clipToBounds(), contentAlignment = Alignment.Center) {
        rows.forEachIndexed { index, row ->
            Part(row, tint, Modifier.offset(x = listRowSlide(progress, index).dp))
        }
    }
}

/** One piece of an icon, drawn at the icon's own size so every piece lands where it belongs. */
@Composable
private fun Part(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(IconSize))
}

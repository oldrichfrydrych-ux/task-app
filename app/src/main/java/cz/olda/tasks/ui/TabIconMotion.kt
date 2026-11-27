package cz.olda.tasks.ui

import androidx.compose.animation.core.SpringSpec
import cz.olda.tasks.ui.theme.AppMotion
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * How a whole icon stands at one moment of its tap gesture: a plain description, so the shape of
 * the movement can be tested without a screen.
 *
 * Only the clock uses it. The other three move a *part* of themselves, which is the difference
 * between an icon that answers a tap and an icon that merely wobbles - see [calendarPageTurn],
 * [trayDrop] and [listRowSlide].
 */
data class IconTransform(
    val rotationZ: Float = 0f,
    val translationYDp: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

/**
 * What the icon as a whole does when its tab is tapped, at [progress] through the gesture.
 *
 * Material 3 Expressive asks for spring physics and for things that answer a touch; it hands out
 * no gesture per icon, so these are ours. The rule they follow is that the movement has to come
 * from what the icon draws - a clock turns, because that is what a clock does. A tilt or a bounce
 * would fit any icon at all, which is the same as fitting none.
 *
 * Every curve is written so that **0 and 1 are both rest**. A spring does not stop dead on its
 * target, it crosses it and settles back, so [progress] arrives here slightly above 1 as well.
 */
fun tabIconTransform(destination: Destination, progress: Float): IconTransform = when (destination) {
    // The face sweeps a whole turn and ends where it started.
    Destination.Upcoming -> IconTransform(rotationZ = 360f * progress.coerceIn(0f, 1f))
    // The rest keep still as a whole; their parts do the moving.
    else -> IconTransform()
}

/** How far a page slides as it is turned, and how far the next one comes from. */
private const val PageTravelDp = 9f

/** Where the calendar's two pages are mid-turn: the one leaving, and the one coming after it. */
data class PageTurn(
    val leavingX: Float,
    val leavingAlpha: Float,
    val arrivingX: Float,
    val arrivingAlpha: Float,
)

/**
 * The calendar turning a page: the day's dots slide out to the left while the next page's slide
 * in from the right. The frame stays put, as a calendar's frame does.
 *
 * The two halves overlap, so the frame is never empty in the middle of the turn.
 */
fun calendarPageTurn(progress: Float): PageTurn {
    val p = progress.coerceIn(0f, 1f)
    val leaving = (p / 0.6f).coerceIn(0f, 1f)
    val arriving = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
    return PageTurn(
        leavingX = -PageTravelDp * leaving,
        leavingAlpha = 1f - leaving,
        arrivingX = PageTravelDp * (1f - arriving),
        arrivingAlpha = arriving,
    )
}

/** How far above the tray the thing starts, and how far the tray gives way as it lands. */
private const val ItemFallDp = 9f
private const val TrayDipDp = 2.5f

/** Where the thing landing in the tray is, and how far the tray has given way under it. */
data class TrayDrop(val itemY: Float, val itemAlpha: Float, val dipDp: Float)

/**
 * Something dropping into the inbox: a small bar falls in from above, and the tray gives under it
 * and settles. Nothing hangs over the tray at rest - the item only exists while it is falling.
 */
fun trayDrop(progress: Float): TrayDrop {
    val p = progress.coerceIn(0f, 1f)
    val falling = (p / 0.6f).coerceIn(0f, 1f)
    val landed = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)
    return TrayDrop(
        itemY = -ItemFallDp * (1f - falling),
        // Fades in over the first moment so it does not appear from nowhere, and is gone by the
        // time the tray has taken it.
        itemAlpha = min(1f, p / 0.1f) * (1f - landed),
        dipDp = TrayDipDp * sin(landed * PI).toFloat(),
    )
}

/** How far a row swings out, and how much later each row goes than the one above it. */
private const val RowTravelDp = 7f
private const val RowStagger = 0.18f

/**
 * The list's rows flicking out to the left and back, one after another from the top - the way a
 * list is read. [row] is 0 at the top. Returned in dp, negative being out to the left.
 */
fun listRowSlide(progress: Float, row: Int): Float {
    val p = progress.coerceIn(0f, 1f)
    val span = 1f - RowStagger * 2f
    val own = ((p - RowStagger * row) / span).coerceIn(0f, 1f)
    return -RowTravelDp * sin(own * PI).toFloat()
}

/**
 * The spring each gesture runs on. The clock and the calendar get the slow one - a whole turn and
 * a page changing both need room to be read - while the tray and the rows are quick, because they
 * are answering a finger that has already moved on.
 */
fun tabIconSpring(destination: Destination): SpringSpec<Float> = when (destination) {
    Destination.Upcoming, Destination.Today -> AppMotion.slowSpatial()
    else -> AppMotion.fastSpatial()
}

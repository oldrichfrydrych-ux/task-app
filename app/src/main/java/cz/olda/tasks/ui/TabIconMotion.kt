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
 * [trayDrop] and [gridTilePop].
 */
data class IconTransform(
    val rotationZ: Float = 0f,
    val translationYDp: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

/** How far the clock winds back before it spins, which is where the gesture gets its swagger. */
private const val ClockWindUpDeg = 55f

/**
 * What the icon as a whole does when its tab is tapped, at [progress] through the gesture.
 *
 * Material 3 Expressive asks for spring physics and for things that answer a touch; it hands out
 * no gesture per icon, so these are ours. The rule they follow is that the movement has to come
 * from what the icon draws - a clock turns, because that is what a clock does - and that it is
 * worth watching: a wind-up before the move, and a recoil after it, are what separate a gesture
 * with some life in it from a thing sliding politely from A to B.
 *
 * Every curve is written so that **0 and 1 are both rest**. A spring does not stop dead on its
 * target, it crosses it and settles back, so [progress] arrives here slightly above 1 as well.
 */
fun tabIconTransform(destination: Destination, progress: Float): IconTransform {
    val p = progress.coerceIn(0f, 1f)
    return when (destination) {
        // Winds back against the turn, then sweeps the whole way round and ends where it began.
        Destination.Upcoming -> IconTransform(
            rotationZ = 360f * p - ClockWindUpDeg * sin(min(p / 0.3f, 1f) * PI).toFloat(),
        )
        // The rest keep still as a whole; their parts do the moving.
        else -> IconTransform()
    }
}

/** How far a page slides as it is turned, and how far the next one comes from. */
private const val PageTravelDp = 12f

/** How far the frame rocks as the page goes through it. */
private const val FrameRockDp = 1.6f

/** Where the calendar's two pages are mid-turn, and what the frame around them is doing. */
data class PageTurn(
    val leavingX: Float,
    val leavingAlpha: Float,
    val leavingScale: Float,
    val arrivingX: Float,
    val arrivingAlpha: Float,
    val frameNudgeDp: Float,
)

/**
 * The calendar turning a page: the day's dots are flicked out to the left, shrinking away as
 * they go, while the next page's slide in from the right - and the frame rocks after them, the
 * way a thing does when something is pulled out of it.
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
        leavingScale = 1f - 0.25f * leaving,
        arrivingX = PageTravelDp * (1f - arriving),
        arrivingAlpha = arriving,
        // One rock out and back, against the page's travel.
        frameNudgeDp = FrameRockDp * sin(p * 2f * PI).toFloat(),
    )
}

/** How far above the tray the thing starts, how far the tray gives way, and how much it squashes. */
private const val ItemFallDp = 11f
private const val TrayDipDp = 3.5f
private const val TraySquash = 0.14f

/** Where the thing landing in the tray is, and what the tray is doing under it. */
data class TrayDrop(
    val itemY: Float,
    val itemAlpha: Float,
    val dipDp: Float,
    val trayScaleY: Float,
)

/**
 * Something dropping into the inbox: a bar falls in from above - gathering speed, as a falling
 * thing does - and the tray takes the weight, squashes, gives way and rebounds past its own
 * level before settling. Nothing hangs over the tray at rest: the item only exists while it
 * is falling.
 */
fun trayDrop(progress: Float): TrayDrop {
    val p = progress.coerceIn(0f, 1f)
    val falling = (p / 0.5f).coerceIn(0f, 1f)
    val landed = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
    return TrayDrop(
        // Squared, so it falls slowly at first and arrives fast.
        itemY = -ItemFallDp * (1f - falling * falling),
        itemAlpha = min(1f, p / 0.08f) * (1f - landed),
        // Down, back up past the line, and settle: a bounce rather than a dip.
        dipDp = TrayDipDp * sin(landed * 2f * PI).toFloat() * (1f - landed),
        trayScaleY = 1f - TraySquash * sin(landed * PI).toFloat(),
    )
}

/** How small a tile gets as the wave goes through it, and how far apart the four are set off. */
private const val TileDip = 0.45f
private const val TileStagger = 0.16f

/**
 * The wave that runs through the four tiles of the Lists icon when it is tapped: each one is
 * punched in and springs back, a moment after the one before it, reading top-left to
 * bottom-right. [tile] is 0 for the top-left and 3 for the bottom-right; the result is its scale.
 */
fun gridTilePop(progress: Float, tile: Int): Float {
    val p = progress.coerceIn(0f, 1f)
    val span = 1f - TileStagger * 3f
    val own = ((p - TileStagger * tile) / span).coerceIn(0f, 1f)
    return 1f - TileDip * sin(own * PI).toFloat()
}

/**
 * The spring each gesture runs on. The clock and the calendar get the slow one - a whole turn and
 * a page changing both need room to be read - while the tray and the tiles are quick, because
 * they are answering a finger that has already moved on.
 */
fun tabIconSpring(destination: Destination): SpringSpec<Float> = when (destination) {
    Destination.Upcoming, Destination.Today -> AppMotion.slowSpatial()
    else -> AppMotion.fastSpatial()
}

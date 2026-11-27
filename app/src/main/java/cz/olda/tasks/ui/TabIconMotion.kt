package cz.olda.tasks.ui

import androidx.compose.animation.core.SpringSpec
import cz.olda.tasks.ui.theme.AppMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * How a tab's icon stands at one moment of its tap gesture: a plain description, so the shape of
 * the movement can be tested without a screen.
 */
data class IconTransform(
    val rotationX: Float = 0f,
    val rotationZ: Float = 0f,
    val translationYDp: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

/** How far the calendar leans over as its page turns. */
private const val PageTurnDegrees = -34f

/** How deep the tray dips as something lands in it. */
private const val TrayDipDp = 5f

/** How much the list rows squash together before they spring apart. */
private const val RowSquash = 0.2f

/**
 * What each icon does when its tab is tapped, at [progress] through the gesture.
 *
 * Material 3 Expressive asks for spring physics and for interface elements that answer a touch;
 * it does not hand out a gesture per icon, so these four are ours. Each one is the thing the icon
 * draws, doing what that thing does: the clock's face sweeps a whole turn, the tray dips as
 * something lands in it, the calendar turns a page, and the list's rows squash together and
 * spring apart.
 *
 * Every curve is written so that **0 and 1 are both rest**. A spring does not stop dead on its
 * target, it crosses it and settles back, so [progress] arrives here slightly above 1 as well -
 * which is where the gesture gets its last little wobble, and why nothing may be left leaning
 * when it is over.
 */
fun tabIconTransform(destination: Destination, progress: Float): IconTransform {
    // Out and back: zero at both ends of the gesture, full at its middle.
    val swing = sin(progress.coerceIn(0f, 1f) * PI).toFloat()
    return when (destination) {
        Destination.Today -> IconTransform(rotationX = PageTurnDegrees * swing)
        Destination.Upcoming -> IconTransform(rotationZ = 360f * progress.coerceIn(0f, 1f))
        Destination.Inbox -> IconTransform(translationYDp = TrayDipDp * swing)
        Destination.Lists -> IconTransform(
            scaleX = 1f + RowSquash / 2f * swing,
            scaleY = 1f - RowSquash * swing,
        )
    }
}

/**
 * The spring each gesture runs on. The clock gets the slow one because a whole turn needs room to
 * read as a sweep rather than a flicker; the rest are quick and springy, since they are answering
 * a finger that has already moved on.
 */
fun tabIconSpring(destination: Destination): SpringSpec<Float> = when (destination) {
    Destination.Upcoming -> AppMotion.slowSpatial()
    else -> AppMotion.fastSpatial()
}

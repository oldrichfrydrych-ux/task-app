package cz.olda.tasks.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The handful of icons this app needs, drawn here rather than pulled from an icon
 * library, so the build keeps its short dependency list. Paths are filled 24x24
 * shapes; `Icon` tints them with the current content colour.
 */
object AppIcons {

    /** Calendar: the Today tab. */
    val Today: ImageVector = filledIcon(
        name = "Today",
        pathData = "M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5" +
            "c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2z" +
            "m0 16H5V9h14v11z",
    )

    /** Clock: the Upcoming tab. */
    val Upcoming: ImageVector = filledIcon(
        name = "Upcoming",
        pathData = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12" +
            "S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z" +
            "m.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
    )

    /** Tray: the Inbox tab. */
    val Inbox: ImageVector = filledIcon(
        name = "Inbox",
        pathData = "M19 3H4.99c-1.11 0-1.98.89-1.98 2L3 19c0 1.1.88 2 1.99 2H19c1.1 0 2-.9 2-2" +
            "V5c0-1.1-.9-2-2-2zm0 12h-4c0 1.66-1.35 3-3 3s-3-1.34-3-3H4.99V5H19v10z",
    )

    /** Bulleted rows: the Lists tab. */
    val Lists: ImageVector = filledIcon(
        name = "Lists",
        pathData = "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2z" +
            "M7 7v2h14V7H7z",
    )

    /** Plus: the add button. */
    val Add: ImageVector = filledIcon(
        name = "Add",
        pathData = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
    )
}

private fun filledIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
    }.build()

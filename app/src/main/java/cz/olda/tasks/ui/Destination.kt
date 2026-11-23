package cz.olda.tasks.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import cz.olda.tasks.R

/** The four top-level places in the app, in bottom-bar order. */
enum class Destination(@param:StringRes val label: Int, val icon: ImageVector) {
    Today(R.string.today, AppIcons.Today),
    Upcoming(R.string.upcoming, AppIcons.Upcoming),
    Inbox(R.string.inbox, AppIcons.Inbox),
    Lists(R.string.lists, AppIcons.Lists),
    ;

    /** The navigation route of this destination. */
    val route: String get() = name

    companion object {
        /**
         * The destination saved under [name], or [Today] if no destination has that
         * name any more. Saved state can outlive the build that wrote it.
         */
        fun ofName(name: String): Destination = entries.firstOrNull { it.name == name } ?: Today
    }
}

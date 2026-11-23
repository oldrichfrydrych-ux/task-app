package cz.olda.tasks.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import cz.olda.tasks.R

/** The four top-level places in the app, in bottom-bar order. */
enum class Destination(@StringRes val label: Int, val icon: ImageVector) {
    Today(R.string.today, AppIcons.Today),
    Upcoming(R.string.upcoming, AppIcons.Upcoming),
    Inbox(R.string.inbox, AppIcons.Inbox),
    Lists(R.string.lists, AppIcons.Lists),
}

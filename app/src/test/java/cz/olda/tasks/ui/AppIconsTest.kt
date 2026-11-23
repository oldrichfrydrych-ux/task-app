package cz.olda.tasks.ui

import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The icons are hand-written path data, so a typo would only show up as an empty or
 * broken shape on the phone. Building them here catches it on the computer instead.
 */
class AppIconsTest {

    private val icons = mapOf(
        "Today" to AppIcons.Today,
        "Upcoming" to AppIcons.Upcoming,
        "Inbox" to AppIcons.Inbox,
        "Lists" to AppIcons.Lists,
        "Add" to AppIcons.Add,
    )

    @Test
    fun everyIconParsesIntoAPath() {
        icons.forEach { (name, icon) ->
            val path = icon.root.firstOrNull() as? VectorPath
            assertTrue("$name has no path", path != null)
            assertTrue("$name has empty path data", path!!.pathData.size > 4)
        }
    }

    @Test
    fun everyDestinationHasItsOwnIcon() {
        val distinct = Destination.entries.map { it.icon }.toSet()
        assertTrue("two tabs share an icon", distinct.size == Destination.entries.size)
    }
}

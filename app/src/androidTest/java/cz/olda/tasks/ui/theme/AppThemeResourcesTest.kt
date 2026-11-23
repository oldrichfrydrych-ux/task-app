package cz.olda.tasks.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import cz.olda.tasks.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The window background is what the phone paints before Compose draws its first frame.
 * If it is not the app's own background, every cold start flashes the wrong colour -
 * a bright white one on a phone in dark mode.
 *
 * Both modes are asked for by name, so the test checks the colour Olda actually sees
 * rather than whichever mode the phone happens to be in while the tests run.
 */
class AppThemeResourcesTest {

    @Test
    fun windowBackgroundIsTheAppBackgroundInLightMode() {
        assertEquals(0xFFFAF7F3.toInt(), windowBackgroundIn(Configuration.UI_MODE_NIGHT_NO))
    }

    @Test
    fun windowBackgroundIsTheAppBackgroundInDarkMode() {
        assertEquals(0xFF1A1714.toInt(), windowBackgroundIn(Configuration.UI_MODE_NIGHT_YES))
    }

    private fun windowBackgroundIn(nightMode: Int): Int {
        val base: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val themed = ContextThemeWrapper(
            base.createConfigurationContext(configuration),
            R.style.Theme_Tasks,
        )
        val attributes = themed.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        return try {
            attributes.getColor(0, 0)
        } finally {
            attributes.recycle()
        }
    }
}

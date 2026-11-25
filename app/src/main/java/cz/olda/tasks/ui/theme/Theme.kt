package cz.olda.tasks.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal val LightColorScheme = lightColorScheme(
    primary = AddButton,
    onPrimary = OnAddButton,
    secondary = NowLineLight,
    onSecondary = OnAddButton,
    tertiary = NowLineTextLight,
    onTertiary = OnAddButton,
    background = WarmBackgroundLight,
    onBackground = WarmTextLight,
    surface = WarmSurfaceLight,
    onSurface = WarmTextLight,
    surfaceVariant = WarmBackgroundLight,
    onSurfaceVariant = WarmSecondaryTextLight,
    outline = WarmSecondaryTextLight,
    outlineVariant = WarmLinesLight,
)

internal val DarkColorScheme = darkColorScheme(
    primary = AddButton,
    onPrimary = OnAddButton,
    secondary = NowLineDark,
    onSecondary = WarmBackgroundDark,
    tertiary = NowLineTextDark,
    onTertiary = WarmBackgroundDark,
    background = WarmBackgroundDark,
    onBackground = WarmTextDark,
    surface = WarmSurfaceDark,
    onSurface = WarmTextDark,
    surfaceVariant = WarmBackgroundDark,
    onSurfaceVariant = WarmSecondaryTextDark,
    outline = WarmSecondaryTextDark,
    outlineVariant = WarmLinesDark,
)

/**
 * The app has its own palette. Dynamic colour (Material You wallpaper colours) stays
 * off, so the chosen look is the same on every phone.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    animationsEnabled: Boolean = systemAnimationsEnabled(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBottomBarColors provides if (darkTheme) DarkBottomBarColors else LightBottomBarColors,
        LocalAnimationsEnabled provides animationsEnabled,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/**
 * Whether the phone allows animations: false when Accessibility -> Remove animations has set
 * the animator duration scale to 0.
 *
 * Read once rather than observed: it is a setting people set and leave, and a restart picks
 * up a change. It is a default of [AppTheme] rather than something the theme imposes, so a
 * caller - a test, or a screen with its own reason - can still say no.
 */
@Composable
fun systemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        animationsEnabled(
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ),
        )
    }
}

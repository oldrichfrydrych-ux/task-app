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
    content: @Composable () -> Unit,
) {
    // Read once: "Remove animations" is a setting people set and leave, and a restart picks up
    // a change. An observer would cost a listener on every composition for no real benefit.
    val context = LocalContext.current
    val animations = remember(context) {
        animationsEnabled(
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ),
        )
    }
    CompositionLocalProvider(
        LocalBottomBarColors provides if (darkTheme) DarkBottomBarColors else LightBottomBarColors,
        LocalAnimationsEnabled provides animations,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}

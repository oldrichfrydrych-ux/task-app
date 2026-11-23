package cz.olda.tasks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
    CompositionLocalProvider(
        LocalBottomBarColors provides if (darkTheme) DarkBottomBarColors else LightBottomBarColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}

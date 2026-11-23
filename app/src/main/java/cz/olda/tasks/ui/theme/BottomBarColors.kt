package cz.olda.tasks.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The floating bottom bar is a dark pill in both light and dark mode, so its colours
 * do not fit Material's light/dark roles. [AppTheme] hands them down here instead.
 */
data class BottomBarColors(
    val container: Color,
    val selectedPill: Color,
    val onSelected: Color,
    val onUnselected: Color,
    val addButton: Color,
    val onAddButton: Color,
)

internal val LightBottomBarColors = BottomBarColors(
    container = BottomBarLight,
    selectedPill = BottomBarSelectedLight,
    onSelected = WarmTextDark,
    onUnselected = WarmSecondaryTextDark,
    addButton = AddButton,
    onAddButton = OnAddButton,
)

internal val DarkBottomBarColors = BottomBarColors(
    container = BottomBarDark,
    selectedPill = BottomBarSelectedDark,
    onSelected = WarmTextDark,
    onUnselected = WarmSecondaryTextDark,
    addButton = AddButton,
    onAddButton = OnAddButton,
)

val LocalBottomBarColors = staticCompositionLocalOf { LightBottomBarColors }

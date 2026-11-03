package com.drengr.launcher.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drengr.launcher.core.domain.UserSettings
import kotlin.math.min

/** Android's standard window-width breakpoints. */
enum class WidthClass { Compact, Medium, Expanded }

/**
 * Layout decisions for the launcher window's current size, in dp. Phones in portrait get exactly
 * the original layout; everything here only kicks in on wider windows. Based on the window (not
 * the screen), so split screen and DeX get the layout that fits the space they actually have.
 */
@Immutable
data class LauncherLayout(val widthDp: Int, val heightDp: Int) {

    val widthClass: WidthClass = when {
        widthDp < MEDIUM_WIDTH -> WidthClass.Compact
        widthDp < EXPANDED_WIDTH -> WidthClass.Medium
        else -> WidthClass.Expanded
    }

    /**
     * Tablet-sized window, judged by its shorter side so rotating a phone doesn't make it a
     * "tablet" (and doesn't change how many apps the dock shows).
     */
    val isLargeScreen: Boolean get() = min(widthDp, heightDp) >= MEDIUM_WIDTH

    /** Too short for the phone Home column (phone in landscape). */
    val isShort: Boolean get() = heightDp < SHORT_HEIGHT

    /**
     * Home splits into two columns in wide, landscape-shaped windows (tablet or phone in
     * landscape). Tall windows, like a tablet in portrait, keep one column that fills the height.
     */
    val twoPaneHome: Boolean get() = widthClass != WidthClass.Compact && widthDp > heightDp

    /** Upcoming events on Home: more when the agenda has room to grow. */
    val homeAgendaLimit: Int get() = when {
        isShort -> 3
        twoPaneHome -> 6
        isLargeScreen -> 5
        else -> 3
    }

    val dockSlots: Int get() = if (isLargeScreen) UserSettings.MAX_DOCK_APPS else UserSettings.PHONE_DOCK_APPS

    /** The dock stays a compact bar on wide screens instead of spanning the whole window. */
    val dockMaxWidth: Dp get() = if (widthClass == WidthClass.Compact) Dp.Unspecified else DOCK_MAX_WIDTH

    /**
     * Grid columns for the app list. [userColumns] is the phone setting; wider areas add columns
     * so icons keep roughly the size they have on a phone (a little bigger on tablets).
     */
    fun appGridColumns(userColumns: Int, availableWidthDp: Int): Int {
        if (widthClass == WidthClass.Compact) return userColumns
        val cell = PHONE_CONTENT_WIDTH / userColumns * if (isLargeScreen) TABLET_ICON_SCALE else 1f
        return (availableWidthDp / cell).toInt().coerceIn(userColumns, MAX_APP_COLUMNS)
    }

    /** List-view columns: one on phones, side-by-side rows once each can be ~320 dp wide. */
    fun appListColumns(availableWidthDp: Int): Int =
        if (widthClass == WidthClass.Compact) 1 else (availableWidthDp / LIST_ROW_WIDTH).coerceIn(1, MAX_LIST_COLUMNS)

    /** Favorites per row on Home for a pane [paneWidthDp] wide: 4 on phones, up to 8. */
    fun favoriteColumns(paneWidthDp: Int): Int =
        if (widthClass == WidthClass.Compact) PHONE_FAVORITE_COLUMNS
        else (paneWidthDp / FAVORITE_CELL_WIDTH).coerceIn(PHONE_FAVORITE_COLUMNS, MAX_FAVORITE_COLUMNS)

    companion object {
        const val MEDIUM_WIDTH = 600
        const val EXPANDED_WIDTH = 840
        const val SHORT_HEIGHT = 480
        const val PHONE_FAVORITE_COLUMNS = 4
        private const val MAX_FAVORITE_COLUMNS = 8
        private const val FAVORITE_CELL_WIDTH = 96
        private const val PHONE_CONTENT_WIDTH = 380f
        private const val TABLET_ICON_SCALE = 1.25f
        private const val MAX_APP_COLUMNS = 12
        private const val LIST_ROW_WIDTH = 320
        private const val MAX_LIST_COLUMNS = 3
        private val DOCK_MAX_WIDTH = 640.dp

        /** A typical phone in portrait. */
        val Phone = LauncherLayout(widthDp = 412, heightDp = 915)
    }
}

val LocalLauncherLayout = staticCompositionLocalOf { LauncherLayout.Phone }

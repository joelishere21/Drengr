package com.drengr.launcher.core.domain

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.Immutable
import com.drengr.launcher.core.designsystem.AccentColor

enum class PageId(val title: String) {
    Home("Home"),
    Applications("Applications"),
    Productivity("Productivity"),
    System("System"),
    Media("Media"),
}

enum class AppLayout { Grid, List }

enum class AppCategory(val label: String) {
    Communication("Communication"),
    Productivity("Productivity"),
    Finance("Finance"),
    Media("Media"),
    Photography("Photography"),
    Development("Development"),
    Utilities("Utilities"),
    Games("Games"),
}

/** A launchable activity. [key] is stable across launches and unique across user profiles. */
@Immutable
data class AppInfo(
    val key: String,
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
    val user: UserHandle,
    val category: AppCategory,
    val isWorkProfile: Boolean,
)

@Immutable
data class AppShortcut(
    val id: String,
    val label: String,
    val packageName: String,
    val user: UserHandle,
)

/** A place the user picked by name, for weather without location access. */
@Immutable
data class Place(val name: String, val latitude: Double, val longitude: Double)

@Immutable
data class UserSettings(
    val accent: AccentColor = AccentColor.Default,
    val favorites: List<String> = emptyList(),
    /** null until the user customizes the dock; the launcher then picks sensible defaults. */
    val dock: List<String>? = null,
    val pageOrder: List<PageId> = PageId.entries,
    val hiddenPages: Set<PageId> = emptySet(),
    val appLayout: AppLayout = AppLayout.Grid,
    val gridColumns: Int = 4,
    val lastPage: PageId = PageId.Home,
    /** Entries are "app:<key>" or "action:<id>", most recent first. */
    val recents: List<String> = emptyList(),
    val launchCounts: Map<String, Int> = emptyMap(),
    val quickNote: String = "",
    /** null = follow the system 12/24-hour setting. */
    val use24Hour: Boolean? = null,
    val swipeDownForCommandCenter: Boolean = true,
    /** How strongly the launcher darkens the system wallpaper, 0–100 %. 100 hides it completely. */
    val wallpaperDim: Int = DEFAULT_WALLPAPER_DIM,
    val weatherEnabled: Boolean = true,
    /** null = use the device's approximate location. */
    val weatherPlace: Place? = null,
    /** null = follow the system's temperature unit. */
    val useFahrenheit: Boolean? = null,
) {
    /** Ordered, visible pages. Home can never be hidden. */
    val visiblePages: List<PageId>
        get() = pageOrder.filter { it == PageId.Home || it !in hiddenPages }

    companion object {
        const val MAX_DOCK_APPS = 4
        const val MAX_RECENTS = 10
        const val DEFAULT_WALLPAPER_DIM = 40
    }
}

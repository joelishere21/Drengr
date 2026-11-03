package com.drengr.launcher.launcher

import android.graphics.Rect
import android.os.Bundle
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.core.domain.AppShortcut

/** App operations available to any screen that shows app icons. */
@Stable
interface AppInteractions {
    fun cachedIcon(app: AppInfo): ImageBitmap?
    suspend fun loadIcon(app: AppInfo): ImageBitmap?
    fun launch(app: AppInfo, sourceBounds: Rect? = null, options: Bundle? = null)
    fun toggleFavorite(app: AppInfo)
    fun toggleDock(app: AppInfo)
    fun openAppInfo(app: AppInfo)
    fun uninstall(app: AppInfo)
    fun shortcuts(app: AppInfo): List<AppShortcut>
    fun launchShortcut(shortcut: AppShortcut)
}

val LocalAppInteractions = staticCompositionLocalOf<AppInteractions> {
    error("AppInteractions not provided")
}

/** Favorite/dock membership, so app menus can show the right toggle labels. */
data class AppMembership(val favorites: Set<String> = emptySet(), val dock: Set<String> = emptySet())

val LocalAppMembership = compositionLocalOf { AppMembership() }

package com.drengr.launcher.launcher

import android.graphics.Rect
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drengr.launcher.core.datastore.SettingsRepository
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.core.domain.AppLayout
import com.drengr.launcher.core.domain.AppShortcut
import com.drengr.launcher.core.domain.PageId
import com.drengr.launcher.core.domain.UserSettings
import com.drengr.launcher.integrations.applications.AppRepository
import com.drengr.launcher.plugins.ActionRegistry
import com.drengr.launcher.plugins.LauncherAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class LauncherUiState(
    val settingsLoaded: Boolean = false,
    val appsLoaded: Boolean = false,
    val settings: UserSettings = UserSettings(),
    val apps: List<AppInfo> = emptyList(),
    val favorites: List<AppInfo> = emptyList(),
    val dock: List<AppInfo> = emptyList(),
    val frequent: List<AppInfo> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val dockKeys: Set<String> = emptySet(),
    val pages: List<PageId> = PageId.entries,
)

/** Shared launcher state (apps, favorites, dock, pages) and app-level interactions. */
@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    val actions: ActionRegistry,
) : ViewModel(), AppInteractions {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val uiState: StateFlow<LauncherUiState> = combine(
        settingsRepository.settings,
        appRepository.apps,
        appRepository.loaded,
    ) { settings, apps, appsLoaded ->
        val byKey = apps.associateBy { it.key }
        val dockKeys = settings.dock ?: if (appsLoaded) appRepository.defaultDockKeys(apps) else emptyList()
        val frequent = settings.launchCounts.entries
            .sortedByDescending { it.value }
            .mapNotNull { byKey[it.key] }
            .take(FREQUENT_COUNT)
        LauncherUiState(
            settingsLoaded = true,
            appsLoaded = appsLoaded,
            settings = settings,
            apps = apps,
            favorites = settings.favorites.mapNotNull(byKey::get),
            dock = dockKeys.mapNotNull(byKey::get).take(UserSettings.MAX_DOCK_APPS),
            frequent = frequent,
            favoriteKeys = settings.favorites.toSet(),
            dockKeys = dockKeys.toSet(),
            pages = settings.visiblePages,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState())

    // ---- AppInteractions ------------------------------------------------------------------------

    override fun cachedIcon(app: AppInfo): ImageBitmap? = appRepository.cachedIcon(app.key)

    override suspend fun loadIcon(app: AppInfo): ImageBitmap? = appRepository.loadIcon(app.key)

    override fun launch(app: AppInfo, sourceBounds: Rect?, options: Bundle?) {
        if (appRepository.launch(app, sourceBounds, options)) {
            viewModelScope.launch {
                settingsRepository.recordLaunch(app.key)
                settingsRepository.recordRecent("app:${app.key}")
            }
        } else {
            _messages.tryEmit("Couldn't open ${app.label}")
        }
    }

    override fun toggleFavorite(app: AppInfo) {
        viewModelScope.launch { settingsRepository.toggleFavorite(app.key) }
    }

    override fun toggleDock(app: AppInfo) {
        viewModelScope.launch {
            val current = uiState.value.dock.map { it.key }
            if (!settingsRepository.toggleDock(app.key, current)) {
                _messages.tryEmit("Dock is full — remove an app first")
            }
        }
    }

    override fun openAppInfo(app: AppInfo) = appRepository.openAppInfo(app)

    override fun uninstall(app: AppInfo) = appRepository.uninstall(app)

    override fun shortcuts(app: AppInfo): List<AppShortcut> = appRepository.shortcuts(app)

    override fun launchShortcut(shortcut: AppShortcut) = appRepository.launchShortcut(shortcut)

    // ---- Launcher state ------------------------------------------------------------------------

    fun setLastPage(page: PageId) {
        if (page != uiState.value.settings.lastPage) {
            viewModelScope.launch { settingsRepository.setLastPage(page) }
        }
    }

    fun setAppLayout(layout: AppLayout) {
        viewModelScope.launch { settingsRepository.setAppLayout(layout) }
    }

    fun recordAction(action: LauncherAction) {
        viewModelScope.launch { settingsRepository.recordRecent("action:${action.id}") }
    }

    fun showMessage(text: String) {
        _messages.tryEmit(text)
    }

    private companion object {
        const val FREQUENT_COUNT = 8
    }
}

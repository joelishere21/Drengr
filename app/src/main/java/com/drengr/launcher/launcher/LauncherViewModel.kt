package com.drengr.launcher.launcher

import android.graphics.Rect
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drengr.launcher.core.common.moving
import com.drengr.launcher.core.datastore.SettingsRepository
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.core.domain.AppLayout
import com.drengr.launcher.core.domain.AppShortcut
import com.drengr.launcher.core.domain.PageId
import com.drengr.launcher.core.domain.UserSettings
import com.drengr.launcher.core.domain.normalizeAppLabel
import com.drengr.launcher.core.domain.withCustomLabels
import com.drengr.launcher.integrations.applications.AppRepository
import com.drengr.launcher.integrations.notifications.NotificationRepository
import com.drengr.launcher.plugins.ActionRegistry
import com.drengr.launcher.feature.notes.NoteEditorController
import com.drengr.launcher.plugins.LauncherAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** Installed apps with the user's names applied, hidden apps left out. */
    val apps: List<AppInfo> = emptyList(),
    val favorites: List<AppInfo> = emptyList(),
    val dock: List<AppInfo> = emptyList(),
    val frequent: List<AppInfo> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val dockKeys: Set<String> = emptySet(),
    val hiddenKeys: Set<String> = emptySet(),
    val pages: List<PageId> = PageId.entries,
)

/** Shared launcher state (apps, favorites, dock, pages) and app-level interactions. */
@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    notifications: NotificationRepository,
    val actions: ActionRegistry,
    val noteEditor: NoteEditorController,
) : ViewModel(), AppInteractions {

    /** [AppInfo.notificationKey]s of apps with notifications, or empty when dots are off. */
    val notificationDots: StateFlow<Set<String>> = combine(
        notifications.counts,
        settingsRepository.settings,
    ) { counts, settings ->
        if (settings.notificationDots) counts.filterValues { it > 0 }.keys else emptySet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** How many dock apps the current window shows (more on tablets). */
    private val dockSlots = MutableStateFlow(UserSettings.PHONE_DOCK_APPS)

    /** All dock keys as stored, including any beyond what this window shows. */
    private var storedDock: List<String> = emptyList()

    val uiState: StateFlow<LauncherUiState> = combine(
        settingsRepository.settings,
        appRepository.apps,
        appRepository.loaded,
        dockSlots,
    ) { settings, apps, appsLoaded, slots ->
        val named = apps.withCustomLabels(settings.appLabels)
        // Favorites and the dock were pinned on purpose, so they keep showing hidden apps.
        val byKey = named.associateBy { it.key }
        val allDockKeys = settings.dock ?: if (appsLoaded) appRepository.defaultDockKeys(apps) else emptyList()
        storedDock = allDockKeys
        val dock = allDockKeys.mapNotNull(byKey::get).take(slots)
        val frequent = settings.launchCounts.entries
            .sortedByDescending { it.value }
            .filter { it.key !in settings.hiddenApps }
            .mapNotNull { byKey[it.key] }
            .take(FREQUENT_COUNT)
        LauncherUiState(
            settingsLoaded = true,
            appsLoaded = appsLoaded,
            settings = settings,
            apps = named.filter { it.key !in settings.hiddenApps },
            favorites = settings.favorites.mapNotNull(byKey::get),
            dock = dock,
            frequent = frequent,
            favoriteKeys = settings.favorites.toSet(),
            dockKeys = dock.mapTo(mutableSetOf()) { it.key },
            hiddenKeys = settings.hiddenApps,
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
            if (!settingsRepository.toggleDock(app.key, storedDock, limit = dockSlots.value)) {
                _messages.tryEmit("Dock is full — remove an app first")
            }
        }
    }

    override fun moveInDock(app: AppInfo, delta: Int) {
        val current = uiState.value.dock.map { it.key }
        val moved = current.moving(app.key, delta)
        // Keep apps this window doesn't show (a phone showing a tablet's 6-app dock).
        if (moved != current) viewModelScope.launch { settingsRepository.setDock(moved + (storedDock - current.toSet())) }
    }

    override fun rename(app: AppInfo, label: String?) {
        viewModelScope.launch {
            // Saving the app's own name is the same as resetting it.
            settingsRepository.setAppLabel(app.key, label?.takeIf { normalizeAppLabel(it) != app.originalLabel })
        }
    }

    override fun setHidden(app: AppInfo, hidden: Boolean) {
        viewModelScope.launch { settingsRepository.setAppHidden(app.key, hidden) }
        _messages.tryEmit(
            if (hidden) "${app.label} hidden · unhide in Settings"
            else "${app.label} is back in your apps",
        )
    }

    override fun openAppInfo(app: AppInfo) = appRepository.openAppInfo(app)

    override fun uninstall(app: AppInfo) = appRepository.uninstall(app)

    override fun shortcuts(app: AppInfo): List<AppShortcut> = appRepository.shortcuts(app)

    override fun launchShortcut(shortcut: AppShortcut) = appRepository.launchShortcut(shortcut)

    // ---- Launcher state ------------------------------------------------------------------------

    fun setDockSlots(slots: Int) {
        dockSlots.value = slots
    }

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

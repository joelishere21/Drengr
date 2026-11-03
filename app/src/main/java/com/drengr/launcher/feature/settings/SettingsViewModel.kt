package com.drengr.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drengr.launcher.core.common.moving
import com.drengr.launcher.core.datastore.SettingsRepository
import com.drengr.launcher.core.designsystem.AccentColor
import androidx.compose.ui.graphics.ImageBitmap
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.core.domain.AppLayout
import com.drengr.launcher.core.domain.withCustomLabels
import com.drengr.launcher.integrations.applications.AppRepository
import com.drengr.launcher.integrations.govee.GoveeRepository
import com.drengr.launcher.integrations.notifications.NotificationRepository
import com.drengr.launcher.core.domain.PageId
import com.drengr.launcher.core.domain.Place
import com.drengr.launcher.integrations.weather.WeatherRepository
import com.drengr.launcher.core.domain.UserSettings
import com.drengr.launcher.core.domain.HomeGesture
import com.drengr.launcher.plugins.ActionRegistry
import com.drengr.launcher.integrations.contacts.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface PlaceSearch {
    data object Idle : PlaceSearch
    data object Failed : PlaceSearch
    data class Results(val places: List<Place>) : PlaceSearch
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val weather: WeatherRepository,
    private val appRepository: AppRepository,
    private val notifications: NotificationRepository,
    private val govee: GoveeRepository,
    private val actions: ActionRegistry,
    private val contacts: ContactsRepository,
) : ViewModel() {

    private val _contactsGranted = MutableStateFlow(contacts.hasPermission())
    val contactsGranted: StateFlow<Boolean> = _contactsGranted.asStateFlow()

    fun refreshContactsAccess() {
        _contactsGranted.value = contacts.hasPermission()
    }

    fun setSearchNotesAndTasks(enabled: Boolean) = launch { repository.setSearchNotesAndTasks(enabled) }

    fun setSearchContacts(enabled: Boolean) = launch { repository.setSearchContacts(enabled) }

    /** After the contacts permission prompt: contact search turns on only if access was given. */
    fun onContactsResult(granted: Boolean) {
        refreshContactsAccess()
        if (granted) setSearchContacts(true)
    }

    fun clearRecents() = launch {
        repository.clearRecents()
        _messages.emit("Recent list cleared")
    }

    val goveeConnected: StateFlow<Boolean> =
        govee.hasKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun disconnectGovee() = launch {
        govee.removeKey()
        _messages.tryEmit("Govee disconnected. The key was removed from this phone.")
    }

    private val _notificationAccess = MutableStateFlow(notifications.hasAccess())
    val notificationAccess: StateFlow<Boolean> = _notificationAccess.asStateFlow()

    fun refreshNotificationAccess() {
        _notificationAccess.value = notifications.hasAccess()
    }

    fun openNotificationAccess() = notifications.openAccessSettings()
    fun setNotificationDots(enabled: Boolean) = launch { repository.setNotificationDots(enabled) }

    val settings: StateFlow<UserSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val hiddenApps: StateFlow<List<AppInfo>> =
        combine(appRepository.apps, repository.settings) { apps, s ->
            apps.withCustomLabels(s.appLabels).filter { it.key in s.hiddenApps }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Dock apps left to right; the launcher's defaults until the user changes the dock. */
    val dockApps: StateFlow<List<AppInfo>> =
        combine(appRepository.apps, repository.settings) { apps, s ->
            val named = apps.withCustomLabels(s.appLabels).associateBy { it.key }
            val keys = s.dock ?: appRepository.defaultDockKeys(apps)
            keys.mapNotNull(named::get).take(UserSettings.MAX_DOCK_APPS)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun moveDockApp(app: AppInfo, delta: Int) = launch {
        repository.setDock(dockApps.value.map { it.key }.moving(app.key, delta))
    }

    fun removeDockApp(app: AppInfo) = launch {
        repository.setDock(dockApps.value.map { it.key } - app.key)
    }

    fun unhideApp(app: AppInfo) = launch { repository.setAppHidden(app.key, false) }

    suspend fun appIcon(app: AppInfo): ImageBitmap? =
        appRepository.cachedIcon(app.key) ?: appRepository.loadIcon(app.key)

    private val _isDefaultHome = MutableStateFlow(false)
    val isDefaultHome: StateFlow<Boolean> = _isDefaultHome.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages

    fun refreshDefaultHome() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        _isDefaultHome.value = resolved?.activityInfo?.packageName == context.packageName
    }

    fun setAccent(accent: AccentColor) = launch { repository.setAccent(accent) }
    fun setLayout(layout: AppLayout) = launch { repository.setAppLayout(layout) }
    fun setColumns(columns: Int) = launch { repository.setGridColumns(columns) }
    fun setUse24Hour(value: Boolean?) = launch { repository.setUse24Hour(value) }
    fun setGesture(gesture: HomeGesture, target: String) = launch { repository.setGesture(gesture, target) }
    fun setGestureEdgeGuard(enabled: Boolean) = launch { repository.setGestureEdgeGuard(enabled) }

    /** Everything a Home gesture can be bound to on this device. */
    fun gestureChoices(): List<GestureChoice> = gestureChoices(actions.available(context))
    fun setWeatherEnabled(enabled: Boolean) = launch { repository.setWeatherEnabled(enabled) }
    fun setWeatherPlace(place: Place?) = launch {
        repository.setWeatherPlace(place)
        placeQuery.value = ""
    }
    fun setUseFahrenheit(value: Boolean?) = launch { repository.setUseFahrenheit(value) }
    val systemPrefersFahrenheit: Boolean get() = weather.systemPrefersFahrenheit()

    /** City search for weather: typed text, debounced, then looked up via Open-Meteo. */
    val placeQuery = MutableStateFlow("")
    val placeResults: StateFlow<PlaceSearch> = placeQuery
        .debounce(400)
        .map { it.trim() }
        .distinctUntilChanged()
        .mapLatest { query ->
            if (query.length < 2) {
                PlaceSearch.Idle
            } else {
                runCatching { weather.searchPlaces(query) }
                    .fold({ PlaceSearch.Results(it) }, { PlaceSearch.Failed })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceSearch.Idle)

    fun setWallpaperDim(percent: Int) = launch { repository.setWallpaperDim(percent) }

    fun changeWallpaper() {
        val chooser = Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
            .onFailure { viewModelScope.launch { _messages.emit("No wallpaper picker found") } }
    }
    fun movePage(page: PageId, delta: Int) = launch { repository.movePage(page, delta) }
    fun setPageHidden(page: PageId, hidden: Boolean) = launch { repository.setPageHidden(page, hidden) }

    fun export(uri: Uri) = launch {
        runCatching {
            val json = repository.exportJson()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
            }
        }.onSuccess { _messages.emit("Settings exported") }
            .onFailure { _messages.emit("Export failed: ${it.message}") }
    }

    fun import(uri: Uri) = launch {
        runCatching {
            val json = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            }
            repository.importJson(json)
        }.onSuccess { _messages.emit("Settings imported") }
            .onFailure { _messages.emit("Import failed: ${it.message}") }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

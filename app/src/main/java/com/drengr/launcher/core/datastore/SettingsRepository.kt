package com.drengr.launcher.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drengr.launcher.core.designsystem.AccentColor
import com.drengr.launcher.core.domain.AppLayout
import com.drengr.launcher.core.domain.PageId
import com.drengr.launcher.core.domain.Place
import com.drengr.launcher.core.domain.UserSettings
import com.drengr.launcher.core.domain.normalizeAppLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val SETTINGS_FORMAT = "drengr-settings"

private val Context.drengrDataStore: DataStore<Preferences> by preferencesDataStore(name = "drengr_settings")

/** Single source of truth for user preferences. Screens never write preferences directly. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.drengrDataStore

    private object Keys {
        val ACCENT = stringPreferencesKey("accent")
        val FAVORITES = stringPreferencesKey("favorites")
        val DOCK = stringPreferencesKey("dock")
        val PAGE_ORDER = stringPreferencesKey("page_order")
        val HIDDEN_PAGES = stringPreferencesKey("hidden_pages")
        val APP_LAYOUT = stringPreferencesKey("app_layout")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val LAST_PAGE = stringPreferencesKey("last_page")
        val RECENTS = stringPreferencesKey("recents")
        val LAUNCH_COUNTS = stringPreferencesKey("launch_counts")
        val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
        val APP_LABELS = stringPreferencesKey("app_labels")
        val QUICK_NOTE = stringPreferencesKey("quick_note")
        val USE_24_HOUR = stringPreferencesKey("use_24_hour")
        val SWIPE_DOWN_SHADE = booleanPreferencesKey("swipe_down_notifications")
        val NOTIFICATION_DOTS = booleanPreferencesKey("notification_dots")
        val WALLPAPER_DIM = intPreferencesKey("wallpaper_dim")
        val WEATHER_ENABLED = booleanPreferencesKey("weather_enabled")
        val WEATHER_PLACE = stringPreferencesKey("weather_place")
        val USE_FAHRENHEIT = stringPreferencesKey("use_fahrenheit")
    }

    val settings: Flow<UserSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setAccent(accent: AccentColor) = store.edit { it[Keys.ACCENT] = accent.name }

    suspend fun toggleFavorite(key: String) = store.edit { p ->
        val list = p[Keys.FAVORITES].decodeList().toMutableList()
        if (!list.remove(key)) list.add(key)
        p[Keys.FAVORITES] = list.encodeList()
    }

    /** Adds or removes an app from the dock. Returns false if the dock is full. */
    suspend fun toggleDock(key: String, currentDock: List<String>): Boolean {
        var added = true
        store.edit { p ->
            val list = (p[Keys.DOCK]?.decodeList() ?: currentDock).toMutableList()
            if (!list.remove(key)) {
                if (list.size >= UserSettings.MAX_DOCK_APPS) {
                    added = false
                    return@edit
                }
                list.add(key)
            }
            p[Keys.DOCK] = list.encodeList()
        }
        return added
    }

    suspend fun movePage(page: PageId, delta: Int) = store.edit { p ->
        val order = p.pageOrder().toMutableList()
        val from = order.indexOf(page)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from >= 0 && from != to) {
            order.removeAt(from)
            order.add(to, page)
            p[Keys.PAGE_ORDER] = order.joinToString("\n") { it.name }
        }
    }

    suspend fun setPageHidden(page: PageId, hidden: Boolean) = store.edit { p ->
        if (page == PageId.Home) return@edit
        val set = p.hiddenPages().toMutableSet()
        if (hidden) set.add(page) else set.remove(page)
        p[Keys.HIDDEN_PAGES] = set.joinToString("\n") { it.name }
    }

    suspend fun setAppLayout(layout: AppLayout) = store.edit { it[Keys.APP_LAYOUT] = layout.name }

    suspend fun setGridColumns(columns: Int) = store.edit { it[Keys.GRID_COLUMNS] = columns.coerceIn(3, 6) }

    suspend fun setLastPage(page: PageId) = store.edit { it[Keys.LAST_PAGE] = page.name }

    suspend fun recordRecent(entry: String) = store.edit { p ->
        val list = listOf(entry) + p[Keys.RECENTS].decodeList().filter { it != entry }
        p[Keys.RECENTS] = list.take(UserSettings.MAX_RECENTS).encodeList()
    }

    suspend fun recordLaunch(appKey: String) = store.edit { p ->
        val counts = p[Keys.LAUNCH_COUNTS].decodeCounts().toMutableMap()
        counts[appKey] = (counts[appKey] ?: 0) + 1
        p[Keys.LAUNCH_COUNTS] = counts.encodeCounts()
    }

    suspend fun setAppHidden(key: String, hidden: Boolean) = store.edit { p ->
        val set = p[Keys.HIDDEN_APPS].decodeList().toMutableSet()
        if (hidden) set.add(key) else set.remove(key)
        p[Keys.HIDDEN_APPS] = set.toList().encodeList()
    }

    /** Sets the name shown for an app. null (or blank) restores the app's own name. */
    suspend fun setAppLabel(key: String, label: String?) = store.edit { p ->
        val labels = p[Keys.APP_LABELS].decodeLabels().toMutableMap()
        val clean = label?.let(::normalizeAppLabel).orEmpty()
        if (clean.isEmpty()) labels.remove(key) else labels[key] = clean
        p[Keys.APP_LABELS] = labels.encodeLabels()
    }

    suspend fun setQuickNote(text: String) = store.edit { it[Keys.QUICK_NOTE] = text }

    suspend fun setUse24Hour(value: Boolean?) = store.edit { p ->
        if (value == null) p.remove(Keys.USE_24_HOUR) else p[Keys.USE_24_HOUR] = value.toString()
    }

    suspend fun setSwipeDownForNotifications(enabled: Boolean) =
        store.edit { it[Keys.SWIPE_DOWN_SHADE] = enabled }

    suspend fun setNotificationDots(enabled: Boolean) = store.edit { it[Keys.NOTIFICATION_DOTS] = enabled }

    suspend fun setWeatherEnabled(enabled: Boolean) = store.edit { it[Keys.WEATHER_ENABLED] = enabled }

    suspend fun setWeatherPlace(place: Place?) = store.edit { p ->
        if (place == null) p.remove(Keys.WEATHER_PLACE) else p[Keys.WEATHER_PLACE] = place.encode()
    }

    suspend fun setUseFahrenheit(value: Boolean?) = store.edit { p ->
        if (value == null) p.remove(Keys.USE_FAHRENHEIT) else p[Keys.USE_FAHRENHEIT] = value.toString()
    }

    suspend fun setWallpaperDim(percent: Int) = store.edit { it[Keys.WALLPAPER_DIM] = percent.coerceIn(0, 100) }

    // ---- Backup / restore -------------------------------------------------------------------

    suspend fun exportJson(): String {
        val s = current()
        return JSONObject().apply {
            put("format", SETTINGS_FORMAT)
            put("version", 1)
            put("accent", s.accent.name)
            put("favorites", JSONArray(s.favorites))
            s.dock?.let { put("dock", JSONArray(it)) }
            put("pageOrder", JSONArray(s.pageOrder.map { it.name }))
            put("hiddenPages", JSONArray(s.hiddenPages.map { it.name }))
            put("appLayout", s.appLayout.name)
            put("gridColumns", s.gridColumns)
            s.use24Hour?.let { put("use24Hour", it) }
            put("swipeDownForNotifications", s.swipeDownForNotifications)
            put("notificationDots", s.notificationDots)
            put("quickNote", s.quickNote)
            put("hiddenApps", JSONArray(s.hiddenApps.toList()))
            put("appLabels", JSONObject(s.appLabels))
            put("wallpaperDim", s.wallpaperDim)
            put("weatherEnabled", s.weatherEnabled)
            s.weatherPlace?.let { put("weatherPlace", it.encode()) }
            s.useFahrenheit?.let { put("useFahrenheit", it) }
        }.toString(2)
    }

    /** Restores settings from [exportJson] output. Unknown or missing fields are ignored. */
    suspend fun importJson(json: String) {
        val o = JSONObject(json)
        // Backups exported from the old com.quadro.launcher app say "quadro-settings"; accept both.
        require(o.optString("format") in setOf(SETTINGS_FORMAT, "quadro-settings")) { "Not a Drengr settings file" }
        store.edit { p ->
            o.optString("accent").takeIf { it.isNotEmpty() }?.let { p[Keys.ACCENT] = it }
            o.optJSONArray("favorites")?.let { p[Keys.FAVORITES] = it.strings().encodeList() }
            o.optJSONArray("dock")?.let { p[Keys.DOCK] = it.strings().encodeList() }
            o.optJSONArray("pageOrder")?.let { p[Keys.PAGE_ORDER] = it.strings().encodeList() }
            o.optJSONArray("hiddenPages")?.let { p[Keys.HIDDEN_PAGES] = it.strings().encodeList() }
            o.optString("appLayout").takeIf { it.isNotEmpty() }?.let { p[Keys.APP_LAYOUT] = it }
            if (o.has("gridColumns")) p[Keys.GRID_COLUMNS] = o.getInt("gridColumns").coerceIn(3, 6)
            if (o.has("use24Hour")) p[Keys.USE_24_HOUR] = o.getBoolean("use24Hour").toString()
            if (o.has("swipeDownForNotifications")) {
                p[Keys.SWIPE_DOWN_SHADE] = o.getBoolean("swipeDownForNotifications")
            }
            if (o.has("notificationDots")) p[Keys.NOTIFICATION_DOTS] = o.getBoolean("notificationDots")
            if (o.has("quickNote")) p[Keys.QUICK_NOTE] = o.getString("quickNote")
            o.optJSONArray("hiddenApps")?.let { p[Keys.HIDDEN_APPS] = it.strings().encodeList() }
            o.optJSONObject("appLabels")?.let { labels ->
                p[Keys.APP_LABELS] = labels.keys().asSequence()
                    .associateWith { normalizeAppLabel(labels.getString(it)) }
                    .filterValues { it.isNotEmpty() }
                    .encodeLabels()
            }
            if (o.has("weatherEnabled")) p[Keys.WEATHER_ENABLED] = o.getBoolean("weatherEnabled")
            o.optString("weatherPlace").takeIf { it.decodePlace() != null }?.let { p[Keys.WEATHER_PLACE] = it }
            if (o.has("useFahrenheit")) p[Keys.USE_FAHRENHEIT] = o.getBoolean("useFahrenheit").toString()
            if (o.has("wallpaperDim")) p[Keys.WALLPAPER_DIM] = o.getInt("wallpaperDim").coerceIn(0, 100)
        }
    }

    // ---- Mapping ------------------------------------------------------------------------------

    private fun Preferences.toSettings() = UserSettings(
        accent = AccentColor.fromName(this[Keys.ACCENT]),
        favorites = this[Keys.FAVORITES].decodeList(),
        dock = this[Keys.DOCK]?.decodeList(),
        pageOrder = pageOrder(),
        hiddenPages = hiddenPages(),
        appLayout = AppLayout.entries.firstOrNull { it.name == this[Keys.APP_LAYOUT] } ?: AppLayout.Grid,
        gridColumns = this[Keys.GRID_COLUMNS] ?: 4,
        lastPage = PageId.entries.firstOrNull { it.name == this[Keys.LAST_PAGE] } ?: PageId.Home,
        recents = this[Keys.RECENTS].decodeList(),
        launchCounts = this[Keys.LAUNCH_COUNTS].decodeCounts(),
        hiddenApps = this[Keys.HIDDEN_APPS].decodeList().toSet(),
        appLabels = this[Keys.APP_LABELS].decodeLabels(),
        quickNote = this[Keys.QUICK_NOTE].orEmpty(),
        use24Hour = this[Keys.USE_24_HOUR]?.toBooleanStrictOrNull(),
        swipeDownForNotifications = this[Keys.SWIPE_DOWN_SHADE] ?: true,
        notificationDots = this[Keys.NOTIFICATION_DOTS] ?: true,
        wallpaperDim = this[Keys.WALLPAPER_DIM] ?: UserSettings.DEFAULT_WALLPAPER_DIM,
        weatherEnabled = this[Keys.WEATHER_ENABLED] ?: true,
        weatherPlace = this[Keys.WEATHER_PLACE].decodePlace(),
        useFahrenheit = this[Keys.USE_FAHRENHEIT]?.toBooleanStrictOrNull(),
    )

    /** Saved order, with unknown names dropped and any new pages appended. */
    private fun Preferences.pageOrder(): List<PageId> {
        val saved = this[Keys.PAGE_ORDER].decodeList().mapNotNull { n -> PageId.entries.firstOrNull { it.name == n } }
        return (saved + PageId.entries).distinct()
    }

    private fun Preferences.hiddenPages(): Set<PageId> =
        this[Keys.HIDDEN_PAGES].decodeList()
            .mapNotNull { n -> PageId.entries.firstOrNull { it.name == n } }
            .filter { it != PageId.Home }
            .toSet()

    private fun MutablePreferences.pageOrder(): List<PageId> = (this as Preferences).pageOrder()
    private fun MutablePreferences.hiddenPages(): Set<PageId> = (this as Preferences).hiddenPages()
}

private fun String?.decodeList(): List<String> =
    this?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

private fun List<String>.encodeList(): String = joinToString("\n")

private fun String?.decodeCounts(): Map<String, Int> =
    decodeList().mapNotNull { line ->
        val tab = line.lastIndexOf('\t')
        if (tab <= 0) null else line.substring(0, tab) to (line.substring(tab + 1).toIntOrNull() ?: return@mapNotNull null)
    }.toMap()

private fun Map<String, Int>.encodeCounts(): String =
    entries.joinToString("\n") { "${it.key}\t${it.value}" }

// One "key<TAB>label" per line. App keys never contain tabs; labels are normalized to one line.
private fun String?.decodeLabels(): Map<String, String> =
    decodeList().mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
    }.toMap()

private fun Map<String, String>.encodeLabels(): String =
    entries.joinToString("\n") { "${it.key}\t${it.value}" }

private fun Place.encode(): String = "$name\t$latitude\t$longitude"

private fun String?.decodePlace(): Place? {
    val parts = this?.split('\t') ?: return null
    if (parts.size != 3) return null
    val lat = parts[1].toDoubleOrNull() ?: return null
    val lon = parts[2].toDoubleOrNull() ?: return null
    return Place(parts[0], lat, lon)
}

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

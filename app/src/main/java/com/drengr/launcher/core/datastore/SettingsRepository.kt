package com.drengr.launcher.core.datastore

import android.content.Context
import androidx.datastore.core.DataMigration
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
import com.drengr.launcher.core.domain.Note
import com.drengr.launcher.core.domain.Notes
import com.drengr.launcher.core.domain.TaskItem
import com.drengr.launcher.core.domain.Tasks
import com.drengr.launcher.core.domain.GestureTargets
import com.drengr.launcher.core.domain.HomeGesture
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

internal val SECRET_GOVEE_KEY = stringPreferencesKey("govee_api_key")

/**
 * Credentials, in their own file so it can be left out of Android's cloud backup and device
 * transfer (see res/xml/backup_rules.xml). The Govee key used to live in drengr_settings; the
 * migration moves it here and deletes the old copy.
 */
private val Context.secretsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "drengr_secrets",
    produceMigrations = { context -> listOf(GoveeKeyMigration(context.drengrDataStore)) },
)

internal class GoveeKeyMigration(private val settings: DataStore<Preferences>) : DataMigration<Preferences> {
    private val oldKey = stringPreferencesKey("govee_api_key")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = settings.data.first()[oldKey] != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val old = settings.data.first()[oldKey] ?: return currentData
        return currentData.toMutablePreferences().apply { if (this[SECRET_GOVEE_KEY] == null) this[SECRET_GOVEE_KEY] = old }
    }

    override suspend fun cleanUp() {
        settings.edit { it.remove(oldKey) }
    }
}

/** Single source of truth for user preferences. Screens never write preferences directly. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.drengrDataStore
    private val secrets = context.secretsDataStore

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
        /** Legacy single note; read only until notes are first saved. */
        val QUICK_NOTE = stringPreferencesKey("quick_note")
        val NOTES = stringPreferencesKey("notes")
        val TASKS = stringPreferencesKey("tasks")
        val SEARCH_CONTACTS = booleanPreferencesKey("search_contacts")
        val SEARCH_NOTES = booleanPreferencesKey("search_notes_tasks")
        val USE_24_HOUR = stringPreferencesKey("use_24_hour")
        /** Legacy on/off for swipe down; read only when the swipe-down gesture was never set. */
        val SWIPE_DOWN_SHADE = booleanPreferencesKey("swipe_down_notifications")
        fun gesture(g: HomeGesture) = stringPreferencesKey("gesture_" + g.name)
        val GESTURE_EDGE_GUARD = booleanPreferencesKey("gesture_edge_guard")
        val NOTIFICATION_DOTS = booleanPreferencesKey("notification_dots")
        val HIDDEN_GOVEE = stringPreferencesKey("hidden_govee_devices")
        val GOVEE_ORDER = stringPreferencesKey("govee_device_order")
        val WALLPAPER_DIM = intPreferencesKey("wallpaper_dim")
        val WEATHER_ENABLED = booleanPreferencesKey("weather_enabled")
        val WEATHER_PLACE = stringPreferencesKey("weather_place")
        val USE_FAHRENHEIT = stringPreferencesKey("use_fahrenheit")
    }

    val settings: Flow<UserSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    /**
     * The user's Govee API key, or null. Deliberately not part of [settings], settings exports or
     * Android backups: it stays on this phone.
     */
    val goveeApiKey: Flow<String?> = secrets.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[SECRET_GOVEE_KEY] }

    suspend fun setGoveeApiKey(key: String?) = secrets.edit { p ->
        if (key.isNullOrBlank()) p.remove(SECRET_GOVEE_KEY) else p[SECRET_GOVEE_KEY] = key
    }

    suspend fun setAccent(accent: AccentColor) = store.edit { it[Keys.ACCENT] = accent.name }

    suspend fun toggleFavorite(key: String) = store.edit { p ->
        val list = p[Keys.FAVORITES].decodeList().toMutableList()
        if (!list.remove(key)) list.add(key)
        p[Keys.FAVORITES] = list.encodeList()
    }

    /** Adds or removes an app from the dock. Returns false if [limit] apps are already in it. */
    suspend fun toggleDock(key: String, currentDock: List<String>, limit: Int = UserSettings.MAX_DOCK_APPS): Boolean {
        var added = true
        store.edit { p ->
            val list = (p[Keys.DOCK]?.decodeList() ?: currentDock).toMutableList()
            if (!list.remove(key)) {
                if (list.size >= limit.coerceAtMost(UserSettings.MAX_DOCK_APPS)) {
                    added = false
                    return@edit
                }
                list.add(key)
            }
            p[Keys.DOCK] = list.encodeList()
        }
        return added
    }

    /** Replaces the dock with [keys], in order (left to right). */
    suspend fun setDock(keys: List<String>) = store.edit {
        it[Keys.DOCK] = keys.distinct().take(UserSettings.MAX_DOCK_APPS).encodeList()
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

    /** Saves note [id] with [text] (blank deletes it), as the most recently edited note. */
    suspend fun saveNote(id: String, text: String, now: Long = System.currentTimeMillis()) =
        editNotes { Notes.save(it, id, text, now) }

    suspend fun deleteNote(id: String) = editNotes { Notes.delete(it, id) }

    private suspend fun editNotes(change: (List<Note>) -> List<Note>) = store.edit { p ->
        p[Keys.NOTES] = encodeNotes(change(p.notes()))
        p.remove(Keys.QUICK_NOTE)
    }

    /** Applies [change] to the task list atomically. */
    suspend fun editTasks(change: (List<TaskItem>) -> List<TaskItem>) = store.edit { p ->
        p[Keys.TASKS] = encodeTasks(change(decodeTasks(p[Keys.TASKS])))
    }

    suspend fun setSearchContacts(enabled: Boolean) = store.edit { it[Keys.SEARCH_CONTACTS] = enabled }

    suspend fun setSearchNotesAndTasks(enabled: Boolean) = store.edit { it[Keys.SEARCH_NOTES] = enabled }

    suspend fun clearRecents() = store.edit { it.remove(Keys.RECENTS) }

    suspend fun setUse24Hour(value: Boolean?) = store.edit { p ->
        if (value == null) p.remove(Keys.USE_24_HOUR) else p[Keys.USE_24_HOUR] = value.toString()
    }

    /** Binds [gesture] to [target] (a GestureTargets value or an action id). */
    suspend fun setGesture(gesture: HomeGesture, target: String) = store.edit { p ->
        p[Keys.gesture(gesture)] = target
        if (gesture == HomeGesture.SwipeDown) p.remove(Keys.SWIPE_DOWN_SHADE)
    }

    suspend fun setGestureEdgeGuard(enabled: Boolean) = store.edit { it[Keys.GESTURE_EDGE_GUARD] = enabled }

    suspend fun setGoveeDeviceHidden(deviceId: String, hidden: Boolean) = store.edit { p ->
        val set = p[Keys.HIDDEN_GOVEE].decodeList().toMutableSet()
        if (hidden) set.add(deviceId) else set.remove(deviceId)
        p[Keys.HIDDEN_GOVEE] = set.toList().encodeList()
    }

    suspend fun setGoveeDeviceOrder(deviceIds: List<String>) = store.edit {
        it[Keys.GOVEE_ORDER] = deviceIds.distinct().encodeList()
    }

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
            put("gestures", JSONObject(HomeGesture.entries.associate { it.name to s.gestureTarget(it) }))
            put("gestureEdgeGuard", s.gestureEdgeGuard)
            put("notificationDots", s.notificationDots)
            put("hiddenGoveeDevices", JSONArray(s.hiddenGoveeDevices.toList()))
            put("goveeDeviceOrder", JSONArray(s.goveeDeviceOrder))
            put("notes", JSONArray(encodeNotes(s.notes)))
            put("tasks", JSONArray(encodeTasks(s.tasks)))
            put("searchContacts", s.searchContacts)
            put("searchNotesAndTasks", s.searchNotesAndTasks)
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
            val gestures = o.optJSONObject("gestures")
            if (gestures != null) {
                HomeGesture.entries.forEach { g ->
                    gestures.optString(g.name).takeIf { it.isNotEmpty() }?.let { p[Keys.gesture(g)] = it }
                }
                p.remove(Keys.SWIPE_DOWN_SHADE)
            } else if (o.has("swipeDownForNotifications")) {
                // Backups from before gestures were configurable.
                p[Keys.gesture(HomeGesture.SwipeDown)] =
                    if (o.getBoolean("swipeDownForNotifications")) GestureTargets.NOTIFICATIONS else GestureTargets.NONE
                p.remove(Keys.SWIPE_DOWN_SHADE)
            }
            if (o.has("gestureEdgeGuard")) p[Keys.GESTURE_EDGE_GUARD] = o.getBoolean("gestureEdgeGuard")
            o.optJSONArray("goveeDeviceOrder")?.let { p[Keys.GOVEE_ORDER] = it.strings().encodeList() }
            o.optJSONArray("hiddenGoveeDevices")?.let { p[Keys.HIDDEN_GOVEE] = it.strings().encodeList() }
            if (o.has("notificationDots")) p[Keys.NOTIFICATION_DOTS] = o.getBoolean("notificationDots")
            val notes = o.optJSONArray("notes")
            if (notes != null) {
                p[Keys.NOTES] = encodeNotes(decodeNotes(notes.toString()))
                p.remove(Keys.QUICK_NOTE)
            } else if (o.has("quickNote")) {
                // Backups from before there were several notes.
                p[Keys.NOTES] = encodeNotes(legacyNote(o.getString("quickNote")))
                p.remove(Keys.QUICK_NOTE)
            }
            o.optJSONArray("tasks")?.let { p[Keys.TASKS] = encodeTasks(decodeTasks(it.toString())) }
            if (o.has("searchContacts")) p[Keys.SEARCH_CONTACTS] = o.getBoolean("searchContacts")
            if (o.has("searchNotesAndTasks")) p[Keys.SEARCH_NOTES] = o.getBoolean("searchNotesAndTasks")
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
        notes = notes(),
        tasks = decodeTasks(this[Keys.TASKS]),
        searchContacts = this[Keys.SEARCH_CONTACTS] ?: false,
        searchNotesAndTasks = this[Keys.SEARCH_NOTES] ?: true,
        use24Hour = this[Keys.USE_24_HOUR]?.toBooleanStrictOrNull(),
        gestures = gestures(),
        gestureEdgeGuard = this[Keys.GESTURE_EDGE_GUARD] ?: true,
        notificationDots = this[Keys.NOTIFICATION_DOTS] ?: true,
        hiddenGoveeDevices = this[Keys.HIDDEN_GOVEE].decodeList().toSet(),
        goveeDeviceOrder = this[Keys.GOVEE_ORDER].decodeList(),
        wallpaperDim = this[Keys.WALLPAPER_DIM] ?: UserSettings.DEFAULT_WALLPAPER_DIM,
        weatherEnabled = this[Keys.WEATHER_ENABLED] ?: true,
        weatherPlace = this[Keys.WEATHER_PLACE].decodePlace(),
        useFahrenheit = this[Keys.USE_FAHRENHEIT]?.toBooleanStrictOrNull(),
    )

    /** Saved notes, or the old single quick note if notes were never saved. */
    private fun Preferences.notes(): List<Note> =
        this[Keys.NOTES]?.let(::decodeNotes) ?: legacyNote(this[Keys.QUICK_NOTE].orEmpty())

    private fun Preferences.gestures(): Map<HomeGesture, String> = buildMap {
        HomeGesture.entries.forEach { g -> this@gestures[Keys.gesture(g)]?.let { put(g, it) } }
        // Swipe down used to be a plain on/off switch.
        if (HomeGesture.SwipeDown !in this && this@gestures[Keys.SWIPE_DOWN_SHADE] == false) {
            put(HomeGesture.SwipeDown, GestureTargets.NONE)
        }
    }

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

    private fun MutablePreferences.notes(): List<Note> = (this as Preferences).notes()
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

private const val LEGACY_NOTE_ID = "quick-note"

private fun legacyNote(text: String): List<Note> =
    if (text.isBlank()) emptyList() else listOf(Note(LEGACY_NOTE_ID, text, 0L))

internal fun encodeNotes(notes: List<Note>): String = JSONArray().apply {
    notes.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("updatedAt", it.updatedAt)) }
}.toString()

internal fun decodeNotes(json: String?): List<Note> = decodeObjects(json) { o ->
    val id = o.optString("id").ifEmpty { return@decodeObjects null }
    val text = o.optString("text").ifBlank { return@decodeObjects null }
    Note(id, text, o.optLong("updatedAt"))
}.distinctBy { it.id }.sortedByDescending { it.updatedAt }

internal fun encodeTasks(tasks: List<TaskItem>): String = JSONArray().apply {
    tasks.forEach { t ->
        put(JSONObject().apply {
            put("id", t.id)
            put("title", t.title)
            put("done", t.done)
            put("createdAt", t.createdAt)
            t.doneAt?.let { put("doneAt", it) }
        })
    }
}.toString()

internal fun decodeTasks(json: String?): List<TaskItem> = decodeObjects(json) { o ->
    val id = o.optString("id").ifEmpty { return@decodeObjects null }
    val title = Tasks.clean(o.optString("title")).ifEmpty { return@decodeObjects null }
    TaskItem(id, title, o.optBoolean("done"), o.optLong("createdAt"), if (o.has("doneAt")) o.optLong("doneAt") else null)
}.distinctBy { it.id }

private fun <T> decodeObjects(json: String?, read: (JSONObject) -> T?): List<T> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(read) }
    }.getOrDefault(emptyList())
}

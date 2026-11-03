package com.drengr.launcher.integrations.media

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A track or episode that played on this phone, as its media session described it. */
@Immutable
data class PlayedItem(
    val title: String,
    val artist: String?,
    val packageName: String,
    val appLabel: String,
    val playedAt: Long,
) {
    /** Same track from the same app. */
    val key: String get() = "$packageName\u0000$title\u0000${artist.orEmpty()}"
}

/**
 * What played recently, recorded from media sessions while Drengr has notification access.
 * Android doesn't share apps' listening history, so this only knows what played while that
 * access was on. Stays on the phone: kept in its own store, out of settings exports.
 */
@Singleton
class PlaybackHistoryRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.playbackStore

    val items: Flow<List<PlayedItem>> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { PlaybackHistory.decode(it[KEY]) }

    suspend fun record(playing: NowPlaying, now: Long = System.currentTimeMillis()) {
        val title = playing.title ?: return
        val item = PlayedItem(title, playing.artist, playing.packageName, playing.appLabel, now)
        store.edit { p -> p[KEY] = PlaybackHistory.encode(PlaybackHistory.record(PlaybackHistory.decode(p[KEY]), item)) }
    }

    suspend fun clear() = store.edit { it.remove(KEY) }

    private companion object {
        val KEY = stringPreferencesKey("played")
    }
}

private val Context.playbackStore: DataStore<Preferences> by preferencesDataStore(name = "drengr_media_history")

/** History list rules, free of Android so they can be unit tested. */
object PlaybackHistory {
    const val MAX_ITEMS = 10

    /** Puts [item] on top; a track already listed moves up instead of appearing twice. */
    fun record(items: List<PlayedItem>, item: PlayedItem): List<PlayedItem> =
        (listOf(item) + items.filter { it.key != item.key }).take(MAX_ITEMS)

    fun encode(items: List<PlayedItem>): String = JSONArray().apply {
        items.forEach { i ->
            put(JSONObject().apply {
                put("title", i.title)
                i.artist?.let { put("artist", it) }
                put("package", i.packageName)
                put("app", i.appLabel)
                put("at", i.playedAt)
            })
        }
    }.toString()

    fun decode(json: String?): List<PlayedItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { n ->
                val o = array.optJSONObject(n) ?: return@mapNotNull null
                val title = o.optString("title").ifEmpty { return@mapNotNull null }
                val pkg = o.optString("package").ifEmpty { return@mapNotNull null }
                PlayedItem(title, o.optString("artist").ifEmpty { null }, pkg, o.optString("app").ifEmpty { pkg }, o.optLong("at"))
            }
        }.getOrDefault(emptyList())
    }
}

/** Podcast players, by package or name. */
object PodcastApps {
    private val knownPackages = setOf(
        "au.com.shiftyjelly.pocketcasts",
        "de.danoeh.antennapod",
        "fm.castbox.audiobook.radio.podcast",
        "com.bambuna.podcastaddict",
        "com.google.android.apps.podcasts",
        "com.podbean.app.podcast",
        "fm.player",
        "com.spreaker.android",
        "com.reallybadapps.podcastguru",
        "app.podcast.cosmos",
        "com.stitcher.app",
        "com.clearchannel.iheartradio.controller",
    )

    fun isPodcastApp(packageName: String, label: String): Boolean =
        packageName in knownPackages ||
            "podcast" in packageName.lowercase() ||
            "podcast" in label.lowercase()
}

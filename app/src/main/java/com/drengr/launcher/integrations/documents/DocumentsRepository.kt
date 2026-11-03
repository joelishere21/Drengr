package com.drengr.launcher.integrations.documents

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A file the user picked through the Storage Access Framework. */
@Immutable
data class RecentDocument(
    val uri: String,
    val name: String,
    val mimeType: String?,
    /** When it was added or last opened from Drengr, epoch millis. Newest first in lists. */
    val usedAt: Long,
    /** Whether Android granted write access too, so editors opened from Drengr can save. */
    val writable: Boolean = false,
)

/**
 * Pinned and recently opened documents. Android doesn't share other apps' recent files, so this
 * holds only files the user picked in the system file picker; Drengr keeps access to exactly
 * those files and nothing else. Kept in its own store, out of settings exports: the access grants
 * belong to this device and don't carry over to another one.
 */
@Singleton
class DocumentsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.documentsStore
    private val resolver: ContentResolver get() = context.contentResolver

    val documents: Flow<List<RecentDocument>> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { DocumentList.decode(it[KEY]) }

    /** Keeps access to [uris] and puts them at the top of the list. Returns how many were added. */
    suspend fun add(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val picked = uris.mapIndexedNotNull { i, uri ->
            val writable = keepAccess(uri) ?: return@mapIndexedNotNull null
            // A millisecond apart, so several files picked together keep the picker's order.
            RecentDocument(uri.toString(), displayName(uri) ?: uri.lastPathSegment ?: "Document",
                resolver.getType(uri), now - i, writable)
        }
        var dropped = emptyList<RecentDocument>()
        store.edit { p ->
            val result = DocumentList.add(DocumentList.decode(p[KEY]), picked)
            dropped = result.dropped
            p[KEY] = DocumentList.encode(result.kept)
        }
        dropped.forEach { releaseAccess(it) }
        picked.size
    }

    suspend fun remove(document: RecentDocument) {
        store.edit { p -> p[KEY] = DocumentList.encode(DocumentList.decode(p[KEY]).filter { it.uri != document.uri }) }
        withContext(Dispatchers.IO) { releaseAccess(document) }
    }

    suspend fun clear() {
        val all = documents.first()
        store.edit { it.remove(KEY) }
        withContext(Dispatchers.IO) { all.forEach { releaseAccess(it) } }
    }

    /**
     * Checks the file is still there (it may have been moved, deleted or its access revoked) and
     * refreshes its name. Returns null if it can't be opened any more.
     */
    suspend fun prepareOpen(document: RecentDocument): RecentDocument? = withContext(Dispatchers.IO) {
        val name = runCatching { displayName(Uri.parse(document.uri)) }.getOrNull() ?: return@withContext null
        val updated = document.copy(name = name, usedAt = System.currentTimeMillis())
        store.edit { p -> p[KEY] = DocumentList.encode(DocumentList.touch(DocumentList.decode(p[KEY]), updated)) }
        updated
    }

    /** Opens the file in whichever app handles its type, passing on Drengr's access to it. */
    fun viewIntent(document: RecentDocument): Intent {
        val uri = Uri.parse(document.uri)
        var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (document.writable) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, document.mimeType ?: resolver.getType(uri) ?: "*/*")
            .addFlags(flags)
    }

    /** Returns whether write access was kept too, or null if Android refused to keep access. */
    private fun keepAccess(uri: Uri): Boolean? {
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (runCatching { resolver.takePersistableUriPermission(uri, readWrite) }.isSuccess) return true
        if (runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess) {
            return false
        }
        return null
    }

    private fun releaseAccess(document: RecentDocument) {
        val uri = Uri.parse(document.uri)
        var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (document.writable) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.releasePersistableUriPermission(uri, flags) }
    }

    /** The file's name, or null if it no longer exists or Drengr lost access. */
    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private companion object {
        val KEY = stringPreferencesKey("recent_documents")
    }
}

private val Context.documentsStore: DataStore<Preferences> by preferencesDataStore(name = "drengr_documents")

/** Pure list rules, separate from Android so they can be unit tested. */
object DocumentList {
    /** Android caps persisted grants per app; stay well under it. */
    const val MAX_DOCUMENTS = 12

    data class AddResult(val kept: List<RecentDocument>, val dropped: List<RecentDocument>)

    /** Puts [added] first (newest first), replacing duplicates; anything past the cap is dropped. */
    fun add(existing: List<RecentDocument>, added: List<RecentDocument>): AddResult {
        val addedUris = added.map { it.uri }.toSet()
        val merged = added.sortedByDescending { it.usedAt }.distinctBy { it.uri } +
            existing.filter { it.uri !in addedUris }
        return AddResult(merged.take(MAX_DOCUMENTS), merged.drop(MAX_DOCUMENTS))
    }

    /** Moves [document] to the top with its new name and time. Unknown documents are ignored. */
    fun touch(existing: List<RecentDocument>, document: RecentDocument): List<RecentDocument> =
        if (existing.none { it.uri == document.uri }) existing
        else listOf(document) + existing.filter { it.uri != document.uri }

    fun encode(documents: List<RecentDocument>): String = JSONArray().apply {
        documents.forEach { d ->
            put(JSONObject().apply {
                put("uri", d.uri)
                put("name", d.name)
                d.mimeType?.let { put("mime", it) }
                put("usedAt", d.usedAt)
                put("writable", d.writable)
            })
        }
    }.toString()

    fun decode(json: String?): List<RecentDocument> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("uri").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                RecentDocument(
                    uri = uri,
                    name = o.optString("name").ifEmpty { "Document" },
                    mimeType = o.optString("mime").takeIf { it.isNotEmpty() },
                    usedAt = o.optLong("usedAt"),
                    writable = o.optBoolean("writable"),
                )
            }.sortedByDescending { it.usedAt }
        }.getOrDefault(emptyList())
    }
}

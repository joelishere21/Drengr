package com.drengr.launcher.integrations.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class Contact(val id: Long, val lookupKey: String, val name: String, val hasPhone: Boolean)

/**
 * Looks up contacts by name for Command Center, read on the device via ContactsContract.
 * Only used once the user turns on contact search (which asks for contacts access); nothing
 * is copied or kept.
 */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Up to [limit] contacts matching [query] (names, nicknames, initials), best matches first. */
    suspend fun search(query: String, limit: Int = 5): List<Contact> = withContext(Dispatchers.IO) {
        if (!hasPermission() || query.isBlank()) return@withContext emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )
        val args = Bundle().apply { putInt(ContentResolver.QUERY_ARG_LIMIT, limit) }
        runCatching {
            context.contentResolver.query(uri, projection, args, null)?.use { c ->
                buildList {
                    while (c.moveToNext() && size < limit) {
                        val name = c.getString(2)?.trim().orEmpty()
                        if (name.isNotEmpty()) add(Contact(c.getLong(0), c.getString(1).orEmpty(), name, c.getInt(3) > 0))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Opens the contact in the contacts app. */
    fun viewIntent(contact: Contact): Intent =
        Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey))
}

package com.drengr.launcher.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GoveeKeyMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val oldKey = stringPreferencesKey("govee_api_key")

    @After fun tearDown() = scope.cancel()

    private fun settings() = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("settings.preferences_pb") }

    @Test fun movesTheKeyOutOfSettings() = runBlocking {
        val settings = settings()
        settings.edit { it[oldKey] = "secret-123"; it[stringPreferencesKey("accent")] = "Blue" }
        val secrets = PreferenceDataStoreFactory.create(
            migrations = listOf(GoveeKeyMigration(settings)),
            scope = scope,
        ) { folder.root.resolve("secrets.preferences_pb") }

        assertEquals("secret-123", secrets.data.first()[SECRET_GOVEE_KEY])
        val after = settings.data.first()
        assertNull(after[oldKey])
        assertEquals("Blue", after[stringPreferencesKey("accent")])
    }

    @Test fun nothingToMoveLeavesSecretsEmpty() = runBlocking {
        val settings = settings()
        val secrets = PreferenceDataStoreFactory.create(
            migrations = listOf(GoveeKeyMigration(settings)),
            scope = scope,
        ) { folder.root.resolve("secrets.preferences_pb") }
        assertNull(secrets.data.first()[SECRET_GOVEE_KEY])
    }
}

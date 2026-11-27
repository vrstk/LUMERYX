package com.lumeryx.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemePreferencesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun emptyPreferencesFollowTheSystem() = runBlocking {
        withTimeout(5_000) {
            val file = File(temporaryFolder.root, "settings.preferences_pb")
            withPreferences(file) { preferences ->
                assertEquals(ThemeMode.SYSTEM, preferences.theme.first())
            }
        }
    }

    @Test
    fun savedThemeSurvivesReopeningTheFile() = runBlocking {
        withTimeout(5_000) {
            val file = File(temporaryFolder.root, "settings.preferences_pb")
            withPreferences(file) { preferences -> preferences.setTheme(ThemeMode.DARK) }
            withPreferences(file) { preferences ->
                assertEquals(ThemeMode.DARK, preferences.theme.first())
                preferences.setTheme(ThemeMode.LIGHT)
            }
            withPreferences(file) { preferences ->
                assertEquals(ThemeMode.LIGHT, preferences.theme.first())
                preferences.setTheme(ThemeMode.SYSTEM)
            }
            withPreferences(file) { preferences ->
                assertEquals(ThemeMode.SYSTEM, preferences.theme.first())
            }
        }
    }

    @Test
    fun unrecognizedStoredValueUsesTheSystemTheme() = runBlocking {
        withTimeout(5_000) {
            val file = File(temporaryFolder.root, "settings.preferences_pb")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
                dataStore.edit { it[stringPreferencesKey("theme_mode")] = "future-theme" }

                assertEquals(ThemeMode.SYSTEM, ThemePreferences(dataStore).theme.first())
            } finally {
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }
    }

    private suspend fun withPreferences(file: File, block: suspend (ThemePreferences) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
            block(ThemePreferences(dataStore))
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }
}

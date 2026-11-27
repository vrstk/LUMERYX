package com.lumeryx.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.lumeryxSettings by preferencesDataStore(name = "settings")

enum class ThemeMode(internal val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    internal companion object {
        fun fromStoredValue(value: String?): ThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

internal class ThemePreferences(private val dataStore: DataStore<Preferences>) {
    val theme: Flow<ThemeMode> = dataStore.data.map {
        ThemeMode.fromStoredValue(it[themeKey])
    }

    suspend fun setTheme(mode: ThemeMode) {
        dataStore.edit { it[themeKey] = mode.storedValue }
    }

    private companion object {
        val themeKey = stringPreferencesKey("theme_mode")
    }
}

package com.lumeryx.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class SettingsState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val loading: Boolean = true,
    val readFailed: Boolean = false,
    val saving: Boolean = false,
    val failedTheme: ThemeMode? = null,
)

internal class SettingsViewModel(private val preferences: ThemePreferences) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsState())
    val state = mutableState.asStateFlow()
    private var readJob: Job? = null

    init {
        loadSettings()
    }

    fun loadSettings() {
        readJob?.cancel()
        mutableState.update { it.copy(loading = true, readFailed = false) }
        readJob = viewModelScope.launch {
            preferences.theme
                .catch { error ->
                    if (error !is IOException) throw error
                    mutableState.update { it.copy(loading = false, readFailed = true) }
                }
                .collect { mode ->
                    mutableState.update { it.copy(theme = mode, loading = false) }
                }
        }
    }

    fun setTheme(mode: ThemeMode) {
        val current = state.value
        if (current.loading || current.readFailed || current.saving) return

        mutableState.update { it.copy(saving = true, failedTheme = null) }
        viewModelScope.launch {
            try {
                preferences.setTheme(mode)
                mutableState.update { it.copy(theme = mode, saving = false) }
            } catch (_: IOException) {
                mutableState.update { it.copy(saving = false, failedTheme = mode) }
            }
        }
    }
}

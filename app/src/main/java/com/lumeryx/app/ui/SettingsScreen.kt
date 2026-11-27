package com.lumeryx.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lumeryx.app.BuildConfig
import com.lumeryx.app.R
import com.lumeryx.app.settings.SettingsState
import com.lumeryx.app.settings.ThemeMode

@Composable
internal fun SettingsScreen(
    state: SettingsState,
    onThemeSelected: (ThemeMode) -> Unit,
    onRetryRead: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeading(R.string.settings_theme)
            Column(Modifier.selectableGroup()) {
                val enabled = !state.loading && !state.readFailed && !state.saving
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = state.theme == mode,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onThemeSelected(mode) },
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(state.theme == mode, onClick = null, enabled = enabled)
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            when {
                state.loading -> Text(
                    stringResource(R.string.settings_loading),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                state.readFailed -> SettingsError(R.string.settings_read_error, onRetryRead)
                else -> state.failedTheme?.let { mode ->
                    SettingsError(R.string.settings_write_error) { onThemeSelected(mode) }
                }
            }
            HorizontalDivider()
            SectionHeading(R.string.about_app)
            Text(
                stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SectionHeading(@StringRes label: Int) {
    Text(
        stringResource(label),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun SettingsError(@StringRes message: Int, onRetry: () -> Unit) {
    Column {
        Text(
            stringResource(message),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

package com.lumeryx.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lumeryx.app.R
import com.lumeryx.app.settings.SettingsState
import com.lumeryx.app.settings.ThemeMode

private enum class Destination(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Home("home", R.string.nav_home, R.drawable.ic_home),
    Library("library", R.string.nav_library, R.drawable.ic_library),
    Browser("browser", R.string.nav_browser, R.drawable.ic_browser),
    Cloud("cloud", R.string.nav_cloud, R.drawable.ic_cloud),
    Settings("settings", R.string.nav_settings, R.drawable.ic_settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LumeryxApp(
    settings: SettingsState,
    onThemeSelected: (ThemeMode) -> Unit,
    onRetryRead: () -> Unit,
) {
    val navigation = rememberNavController()
    val currentSettings by rememberUpdatedState(settings)
    val entry by navigation.currentBackStackEntryAsState()
    val current = Destination.entries.firstOrNull { it.route == entry?.destination?.route }
        ?: Destination.Home

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = if (current == Destination.Home) R.string.app_name else current.label
                    Text(stringResource(title))
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination,
                        onClick = {
                            if (current != destination) {
                                navigation.navigate(destination.route) {
                                    popUpTo(Destination.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = {
                            Text(
                                stringResource(destination.label),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        },
                    )
                }
            }
        },
    ) { insets ->
        NavHost(
            navController = navigation,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(insets),
        ) {
            composable(Destination.Home.route) {
                SectionMessage(R.string.home_title, R.string.home_description)
            }
            composable(Destination.Library.route) {
                SectionMessage(R.string.library_unavailable, R.string.library_description)
            }
            composable(Destination.Browser.route) {
                SectionMessage(R.string.browser_unavailable, R.string.browser_description)
            }
            composable(Destination.Cloud.route) {
                SectionMessage(R.string.cloud_unavailable, R.string.cloud_description)
            }
            composable(Destination.Settings.route) {
                SettingsScreen(currentSettings, onThemeSelected, onRetryRead)
            }
        }
    }
}

@Composable
private fun SectionMessage(@StringRes title: Int, @StringRes description: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

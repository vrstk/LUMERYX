package com.lumeryx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumeryx.app.library.LibraryDatabase
import com.lumeryx.app.library.LibraryViewModel
import com.lumeryx.app.library.SafLibraryRepository
import com.lumeryx.app.settings.SettingsViewModel
import com.lumeryx.app.settings.ThemePreferences
import com.lumeryx.app.settings.lumeryxSettings
import com.lumeryx.app.transfer.CopyViewModel
import com.lumeryx.app.transfer.SafCopyStorage
import com.lumeryx.app.ui.LumeryxApp
import com.lumeryx.app.ui.LumeryxTheme
import com.lumeryx.app.ui.useDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val factory = viewModelFactory {
            initializer {
                SettingsViewModel(ThemePreferences(applicationContext.lumeryxSettings))
            }
            initializer {
                val dao = LibraryDatabase.get(applicationContext).libraryDao()
                LibraryViewModel(SafLibraryRepository(applicationContext.contentResolver, dao))
            }
            initializer { CopyViewModel(SafCopyStorage(applicationContext.contentResolver)) }
        }
        val models = ViewModelProvider(this, factory)
        val settingsModel = models[SettingsViewModel::class.java]
        val libraryModel = models[LibraryViewModel::class.java]
        val copyModel = models[CopyViewModel::class.java]

        setContent {
            val settings by settingsModel.state.collectAsStateWithLifecycle()
            val dark = useDarkTheme(settings.theme)

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            LumeryxTheme(dark = dark) {
                LumeryxApp(
                    settings = settings,
                    libraryModel = libraryModel,
                    copyModel = copyModel,
                    onThemeSelected = settingsModel::setTheme,
                    onRetryRead = settingsModel::loadSettings,
                )
            }
        }
    }
}

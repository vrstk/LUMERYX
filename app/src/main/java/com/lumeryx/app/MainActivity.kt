package com.lumeryx.app

import android.content.Intent
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
import com.lumeryx.app.browser.BrowserModel
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
    private lateinit var browserModel: BrowserModel

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptSharedLink(intent)
    }

    private fun acceptSharedLink(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(browserModel::offerLink)
        }
    }

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
            initializer { BrowserModel(applicationContext) }
            initializer { CopyViewModel(SafCopyStorage(applicationContext.contentResolver)) }
        }
        val models = ViewModelProvider(this, factory)
        val settingsModel = models[SettingsViewModel::class.java]
        val libraryModel = models[LibraryViewModel::class.java]
        val copyModel = models[CopyViewModel::class.java]

        browserModel = models[BrowserModel::class.java]
        if (savedInstanceState == null) acceptSharedLink(intent)

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
                    browserModel = browserModel,
                    onThemeSelected = settingsModel::setTheme,
                    onRetryRead = settingsModel::loadSettings,
                )
            }
        }
    }
}

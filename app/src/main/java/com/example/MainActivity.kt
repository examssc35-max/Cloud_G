package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ThemeSetting
import com.example.ui.navigation.NavGraph
import com.example.ui.theme.CloudGalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as CloudGalleryApp
        val preferencesManager = app.preferencesManager

        setContent {
            val themeSetting by preferencesManager.themeFlow.collectAsStateWithLifecycle(initialValue = ThemeSetting.SYSTEM)
            val isDark = when (themeSetting) {
                ThemeSetting.SYSTEM -> isSystemInDarkTheme()
                ThemeSetting.DARK -> true
                ThemeSetting.LIGHT -> false
            }

            CloudGalleryTheme(darkTheme = isDark) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavGraph(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

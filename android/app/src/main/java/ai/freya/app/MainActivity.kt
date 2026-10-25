package ai.freya.app

import ai.freya.app.ui.FreyaApp
import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.ThemeMode
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dataStore = (application as FreyaApplication).container.dataStore
        setContent {
            val themeMode by dataStore.themeMode.collectAsStateWithLifecycle(initialValue = "dark")
            val reduceMotionPref by dataStore.reduceMotion.collectAsStateWithLifecycle(initialValue = false)
            // Respect the system "Remove animations" setting as well as the in-app toggle.
            val systemNoAnimations = Settings.Global.getFloat(
                contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            ) == 0f
            FreyaTheme(
                themeMode = when (themeMode) {
                    "light" -> ThemeMode.LIGHT
                    "system" -> ThemeMode.SYSTEM
                    else -> ThemeMode.DARK
                },
                reduceMotion = reduceMotionPref || systemNoAnimations,
            ) {
                FreyaApp()
            }
        }
    }
}

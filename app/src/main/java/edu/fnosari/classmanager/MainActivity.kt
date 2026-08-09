package edu.fnosari.classmanager

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import edu.fnosari.classmanager.ui.AppLocale
import edu.fnosari.classmanager.ui.AppNavHost
import edu.fnosari.classmanager.ui.theme.ClassManagerTheme
import edu.fnosari.classmanager.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val tag = (newBase.applicationContext as? ClassManagerApp)?.container?.language
            ?: AppLocale.SYSTEM
        super.attachBaseContext(AppLocale.wrap(newBase, tag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startStudentId = intent.getLongExtra(EXTRA_STUDENT_ID, -1L).takeIf { it > 0 }
        val container = appContainer
        setContent {
            // cached value first so the very first frame already has the right theme
            val pref by container.settings.theme.collectAsState(initial = container.theme)
            val dark = isDarkTheme(pref, isSystemInDarkTheme())
            // The status bar sits on the top app bar (colorScheme.primary), which is a deep green
            // in light theme and a pale mint in dark — so the icon colours are the inverse of the
            // usual light/dark mapping. The navigation bar sits on the app background.
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
            }
            ClassManagerTheme(darkTheme = dark) {
                val nav = rememberNavController()
                AppNavHost(nav, startStudentId)
            }
        }
    }

    companion object { const val EXTRA_STUDENT_ID = "studentId" }
}

package edu.fnosari.classmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import edu.fnosari.classmanager.ui.AppNavHost
import edu.fnosari.classmanager.ui.theme.ClassManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startStudentId = intent.getLongExtra(EXTRA_STUDENT_ID, -1L).takeIf { it > 0 }
        setContent {
            ClassManagerTheme {
                val nav = rememberNavController()
                AppNavHost(nav, startStudentId)
            }
        }
    }

    companion object { const val EXTRA_STUDENT_ID = "studentId" }
}

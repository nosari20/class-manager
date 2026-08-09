package edu.fnosari.classmanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

object Routes {
    const val CLASS_LIST = "classes"
    const val CLASS_DETAIL = "class/{classId}"
    const val STUDENT = "student/{studentId}"
    const val PICKER = "picker/{classId}"
    const val GROUPS = "groups/{classId}"
    const val CSV_IMPORT = "csvImport"
    const val SETTINGS = "settings"
    fun classDetail(id: Long) = "class/$id"
    fun student(id: Long) = "student/$id"
    fun picker(classId: Long) = "picker/$classId"
    fun groups(classId: Long) = "groups/$classId"
}

@Composable
fun AppNavHost(nav: NavHostController, startStudentId: Long?) {
    NavHost(navController = nav, startDestination = Routes.CLASS_LIST) {
        composable(Routes.CLASS_LIST) { PlaceholderScreen("Classes") }
        composable(
            Routes.CLASS_DETAIL,
            arguments = listOf(navArgument("classId") { type = NavType.LongType }),
        ) {
            PlaceholderScreen("Class ${it.arguments!!.getLong("classId")}")
        }
        composable(
            Routes.STUDENT,
            arguments = listOf(navArgument("studentId") { type = NavType.LongType }),
        ) {
            PlaceholderScreen("Student ${it.arguments!!.getLong("studentId")}")
        }
        composable(
            Routes.PICKER,
            arguments = listOf(navArgument("classId") { type = NavType.LongType }),
        ) {
            PlaceholderScreen("Picker")
        }
        composable(
            Routes.GROUPS,
            arguments = listOf(navArgument("classId") { type = NavType.LongType }),
        ) {
            PlaceholderScreen("Groups")
        }
        composable(Routes.CSV_IMPORT) { PlaceholderScreen("CSV import") }
        composable(Routes.SETTINGS) { PlaceholderScreen("Settings") }
    }
    LaunchedEffect(startStudentId) {
        if (startStudentId != null && startStudentId > 0) nav.navigate(Routes.student(startStudentId))
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title) }
}

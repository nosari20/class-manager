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
import edu.fnosari.classmanager.ui.classdetail.ClassDetailScreen
import edu.fnosari.classmanager.ui.classlist.ClassListScreen
import edu.fnosari.classmanager.ui.csv.CsvImportScreen
import edu.fnosari.classmanager.ui.groups.GroupsScreen
import edu.fnosari.classmanager.ui.picker.PickerScreen
import edu.fnosari.classmanager.ui.student.StudentDetailScreen

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
        composable(Routes.CLASS_LIST) {
            ClassListScreen(
                onOpenClass = { nav.navigate(Routes.classDetail(it)) },
                onImportCsv = { nav.navigate(Routes.CSV_IMPORT) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.CLASS_DETAIL,
            arguments = listOf(navArgument("classId") { type = NavType.LongType }),
        ) {
            val classId = it.arguments!!.getLong("classId")
            ClassDetailScreen(
                classId,
                onOpenStudent = { id -> nav.navigate(Routes.student(id)) },
                onPicker = { nav.navigate(Routes.picker(classId)) },
                onGroups = { nav.navigate(Routes.groups(classId)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.STUDENT,
            arguments = listOf(navArgument("studentId") { type = NavType.LongType }),
        ) {
            StudentDetailScreen(it.arguments!!.getLong("studentId"), onBack = { nav.popBackStack() })
        }
        composable(
            Routes.PICKER,
            arguments = listOf(navArgument("classId") { type = NavType.LongType }),
        ) {
            PickerScreen(it.arguments!!.getLong("classId"), onBack = { nav.popBackStack() })
        }
        composable(
            Routes.GROUPS,
            arguments = listOf(navArgument("classId") { type = NavType.LongType }),
        ) {
            GroupsScreen(it.arguments!!.getLong("classId"), onBack = { nav.popBackStack() })
        }
        composable(Routes.CSV_IMPORT) {
            CsvImportScreen(
                onDone = { id ->
                    nav.navigate(Routes.classDetail(id)) { popUpTo(Routes.CLASS_LIST) }
                },
                onBack = { nav.popBackStack() },
            )
        }
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

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
import edu.fnosari.classmanager.ui.csv.CsvImportScreen
import edu.fnosari.classmanager.ui.home.HomeScreen
import edu.fnosari.classmanager.ui.groups.GroupsScreen
import edu.fnosari.classmanager.ui.picker.PickerScreen
import edu.fnosari.classmanager.ui.rooms.RoomEditorScreen
import edu.fnosari.classmanager.ui.rooms.RoomsScreen
import edu.fnosari.classmanager.ui.seating.SeatingPlansScreen
import edu.fnosari.classmanager.ui.seating.SeatingScreen
import edu.fnosari.classmanager.ui.settings.SettingsScreen
import edu.fnosari.classmanager.ui.student.StudentDetailScreen
import edu.fnosari.classmanager.ui.timetable.GlobalTimetableScreen

object Routes {
    const val CLASS_LIST = "classes"
    const val CLASS_DETAIL = "class/{classId}?roomId={roomId}"
    const val STUDENT = "student/{studentId}"
    const val PICKER = "picker/{classId}"
    const val GROUPS = "groups/{classId}"
    const val CSV_IMPORT = "csvImport"
    const val SETTINGS = "settings"
    const val GLOBAL_TIMETABLE = "globalTimetable"
    const val ROOMS = "rooms"
    const val ROOM_EDITOR = "room/{roomId}"
    const val SEATING_PLANS = "seatingPlans/{classId}"
    const val SEATING = "seating/{planId}"
    fun classDetail(id: Long, roomId: Long? = null) =
        "class/$id" + (roomId?.let { "?roomId=$it" } ?: "")
    fun student(id: Long) = "student/$id"
    fun picker(classId: Long) = "picker/$classId"
    fun groups(classId: Long) = "groups/$classId"
    fun roomEditor(id: Long) = "room/$id"
    fun seatingPlans(classId: Long) = "seatingPlans/$classId"
    fun seating(planId: Long) = "seating/$planId"
}

@Composable
fun AppNavHost(nav: NavHostController, startStudentId: Long?) {
    NavHost(navController = nav, startDestination = Routes.CLASS_LIST) {
        composable(Routes.CLASS_LIST) {
            HomeScreen(
                onOpenClass = { nav.navigate(Routes.classDetail(it)) },
                onImportCsv = { nav.navigate(Routes.CSV_IMPORT) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenCourse = { classId, roomId ->
                    nav.navigate(Routes.classDetail(classId, roomId))
                },
                onOpenStudent = { nav.navigate(Routes.student(it)) },
                onOpenTimetable = { nav.navigate(Routes.GLOBAL_TIMETABLE) },
            )
        }
        composable(Routes.GLOBAL_TIMETABLE) {
            GlobalTimetableScreen(
                onBack = { nav.popBackStack() },
                onOpenClass = { nav.navigate(Routes.classDetail(it)) },
            )
        }
        composable(
            Routes.CLASS_DETAIL,
            arguments = listOf(
                navArgument("classId") { type = NavType.LongType },
                navArgument("roomId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            val classId = it.arguments!!.getLong("classId")
            ClassDetailScreen(
                classId,
                onOpenStudent = { id -> nav.navigate(Routes.student(id)) },
                onPicker = { nav.navigate(Routes.picker(classId)) },
                onGroups = { nav.navigate(Routes.groups(classId)) },
                onSeating = { nav.navigate(Routes.seatingPlans(classId)) },
                onBack = { nav.popBackStack() },
                roomId = it.arguments!!.getLong("roomId").takeIf { id -> id > 0 },
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
        composable(Routes.ROOMS) {
            RoomsScreen(
                onOpenRoom = { nav.navigate(Routes.roomEditor(it)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.ROOM_EDITOR,
            arguments = listOf(navArgument("roomId") { type = NavType.LongType }),
        ) {
            RoomEditorScreen(it.arguments!!.getLong("roomId"), onBack = { nav.popBackStack() })
        }
        composable(
            Routes.SEATING_PLANS,
            arguments = listOf(navArgument("classId") { type = NavType.LongType }),
        ) {
            SeatingPlansScreen(
                it.arguments!!.getLong("classId"),
                onOpenPlan = { id -> nav.navigate(Routes.seating(id)) },
                onManageRooms = { nav.navigate(Routes.ROOMS) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.SEATING,
            arguments = listOf(navArgument("planId") { type = NavType.LongType }),
        ) {
            SeatingScreen(it.arguments!!.getLong("planId"), onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onRestored = {
                    nav.navigate(Routes.CLASS_LIST) { popUpTo(0) { inclusive = true } }
                },
                onRooms = { nav.navigate(Routes.ROOMS) },
            )
        }
    }
    LaunchedEffect(startStudentId) {
        if (startStudentId != null && startStudentId > 0) nav.navigate(Routes.student(startStudentId))
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title) }
}

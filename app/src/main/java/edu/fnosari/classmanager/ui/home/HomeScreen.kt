package edu.fnosari.classmanager.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.ui.classlist.ClassListScreen
import edu.fnosari.classmanager.ui.today.TodayScreen

@Composable
fun HomeScreen(
    onOpenClass: (Long) -> Unit,
    onImportCsv: () -> Unit,
    onSettings: () -> Unit,
    onOpenCourse: (classId: Long, roomId: Long?) -> Unit,
    onOpenStudent: (Long) -> Unit,
    onOpenTimetable: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Today, null) },
                    label = { Text(stringResource(R.string.tab_today)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.School, null) },
                    label = { Text(stringResource(R.string.tab_classes)) },
                )
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            if (tab == 0) {
                TodayScreen(
                    onOpenCourse = onOpenCourse,
                    onOpenStudent = onOpenStudent,
                    onOpenTimetable = onOpenTimetable,
                )
            } else {
                ClassListScreen(
                    onOpenClass = onOpenClass,
                    onImportCsv = onImportCsv,
                    onSettings = onSettings,
                )
            }
        }
    }
}

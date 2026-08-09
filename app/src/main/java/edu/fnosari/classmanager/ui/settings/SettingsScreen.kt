package edu.fnosari.classmanager.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.ui.common.pronoteTopBarColors
import edu.fnosari.classmanager.appContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onRestored: () -> Unit, onRooms: () -> Unit) {
    val context = LocalContext.current
    val vm: SettingsViewModel =
        viewModel(factory = SettingsViewModel.factory(context.appContainer))
    val digestTime by vm.digestTime.collectAsStateWithLifecycle()
    val weekARef by vm.weekARef.collectAsStateWithLifecycle()

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) vm.backupTo(context, uri) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.startRestore(context, uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pronoteTopBarColors(),
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.digest_time)) },
                supportingContent = { Text(digestTime) },
                modifier = Modifier.clickable { showTimePicker = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.week_a_reference)) },
                supportingContent = {
                    Column {
                        Text(weekARef ?: stringResource(R.string.not_set))
                        Text(stringResource(R.string.week_a_help))
                    }
                },
                modifier = Modifier.clickable { showDatePicker = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.rooms)) },
                modifier = Modifier.clickable { onRooms() },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup)) },
                modifier = Modifier.clickable {
                    backupLauncher.launch("classmanager-backup-${LocalDate.now()}.zip")
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.restore)) },
                modifier = Modifier.clickable {
                    restoreLauncher.launch(arrayOf("application/zip", "*/*"))
                },
            )
        }
    }

    if (showTimePicker) {
        val parts = digestTime.split(":")
        val state = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 7,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDigestTime(String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute))
                    showTimePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
                        vm.setWeekARef(monday.toString())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) { DatePicker(state = state) }
    }

    when (vm.restoreState) {
        null -> {}
        "confirm" -> AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            text = { Text(stringResource(R.string.restore_warning)) },
            confirmButton = {
                TextButton(onClick = { vm.confirmRestore() }) {
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
        "done" -> AlertDialog(
            onDismissRequest = { vm.dismissDialog(); onRestored() },
            text = { Text(stringResource(R.string.restore_done)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissDialog(); onRestored() }) {
                    Text(stringResource(R.string.save))
                }
            },
        )
        "backup_done" -> AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            text = { Text(stringResource(R.string.backup_done)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissDialog() }) { Text("OK") }
            },
        )
        "backup_failed" -> AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            text = { Text(stringResource(R.string.backup_failed)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissDialog() }) { Text("OK") }
            },
        )
        "bad_schema_version" -> AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            text = { Text(stringResource(R.string.restore_newer)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissDialog() }) { Text("OK") }
            },
        )
        else -> AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            text = { Text(stringResource(R.string.restore_invalid)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissDialog() }) { Text("OK") }
            },
        )
    }
}

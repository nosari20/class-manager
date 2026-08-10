package edu.fnosari.classmanager.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import edu.fnosari.classmanager.ui.theme.Palette
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.ui.AppLocale
import edu.fnosari.classmanager.ui.common.pronoteTopBarColors
import edu.fnosari.classmanager.ui.theme.THEME_DARK
import edu.fnosari.classmanager.ui.theme.THEME_LIGHT
import edu.fnosari.classmanager.ui.theme.THEME_SYSTEM
import edu.fnosari.classmanager.appContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Composable
private fun languageLabel(tag: String): String = when (tag) {
    "fr" -> stringResource(R.string.lang_fr)
    "en" -> stringResource(R.string.lang_en)
    else -> stringResource(R.string.system_default)
}

@Composable
private fun themeLabel(pref: String): String = when (pref) {
    THEME_LIGHT -> stringResource(R.string.theme_light)
    THEME_DARK -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.system_default)
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    label: @Composable (String) -> String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onPick(option) })
                        Text(label(option), Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onRestored: () -> Unit, onRooms: () -> Unit) {
    val context = LocalContext.current
    val vm: SettingsViewModel =
        viewModel(factory = SettingsViewModel.factory(context.appContainer))
    val digestTime by vm.digestTime.collectAsStateWithLifecycle()
    val weekARef by vm.weekARef.collectAsStateWithLifecycle()

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) vm.backupTo(context, uri) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.startRestore(context, uri) }
    val calendarPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> if (grants.values.all { it }) vm.syncCalendar() }

    var showLanguage by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }
    val theme by vm.theme.collectAsStateWithLifecycle()
    val accent by vm.accentColor.collectAsStateWithLifecycle()
    val language = vm.currentLanguage(context)

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
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = { Text(languageLabel(language)) },
                modifier = Modifier.clickable { showLanguage = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme)) },
                supportingContent = { Text(themeLabel(theme)) },
                modifier = Modifier.clickable { showTheme = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.accent_color)) },
                supportingContent = { Text(stringResource(R.string.accent_color_help)) },
                trailingContent = {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(accent))
                    )
                },
                modifier = Modifier.clickable { showAccent = true },
            )
            HorizontalDivider()
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
                supportingContent = { Text(stringResource(R.string.backup_help)) },
                modifier = Modifier.clickable { showBackupDialog = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.restore)) },
                modifier = Modifier.clickable {
                    restoreLauncher.launch(arrayOf("application/zip", "*/*"))
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.demo_data)) },
                supportingContent = { Text(stringResource(R.string.demo_data_help)) },
                modifier = Modifier.clickable { vm.demoState = "confirm" },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.calendar_sync)) },
                supportingContent = { Text(stringResource(R.string.calendar_sync_help)) },
                modifier = Modifier.clickable {
                    calendarPermLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR,
                        )
                    )
                },
            )
        }
    }

    if (showLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.language),
            options = AppLocale.TAGS,
            selected = language,
            label = { languageLabel(it) },
            onDismiss = { showLanguage = false },
            onPick = { tag ->
                showLanguage = false
                if (vm.setLanguage(context, tag)) context.findActivity()?.recreate()
            },
        )
    }

    if (showTheme) {
        ChoiceDialog(
            title = stringResource(R.string.theme),
            options = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK),
            selected = theme,
            label = { themeLabel(it) },
            onDismiss = { showTheme = false },
            onPick = { vm.setTheme(it); showTheme = false },
        )
    }

    if (showAccent) {
        AlertDialog(
            onDismissRequest = { showAccent = false },
            title = { Text(stringResource(R.string.accent_color)) },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Palette.PRESETS.forEach { preset ->
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(preset))
                                .clickable { vm.setAccentColor(preset); showAccent = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (preset == accent) {
                                Icon(
                                    Icons.Default.Check,
                                    stringResource(R.string.accent_color),
                                    tint = Color(Palette.onColor(preset)),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAccent = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showBackupDialog) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text(stringResource(R.string.backup)) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_password_help))
                    OutlinedTextField(
                        password, { password = it },
                        label = { Text(stringResource(R.string.backup_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.backupPassword = password.ifBlank { null }
                    val ext = if (password.isBlank()) "zip" else "cmbackup"
                    showBackupDialog = false
                    backupLauncher.launch("classmanager-backup-${LocalDate.now()}.$ext")
                }) { Text(stringResource(R.string.backup)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (vm.restoreState == "password" || vm.restoreState == "bad_password") {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            title = { Text(stringResource(R.string.restore)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            if (vm.restoreState == "bad_password") R.string.bad_password
                            else R.string.restore_password_help
                        ),
                        color = if (vm.restoreState == "bad_password") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedTextField(
                        password, { password = it },
                        label = { Text(stringResource(R.string.backup_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = password.isNotEmpty(),
                    onClick = { vm.submitRestorePassword(password) },
                ) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    when (vm.demoState) {
        "confirm" -> AlertDialog(
            onDismissRequest = { vm.demoState = null },
            text = { Text(stringResource(R.string.demo_data_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.createDemoData() }) {
                    Text(stringResource(R.string.demo_data))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.demoState = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
        "done" -> AlertDialog(
            onDismissRequest = { vm.demoState = null },
            text = { Text(stringResource(R.string.demo_data_done)) },
            confirmButton = {
                TextButton(onClick = { vm.demoState = null }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    vm.calendarSyncResult?.let { result ->
        AlertDialog(
            onDismissRequest = { vm.calendarSyncResult = null },
            text = {
                Text(
                    if (result >= 0) stringResource(R.string.calendar_sync_done, result)
                    else stringResource(R.string.calendar_sync_failed)
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.calendarSyncResult = null }) { Text(stringResource(R.string.ok)) }
            },
        )
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
        null, "password", "bad_password" -> {}
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
                TextButton(onClick = { vm.dismissDialog() }) { Text(stringResource(R.string.ok)) }
            },
        )
        "backup_failed" -> AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            text = { Text(stringResource(R.string.backup_failed)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissDialog() }) { Text(stringResource(R.string.ok)) }
            },
        )
        "bad_schema_version" -> AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            text = { Text(stringResource(R.string.restore_newer)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissDialog() }) { Text(stringResource(R.string.ok)) }
            },
        )
        else -> AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            text = { Text(stringResource(R.string.restore_invalid)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissDialog() }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

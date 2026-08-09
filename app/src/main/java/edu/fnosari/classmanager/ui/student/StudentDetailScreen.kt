package edu.fnosari.classmanager.ui.student

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.ui.common.pronoteTopBarColors
import edu.fnosari.classmanager.appContainer
import androidx.compose.ui.text.style.TextDecoration
import edu.fnosari.classmanager.data.CustomField
import edu.fnosari.classmanager.data.Note
import edu.fnosari.classmanager.data.ReminderType
import edu.fnosari.classmanager.ui.classdetail.StudentAvatar
import edu.fnosari.classmanager.ui.common.SectionLabel
import edu.fnosari.classmanager.ui.common.stripeEdge
import edu.fnosari.classmanager.ui.theme.classColor
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(studentId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: StudentDetailViewModel =
        viewModel(factory = StudentDetailViewModel.factory(context.appContainer, studentId))
    val student by vm.student.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val fields by vm.fields.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()

    var addingNote by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var addingField by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<CustomField?>(null) }
    var addingReminder by remember { mutableStateOf(false) }
    var noTimetable by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pronoteTopBarColors(),
                title = { Text(student?.let { "${it.firstName} ${it.lastName}" } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                student?.let { s ->
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StudentAvatar(s, 96.dp)
                        Text("${s.firstName} ${s.lastName}",
                            style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.custom_fields)) { addingField = true }
            }
            items(fields, key = { "f${it.id}" }) { f ->
                ListItem(
                    overlineContent = { Text(f.key) },
                    headlineContent = { Text(f.value) },
                    trailingContent = {
                        IconButton(onClick = { vm.deleteField(f) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    },
                    modifier = Modifier.padding(0.dp),
                    tonalElevation = 0.dp,
                )
            }

            item {
                SectionHeader(stringResource(R.string.notes)) { addingNote = true }
            }
            items(notes, key = { "n${it.id}" }) { n ->
                val accent = student?.let { classColor(it.classId) }
                    ?: MaterialTheme.colorScheme.primary
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        Modifier.fillMaxWidth().stripeEdge(accent)
                            .padding(start = 24.dp, top = 8.dp, end = 8.dp, bottom = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                DateFormat.getDateInstance().format(Date(n.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.deleteNote(n) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete))
                            }
                        }
                        Text(n.text, Modifier.fillMaxWidth().padding(top = 2.dp))
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.reminders)) { addingReminder = true }
            }
            items(reminders, key = { "r${it.id}" }) { r ->
                ListItem(
                    headlineContent = {
                        Text(
                            r.text,
                            textDecoration = if (r.done) TextDecoration.LineThrough else null,
                        )
                    },
                    supportingContent = {
                        Text(DateFormat.getDateTimeInstance().format(Date(r.dueAt)))
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = r.done,
                                onCheckedChange = { if (!r.done) vm.markDone(r) },
                            )
                            IconButton(onClick = { vm.deleteReminder(r) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete))
                            }
                        }
                    },
                )
            }
        }
    }

    if (addingNote) {
        NoteDialog(null, onDismiss = { addingNote = false }) { t ->
            vm.addNote(t); addingNote = false
        }
    }
    editingNote?.let { n ->
        NoteDialog(n, onDismiss = { editingNote = null }) { t ->
            vm.updateNote(n, t); editingNote = null
        }
    }
    if (addingField) {
        FieldDialog(null, onDismiss = { addingField = false }) { k, v ->
            vm.addField(k, v); addingField = false
        }
    }
    editingField?.let { f ->
        FieldDialog(f, onDismiss = { editingField = null }) { k, v ->
            vm.updateField(f, k, v); editingField = null
        }
    }
    if (addingReminder) {
        ReminderDialog(onDismiss = { addingReminder = false }) { text, type, fixedAt ->
            if (Build.VERSION.SDK_INT >= 33) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            scope.launch {
                val result = vm.addReminder(text, type, fixedAt)
                addingReminder = false
                if (result == ReminderCreateResult.NO_TIMETABLE) noTimetable = true
            }
        }
    }
    if (noTimetable) {
        AlertDialog(
            onDismissRequest = { noTimetable = false },
            text = { Text(stringResource(R.string.no_timetable_message)) },
            confirmButton = {
                TextButton(onClick = { noTimetable = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(
    onDismiss: () -> Unit,
    onSave: (String, ReminderType, Long?) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ReminderType.NEXT_LESSON) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    var hour by remember { mutableStateOf(8) }
    var minute by remember { mutableStateOf(0) }

    fun fixedAt(): Long? = when (type) {
        ReminderType.NEXT_LESSON -> null
        ReminderType.MORNING_DIGEST -> dateMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        ReminderType.FIXED_DATETIME -> dateMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                .atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }

    val needsDate = type != ReminderType.NEXT_LESSON
    val valid = text.isNotBlank() && (!needsDate || fixedAt() != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_reminder)) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth())
                listOf(
                    ReminderType.NEXT_LESSON to R.string.reminder_next_lesson,
                    ReminderType.FIXED_DATETIME to R.string.reminder_fixed,
                    ReminderType.MORNING_DIGEST to R.string.reminder_digest,
                ).forEach { (t, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Text(stringResource(label))
                    }
                }
                if (needsDate) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text(
                            dateMillis?.let {
                                DateFormat.getDateInstance().format(Date(it))
                            } ?: stringResource(R.string.pick_date)
                        )
                    }
                }
                if (type == ReminderType.FIXED_DATETIME) {
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(text, type, fixedAt()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateMillis = state.selectedDateMillis
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
    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour; minute = state.minute; showTimePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionLabel(title)
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun NoteDialog(existing: Note?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(existing?.text ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_note)) },
        text = {
            OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun FieldDialog(
    existing: CustomField?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var key by remember { mutableStateOf(existing?.key ?: "") }
    var value by remember { mutableStateOf(existing?.value ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_field)) },
        text = {
            Column {
                OutlinedTextField(key, { key = it }, label = { Text(stringResource(R.string.field_key)) })
                OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.field_value)) })
            }
        },
        confirmButton = {
            TextButton(enabled = key.isNotBlank(), onClick = { onSave(key, value) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

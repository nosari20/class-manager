package edu.fnosari.classmanager.ui.student

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.appContainer
import edu.fnosari.classmanager.data.CustomField
import edu.fnosari.classmanager.data.Note
import edu.fnosari.classmanager.ui.classdetail.StudentAvatar
import java.text.DateFormat
import java.util.Date

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

    Scaffold(
        topBar = {
            TopAppBar(
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
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
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
                        Text(n.text, Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.reminders), onAdd = null)
            }
            items(reminders, key = { "r${it.id}" }) { r ->
                ListItem(
                    headlineContent = { Text(r.text) },
                    supportingContent = {
                        Text(DateFormat.getDateTimeInstance().format(Date(r.dueAt)))
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
}

@Composable
private fun SectionHeader(title: String, onAdd: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (onAdd != null) {
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, null) }
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

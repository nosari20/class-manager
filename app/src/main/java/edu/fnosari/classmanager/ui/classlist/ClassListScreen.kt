package edu.fnosari.classmanager.ui.classlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import edu.fnosari.classmanager.data.SchoolClass
import androidx.compose.ui.text.font.FontWeight
import edu.fnosari.classmanager.ui.common.AccentPill
import edu.fnosari.classmanager.ui.common.SectionLabel
import edu.fnosari.classmanager.ui.common.stripeEdge
import edu.fnosari.classmanager.ui.theme.classColor
import edu.fnosari.classmanager.ui.timetable.dayName
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClassListScreen(
    onOpenClass: (Long) -> Unit,
    onImportCsv: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm: ClassListViewModel =
        viewModel(factory = ClassListViewModel.factory(LocalContext.current.appContainer))
    val classes by vm.classes.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SchoolClass?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SchoolClass?>(null) }
    var fabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pronoteTopBarColors(),
                title = { Text(stringResource(R.string.classes_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (fabMenu) {
                    SmallFloatingActionButton(onClick = { fabMenu = false; onImportCsv() }) {
                        Icon(Icons.Default.FileUpload, stringResource(R.string.import_csv))
                    }
                    Spacer(Modifier.height(8.dp))
                    SmallFloatingActionButton(onClick = { fabMenu = false; showCreate = true }) {
                        Icon(Icons.Default.Add, stringResource(R.string.new_class))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FloatingActionButton(onClick = { fabMenu = !fabMenu }) {
                    Icon(Icons.Default.Add, null)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(classes, key = { it.schoolClass.id }) { row ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .combinedClickable(
                            onClick = { onOpenClass(row.schoolClass.id) },
                            onLongClick = { editing = row.schoolClass },
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    val accent = classColor(row.schoolClass.id)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .stripeEdge(accent)
                            .padding(start = 28.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.schoolClass.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(Modifier.padding(top = 6.dp)) {
                                AccentPill(
                                    "${row.schoolClass.level} · ${row.studentCount}",
                                    accent,
                                )
                            }
                        }
                        IconButton(onClick = { deleting = row.schoolClass }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        ClassDialog(null, onDismiss = { showCreate = false }) { n, l ->
            vm.create(n, l); showCreate = false
        }
    }
    editing?.let { c ->
        ClassDialog(c, onDismiss = { editing = null }) { n, l -> vm.rename(c, n, l); editing = null }
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.confirm_delete_class)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(c); deleting = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ClassDialog(existing: SchoolClass?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var level by remember { mutableStateOf(existing?.level ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.new_class else R.string.classes_title)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.class_name)) })
                OutlinedTextField(level, { level = it }, label = { Text(stringResource(R.string.class_level)) })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, level) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

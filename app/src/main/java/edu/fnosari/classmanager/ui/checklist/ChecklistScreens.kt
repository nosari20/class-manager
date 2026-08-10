package edu.fnosari.classmanager.ui.checklist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.appContainer
import edu.fnosari.classmanager.data.Checklist
import edu.fnosari.classmanager.ui.common.AccentPill
import edu.fnosari.classmanager.ui.common.pronoteTopBarColors
import edu.fnosari.classmanager.ui.common.stripeEdge
import edu.fnosari.classmanager.ui.theme.classColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DUE_RE = Regex("\\d{4}-\\d{2}-\\d{2}")

/** Human date for a stored "yyyy-MM-dd", or the raw value if it will not parse. */
@Composable
private fun dueLabel(due: String): String = runCatching {
    LocalDate.parse(due).format(DateTimeFormatter.ofPattern("d MMM"))
}.getOrDefault(due)

/** The class's checklists, with how far along each one is. Shown as a tab of the class page. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChecklistsTab(classId: Long, onOpen: (Long) -> Unit, creating: Boolean, onCreatingChange: (Boolean) -> Unit) {
    val vm: ChecklistsViewModel =
        viewModel(
            key = "checklists-$classId",
            factory = ChecklistsViewModel.factory(LocalContext.current.appContainer, classId),
        )
    val rows by vm.rows.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Checklist?>(null) }
    var deleting by remember { mutableStateOf<Checklist?>(null) }
    val accent = classColor(classId)

    if (rows.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                stringResource(R.string.no_checklists),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(rows, key = { it.checklist.id }) { row ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .combinedClickable(
                        onClick = { onOpen(row.checklist.id) },
                        onLongClick = { editing = row.checklist },
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    Modifier.fillMaxWidth().stripeEdge(accent)
                        .padding(start = 26.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.checklist.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val complete = row.total > 0 && row.done == row.total
                            AccentPill(
                                if (complete) stringResource(R.string.checklist_complete)
                                else stringResource(R.string.checklist_missing, row.total - row.done),
                                if (complete) accent else MaterialTheme.colorScheme.error,
                            )
                            row.checklist.dueDate?.let {
                                Text(
                                    dueLabel(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { if (row.total == 0) 0f else row.done.toFloat() / row.total },
                            color = accent,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text(
                            stringResource(R.string.checklist_progress, row.done, row.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    IconButton(onClick = { deleting = row.checklist }) {
                        Icon(
                            Icons.Default.Delete, stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        ChecklistDialog(null, onDismiss = { onCreatingChange(false) }) { title, due ->
            vm.create(title, due)
            onCreatingChange(false)
        }
    }
    editing?.let { c ->
        ChecklistDialog(c, onDismiss = { editing = null }) { title, due ->
            vm.rename(c, title, due)
            editing = null
        }
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.confirm_delete_checklist)) },
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
private fun ChecklistDialog(
    existing: Checklist?,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var due by remember { mutableStateOf(existing?.dueDate ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) R.string.new_checklist else R.string.rename))
        },
        text = {
            Column {
                OutlinedTextField(
                    title, { title = it },
                    label = { Text(stringResource(R.string.checklist_title)) },
                    supportingText = { Text(stringResource(R.string.checklist_title_help)) },
                )
                OutlinedTextField(
                    due, { due = it },
                    label = { Text(stringResource(R.string.checklist_due)) },
                    isError = due.isNotBlank() && !DUE_RE.matches(due),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && (due.isBlank() || DUE_RE.matches(due)),
                onClick = { onSave(title, due.ifBlank { null }) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Ticking off a class, one student at a time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistDetailScreen(checklistId: Long, onBack: () -> Unit) {
    val vm: ChecklistDetailViewModel =
        viewModel(
            key = "checklist-$checklistId",
            factory = ChecklistDetailViewModel.factory(LocalContext.current.appContainer, checklistId),
        )
    val state by vm.state.collectAsStateWithLifecycle()
    val accent = classColor(state.checklist?.classId ?: 0L)
    val done = state.doneIds.size
    val total = state.students.size

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pronoteTopBarColors(),
                title = { Text(state.checklist?.title ?: "") },
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
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        state.schoolClass?.name.orEmpty() +
                            (state.checklist?.dueDate?.let { "  ·  ${dueLabel(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.checklist_progress, done, total),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        color = accent,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { vm.markAll() }) {
                            Text(stringResource(R.string.check_all))
                        }
                        TextButton(onClick = { vm.clearAll() }) {
                            Text(stringResource(R.string.uncheck_all))
                        }
                    }
                }
            }
            items(state.students, key = { it.id }) { s ->
                val isDone = s.id in state.doneIds
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggle(s.id, !isDone) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isDone, onCheckedChange = { vm.toggle(s.id, it) })
                    Text(
                        "${s.firstName} ${s.lastName}",
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

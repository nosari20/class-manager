package edu.fnosari.classmanager.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import edu.fnosari.classmanager.ui.common.pronoteTopBarColors
import edu.fnosari.classmanager.appContainer
import edu.fnosari.classmanager.data.Grouping
import edu.fnosari.classmanager.data.Student
import edu.fnosari.classmanager.domain.SplitMode
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(classId: Long, onBack: () -> Unit) {
    val vm: GroupsViewModel =
        viewModel(factory = GroupsViewModel.factory(LocalContext.current.appContainer, classId))
    val students by vm.students.collectAsStateWithLifecycle()
    val constraints by vm.constraints.collectAsStateWithLifecycle()
    val groupings by vm.groupings.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val infeasible by vm.infeasible.collectAsStateWithLifecycle()
    val viewing by vm.viewing.collectAsStateWithLifecycle()

    var addingConstraint by remember { mutableStateOf(false) }
    var savingDraft by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Grouping?>(null) }
    var deletingGrouping by remember { mutableStateOf<Grouping?>(null) }

    val byId = students.associateBy { it.id }
    fun name(id: Long) = byId[id]?.let { "${it.firstName} ${it.lastName}" } ?: "?"

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pronoteTopBarColors(),
                title = { Text(stringResource(R.string.groups)) },
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
            // --- Constraints ---
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.constraints), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { addingConstraint = true }) { Icon(Icons.Default.Add, null) }
                }
            }
            items(constraints, key = { "c${it.id}" }) { c ->
                ListItem(
                    headlineContent = { Text("${name(c.studentAId)} ✕ ${name(c.studentBId)}") },
                    trailingContent = {
                        IconButton(onClick = { vm.removeConstraint(c) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    },
                )
            }

            // --- Generation controls ---
            item {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = vm.splitMode == SplitMode.GROUPS_OF_N,
                            onClick = { vm.splitMode = SplitMode.GROUPS_OF_N },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text(stringResource(R.string.groups_of_n)) }
                        SegmentedButton(
                            selected = vm.splitMode == SplitMode.N_GROUPS,
                            onClick = { vm.splitMode = SplitMode.N_GROUPS },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text(stringResource(R.string.n_groups)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (vm.n > 1) vm.n-- }) {
                            Icon(Icons.Default.Remove, null)
                        }
                        Text("${vm.n}", style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { if (vm.n < 15) vm.n++ }) {
                            Icon(Icons.Default.Add, null)
                        }
                        Checkbox(checked = vm.excludeAbsent, onCheckedChange = { vm.excludeAbsent = it })
                        Text(stringResource(R.string.exclude_absent))
                    }
                    Button(onClick = { vm.generate() }, Modifier.fillMaxWidth()) {
                        Text(stringResource(
                            if (draft == null) R.string.generate else R.string.reshuffle
                        ))
                    }
                }
            }

            // --- Infeasible warning ---
            infeasible?.let { pairs ->
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.infeasible_message),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            pairs.forEach { (a, b) ->
                                Text(
                                    "• ${name(a)} ✕ ${name(b)}",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            // --- Draft result ---
            draft?.let { d ->
                itemsIndexedGroups(d.groups, d.violations, ::name,
                    movable = true,
                    onMove = { s, to -> vm.moveStudent(s, to) })
                item {
                    Button(onClick = { savingDraft = true }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.save))
                    }
                }
            }

            // --- Viewing saved grouping ---
            viewing?.let { (g, groups) ->
                item {
                    Text(
                        g.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                itemsIndexedGroups(groups, emptySet(), ::name, movable = false, onMove = { _, _ -> })
            }

            // --- Saved groupings ---
            item {
                Text(
                    stringResource(R.string.saved_groupings),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            items(groupings, key = { "g${it.id}" }) { g ->
                ListItem(
                    headlineContent = { Text(g.name) },
                    supportingContent = {
                        Text(DateFormat.getDateInstance().format(Date(g.createdAt)))
                    },
                    trailingContent = {
                        IconButton(onClick = { deletingGrouping = g }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    },
                    modifier = Modifier.padding(0.dp),
                    tonalElevation = 1.dp,
                )
                Row {
                    TextButton(onClick = { vm.viewGrouping(g) }) { Text(stringResource(R.string.view)) }
                    TextButton(onClick = { renaming = g }) { Text(stringResource(R.string.rename)) }
                }
            }
        }
    }

    if (addingConstraint) {
        ConstraintDialog(students, onDismiss = { addingConstraint = false }) { a, b ->
            vm.addConstraint(a, b); addingConstraint = false
        }
    }
    if (savingDraft) {
        NameDialog(initial = "", title = stringResource(R.string.save),
            onDismiss = { savingDraft = false }) { name ->
            vm.saveDraft(name); savingDraft = false
        }
    }
    renaming?.let { g ->
        NameDialog(initial = g.name, title = stringResource(R.string.rename),
            onDismiss = { renaming = null }) { name ->
            vm.renameGrouping(g, name); renaming = null
        }
    }
    deletingGrouping?.let { g ->
        AlertDialog(
            onDismissRequest = { deletingGrouping = null },
            text = { Text(stringResource(R.string.confirm_delete_grouping)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteGrouping(g); deletingGrouping = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingGrouping = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedGroups(
    groups: List<List<Student>>,
    violations: Set<Pair<Long, Long>>,
    name: (Long) -> String,
    movable: Boolean,
    onMove: (Student, Int) -> Unit,
) {
    groups.forEachIndexed { i, members ->
        item(key = "grp$i-${members.hashCode()}") {
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.group_n, i + 1),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    members.forEach { s ->
                        val violating = violations.any { (a, b) ->
                            (a == s.id || b == s.id) &&
                                members.any { m -> m.id != s.id && (m.id == a || m.id == b) }
                        }
                        MemberRow(s, i, groups.size, movable, violating, onMove)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    s: Student,
    groupIndex: Int,
    groupCount: Int,
    movable: Boolean,
    violating: Boolean,
    onMove: (Student, Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${s.firstName} ${s.lastName}",
                color = if (violating) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (movable) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            (0 until groupCount).filter { it != groupIndex }.forEach { g ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.group_n, g + 1)) },
                    onClick = { menu = false; onMove(s, g) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConstraintDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit,
) {
    var a by remember { mutableStateOf<Student?>(null) }
    var b by remember { mutableStateOf<Student?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_constraint)) },
        text = {
            Column {
                StudentPicker(students, a, { a = it }, stringResource(R.string.first_student))
                StudentPicker(students, b, { b = it }, stringResource(R.string.second_student))
            }
        },
        confirmButton = {
            TextButton(
                enabled = a != null && b != null && a?.id != b?.id,
                onClick = { onSave(a!!.id, b!!.id) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentPicker(
    students: List<Student>,
    selected: Student?,
    onSelect: (Student) -> Unit,
    label: String,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { "${it.firstName} ${it.lastName}" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            students.forEach { s ->
                DropdownMenuItem(
                    text = { Text("${s.firstName} ${s.lastName}") },
                    onClick = { onSelect(s); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    initial: String,
    title: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it },
                label = { Text(stringResource(R.string.grouping_name)) })
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

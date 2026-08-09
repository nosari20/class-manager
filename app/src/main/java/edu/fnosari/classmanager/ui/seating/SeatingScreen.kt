package edu.fnosari.classmanager.ui.seating

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.ui.common.pronoteTopBarColors
import edu.fnosari.classmanager.appContainer
import edu.fnosari.classmanager.data.Room
import edu.fnosari.classmanager.data.SeatingPlan
import edu.fnosari.classmanager.data.Student
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.font.FontWeight
import edu.fnosari.classmanager.ui.common.AccentPill
import edu.fnosari.classmanager.ui.common.stripeEdge
import edu.fnosari.classmanager.ui.rooms.RoomCanvas
import edu.fnosari.classmanager.ui.rooms.SeatSlots
import edu.fnosari.classmanager.ui.theme.classColor
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatingPlansScreen(
    classId: Long,
    onOpenPlan: (Long) -> Unit,
    onManageRooms: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: SeatingPlansViewModel =
        viewModel(factory = SeatingPlansViewModel.factory(LocalContext.current.appContainer, classId))
    val plans by vm.plans.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SeatingPlan?>(null) }
    var deleting by remember { mutableStateOf<SeatingPlan?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pronoteTopBarColors(),
                title = { Text(stringResource(R.string.seating_plans)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = onManageRooms) { Text(stringResource(R.string.rooms)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.new_plan))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(plans, key = { it.id }) { p ->
                val roomName = rooms.firstOrNull { it.id == p.roomId }?.name
                val accent = classColor(classId)
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onOpenPlan(p.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .stripeEdge(accent)
                            .padding(start = 26.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(Modifier.padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                roomName?.let {
                                    AccentPill(it, accent, Modifier.padding(end = 8.dp))
                                }
                                Text(
                                    DateFormat.getDateInstance().format(Date(p.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { deleting = p }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CreatePlanDialog(
            rooms = rooms,
            onDismiss = { creating = false },
            onManageRooms = { creating = false; onManageRooms() },
        ) { name, roomId ->
            creating = false
            scope.launch { onOpenPlan(vm.create(name, roomId)) }
        }
    }
    renaming?.let { p ->
        NameDialog(p.name, onDismiss = { renaming = null }) { vm.rename(p, it); renaming = null }
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.confirm_delete_plan)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(p); deleting = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePlanDialog(
    rooms: List<Room>,
    onDismiss: () -> Unit,
    onManageRooms: () -> Unit,
    onCreate: (String, Long) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var room by remember { mutableStateOf<Room?>(null) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_plan)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it },
                    label = { Text(stringResource(R.string.grouping_name)) })
                if (rooms.isEmpty()) {
                    TextButton(onClick = onManageRooms) {
                        Text(stringResource(R.string.no_rooms_yet))
                    }
                } else {
                    ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
                        OutlinedTextField(
                            value = room?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.room_name)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            rooms.forEach { r ->
                                DropdownMenuItem(text = { Text(r.name) },
                                    onClick = { room = r; menu = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && room != null,
                onClick = { onCreate(name, room!!.id) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatingScreen(planId: Long, onBack: () -> Unit) {
    val vm: SeatingViewModel = seatingViewModel(planId)
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pronoteTopBarColors(),
                title = { Text(state.plan?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        SeatingPlanBody(planId, Modifier.padding(padding))
    }
}

@Composable
private fun seatingViewModel(planId: Long): SeatingViewModel = viewModel(
    key = "seating-$planId",
    factory = SeatingViewModel.factory(LocalContext.current.appContainer, planId),
)

/** The plan itself — canvas plus unassigned students. Shared by the standalone screen and the class tab. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeatingPlanBody(planId: Long, modifier: Modifier = Modifier) {
    val vm: SeatingViewModel = seatingViewModel(planId)
    val state by vm.state.collectAsStateWithLifecycle()
    var pickingFor by remember { mutableStateOf<Pair<Long, Int>?>(null) }   // (deskId, seatIndex) needing a student
    var occupiedTap by remember { mutableStateOf<Pair<Long, Int>?>(null) }  // occupied (deskId, seatIndex) tapped

    Box(modifier) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                state.room?.name ?: "",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            RoomCanvas(
                desks = state.desks,
                deskModifier = { d, _, _ ->
                    val violating = d.id in state.violatingDeskIds
                    Modifier.border(
                        width = if (violating) 3.dp else 1.dp,
                        color = if (violating) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp),
                    )
                },
                deskContent = { d ->
                    SeatSlots(d, Modifier.fillMaxSize()) { seatIndex ->
                        val student = state.seats[d.id]?.get(seatIndex)
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    if (student != null) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable {
                                    if (student != null) occupiedTap = d.id to seatIndex
                                    else pickingFor = d.id to seatIndex
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                student?.let {
                                    "${it.firstName}\n${it.lastName.take(1)}."
                                } ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                },
            )
            Text(
                stringResource(R.string.unassigned_students, state.unassigned.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.unassigned.forEach { s ->
                    FilterChip(selected = false, onClick = {},
                        label = { Text("${s.firstName} ${s.lastName.take(1)}.") })
                }
            }
        }
    }

    pickingFor?.let { (deskId, seatIndex) ->
        StudentPickDialog(
            students = state.unassigned +
                state.seats.values.flatMap { it.values }.sortedBy { it.lastName },
            onDismiss = { pickingFor = null },
        ) { s ->
            vm.assign(deskId, seatIndex, s.id)
            pickingFor = null
        }
    }
    occupiedTap?.let { (deskId, seatIndex) ->
        val student = state.seats[deskId]?.get(seatIndex)
        AlertDialog(
            onDismissRequest = { occupiedTap = null },
            title = { Text(student?.let { "${it.firstName} ${it.lastName}" } ?: "") },
            confirmButton = {
                TextButton(onClick = { vm.unassign(deskId, seatIndex); occupiedTap = null }) {
                    Text(stringResource(R.string.remove_from_seat))
                }
            },
            dismissButton = {
                TextButton(onClick = { occupiedTap = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun StudentPickDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onPick: (Student) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_student)) },
        text = {
            LazyColumn {
                items(students, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text("${s.firstName} ${s.lastName}") },
                        modifier = Modifier.clickable { onPick(s) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

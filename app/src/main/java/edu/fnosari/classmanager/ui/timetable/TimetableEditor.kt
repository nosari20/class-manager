package edu.fnosari.classmanager.ui.timetable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.data.Room
import edu.fnosari.classmanager.data.TimetableSlot
import edu.fnosari.classmanager.data.WeekParityTag

private val TIME_RE = Regex("([01]\\d|2[0-3]):[0-5]\\d")

@Composable
fun dayName(iso: Int): String {
    val ids = listOf(R.string.mon, R.string.tue, R.string.wed, R.string.thu,
        R.string.fri, R.string.sat, R.string.sun)
    return stringResource(ids[iso - 1])
}

@Composable
fun TimetableEditor(
    slots: List<TimetableSlot>,
    rooms: List<Room>,
    onAdd: (day: Int, start: String, end: String, parity: WeekParityTag, roomId: Long?) -> Unit,
    onDelete: (TimetableSlot) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    val roomNames = rooms.associate { it.id to it.name }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(Modifier.weight(1f)) {
            items(slots, key = { it.id }) { s ->
                ListItem(
                    headlineContent = { Text("${dayName(s.dayOfWeek)} ${s.startTime}–${s.endTime}") },
                    supportingContent = {
                        val parts = mutableListOf<String>()
                        s.roomId?.let { roomNames[it] }?.let { parts.add(it) }
                        if (s.weekParity != WeekParityTag.BOTH) {
                            parts.add(stringResource(R.string.week_parity_label, s.weekParity.name))
                        }
                        if (parts.isNotEmpty()) Text(parts.joinToString(" — "))
                    },
                    trailingContent = {
                        IconButton(onClick = { onDelete(s) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    },
                )
            }
        }
        Button(onClick = { showAdd = true }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_slot))
        }
    }
    if (showAdd) {
        SlotDialog(rooms, onDismiss = { showAdd = false }) { d, st, en, p, r ->
            onAdd(d, st, en, p, r); showAdd = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotDialog(
    rooms: List<Room>,
    onDismiss: () -> Unit,
    onSave: (Int, String, String, WeekParityTag, Long?) -> Unit,
) {
    var day by remember { mutableStateOf(1) }
    var dayMenu by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf("08:00") }
    var end by remember { mutableStateOf("09:00") }
    var parity by remember { mutableStateOf(WeekParityTag.BOTH) }
    var room by remember { mutableStateOf<Room?>(null) }
    var roomMenu by remember { mutableStateOf(false) }
    val valid = TIME_RE.matches(start) && TIME_RE.matches(end) && start < end

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_slot)) },
        text = {
            Column {
                ExposedDropdownMenuBox(expanded = dayMenu, onExpandedChange = { dayMenu = it }) {
                    OutlinedTextField(
                        value = dayName(day),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier.menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                        (1..7).forEach { d ->
                            DropdownMenuItem(text = { Text(dayName(d)) },
                                onClick = { day = d; dayMenu = false })
                        }
                    }
                }
                OutlinedTextField(start, { start = it },
                    label = { Text(stringResource(R.string.start_time)) })
                OutlinedTextField(end, { end = it },
                    label = { Text(stringResource(R.string.end_time)) })
                if (rooms.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = roomMenu, onExpandedChange = { roomMenu = it }) {
                        OutlinedTextField(
                            value = room?.name ?: stringResource(R.string.no_room),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.room_name)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = roomMenu, onDismissRequest = { roomMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.no_room)) },
                                onClick = { room = null; roomMenu = false },
                            )
                            rooms.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(r.name) },
                                    onClick = { room = r; roomMenu = false },
                                )
                            }
                        }
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp)) {
                    WeekParityTag.entries.forEachIndexed { i, tag ->
                        SegmentedButton(
                            selected = parity == tag,
                            onClick = { parity = tag },
                            shape = SegmentedButtonDefaults.itemShape(i, WeekParityTag.entries.size),
                        ) { Text(tag.name) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(day, start, end, parity, room?.id) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

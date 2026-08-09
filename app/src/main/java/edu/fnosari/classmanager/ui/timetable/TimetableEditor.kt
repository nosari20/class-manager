package edu.fnosari.classmanager.ui.timetable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.fnosari.classmanager.ui.common.AccentPill
import edu.fnosari.classmanager.ui.common.SectionLabel
import edu.fnosari.classmanager.ui.common.stripeEdge
import edu.fnosari.classmanager.ui.theme.classColor
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.data.OneOffSlot
import edu.fnosari.classmanager.data.Room
import edu.fnosari.classmanager.data.SlotCancellation
import edu.fnosari.classmanager.data.TimetableSlot
import edu.fnosari.classmanager.data.WeekParityTag
import edu.fnosari.classmanager.domain.occurrencesOn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    cancellations: List<SlotCancellation>,
    oneOffs: List<OneOffSlot>,
    weekARef: String?,
    onAdd: (day: Int, start: String, end: String, parity: WeekParityTag, roomId: Long?) -> Unit,
    onDelete: (TimetableSlot) -> Unit,
    onCancelOccurrence: (slotId: Long, date: String) -> Unit,
    onUncancel: (SlotCancellation) -> Unit,
    onAddOneOff: (date: String, start: String, end: String, roomId: Long?) -> Unit,
    onDeleteOneOff: (OneOffSlot) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var showAddOneOff by remember { mutableStateOf(false) }
    val roomNames = rooms.associate { it.id to it.name }
    val today = remember { LocalDate.now() }
    var weekStart by remember { mutableStateOf(today.minusDays((today.dayOfWeek.value - 1).toLong())) }
    val ref = weekARef?.let(LocalDate::parse)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // --- Week browser ---
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
            }
            SectionLabel(
                stringResource(
                    R.string.week_of,
                    weekStart.format(DateTimeFormatter.ofPattern("d MMM")),
                ),
                Modifier.weight(1f),
            )
            IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            for (dayOffset in 0..6L) {
                val date = weekStart.plusDays(dayOffset)
                val occ = occurrencesOn(date, slots, cancellations, oneOffs, ref)
                if (occ.isEmpty()) continue
                item(key = "day$date") {
                    Text(
                        "${dayName(date.dayOfWeek.value)} ${date.dayOfMonth}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(occ, key = { "occ-$date-${it.slotId ?: "o${it.oneOffId}"}-${it.startTime}" }) { o ->
                    val accent = classColor(o.classId)
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().stripeEdge(accent)
                                .padding(start = 24.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${o.startTime}–${o.endTime}" +
                                        if (o.oneOffId != null) "  •  ${stringResource(R.string.one_off)}" else "",
                                    fontWeight = FontWeight.Bold,
                                )
                                o.roomId?.let { roomNames[it] }?.let {
                                    AccentPill(it, accent, Modifier.padding(top = 4.dp))
                                }
                            }
                            IconButton(onClick = {
                                if (o.slotId != null) onCancelOccurrence(o.slotId, date.toString())
                                else oneOffs.firstOrNull { it.id == o.oneOffId }?.let(onDeleteOneOff)
                            }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            // cancelled occurrences of this week, restorable
            val weekDates = (0..6L).map { weekStart.plusDays(it).toString() }.toSet()
            val cancelledThisWeek = cancellations.filter { it.date in weekDates }
            if (cancelledThisWeek.isNotEmpty()) {
                item(key = "cancelled") {
                    Column(Modifier.padding(top = 8.dp)) {
                        cancelledThisWeek.forEach { c ->
                            val s = slots.firstOrNull { it.id == c.slotId } ?: return@forEach
                            TextButton(onClick = { onUncancel(c) }) {
                                Text(stringResource(R.string.restore_occurrence,
                                    "${c.date} ${s.startTime}"))
                            }
                        }
                    }
                }
            }
            item(key = "addOneOff") {
                TextButton(onClick = { showAddOneOff = true }) {
                    Text(stringResource(R.string.add_one_off))
                }
            }

            // --- Recurring template ---
            item(key = "recurringHeader") {
                SectionLabel(stringResource(R.string.recurring), Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            items(slots, key = { it.id }) { s ->
                val accent = classColor(s.classId)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(72.dp)) {
                        Text(
                            s.startTime,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            s.endTime,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Card(
                        Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .stripeEdge(accent)
                                .padding(start = 28.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    dayName(s.dayOfWeek) +
                                        if (s.weekParity != WeekParityTag.BOTH) " · ${s.weekParity.name}" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                s.roomId?.let { roomNames[it] }?.let {
                                    AccentPill(it, accent, Modifier.padding(top = 6.dp))
                                }
                            }
                            IconButton(onClick = { onDelete(s) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
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
    if (showAddOneOff) {
        OneOffDialog(rooms, weekStart, onDismiss = { showAddOneOff = false }) { date, st, en, r ->
            onAddOneOff(date, st, en, r); showAddOneOff = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneOffDialog(
    rooms: List<Room>,
    weekStart: LocalDate,
    onDismiss: () -> Unit,
    onSave: (date: String, start: String, end: String, roomId: Long?) -> Unit,
) {
    var dayOffset by remember { mutableStateOf(0L) }
    var dayMenu by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf("08:00") }
    var end by remember { mutableStateOf("09:00") }
    var room by remember { mutableStateOf<Room?>(null) }
    var roomMenu by remember { mutableStateOf(false) }
    val valid = TIME_RE.matches(start) && TIME_RE.matches(end) && start < end
    val date = weekStart.plusDays(dayOffset)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_one_off)) },
        text = {
            Column {
                ExposedDropdownMenuBox(expanded = dayMenu, onExpandedChange = { dayMenu = it }) {
                    OutlinedTextField(
                        value = "${dayName(date.dayOfWeek.value)} ${date.dayOfMonth}",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier.menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                        (0..6L).forEach { off ->
                            val d = weekStart.plusDays(off)
                            DropdownMenuItem(
                                text = { Text("${dayName(d.dayOfWeek.value)} ${d.dayOfMonth}") },
                                onClick = { dayOffset = off; dayMenu = false },
                            )
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
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(date.toString(), start, end, room?.id) }) {
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

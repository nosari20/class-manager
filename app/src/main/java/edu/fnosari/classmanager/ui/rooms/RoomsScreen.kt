package edu.fnosari.classmanager.ui.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.appContainer
import edu.fnosari.classmanager.data.Desk
import edu.fnosari.classmanager.data.Room
import kotlin.math.roundToInt

val DESK_SIZE = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(onOpenRoom: (Long) -> Unit, onBack: () -> Unit) {
    val vm: RoomsViewModel =
        viewModel(factory = RoomsViewModel.factory(LocalContext.current.appContainer))
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Room?>(null) }
    var deleting by remember { mutableStateOf<Room?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rooms)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.new_room))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(rooms, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text(r.name) },
                    trailingContent = {
                        IconButton(onClick = { deleting = r }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    },
                    modifier = Modifier.pointerInput(r.id) {
                        detectTapGestures(
                            onTap = { onOpenRoom(r.id) },
                            onLongPress = { renaming = r },
                        )
                    },
                )
            }
        }
    }

    if (creating) {
        RoomNameDialog(null, onDismiss = { creating = false }) { vm.create(it); creating = false }
    }
    renaming?.let { r ->
        RoomNameDialog(r.name, onDismiss = { renaming = null }) { vm.rename(r, it); renaming = null }
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.confirm_delete_room)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(r); deleting = null }) {
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
private fun RoomNameDialog(initial: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_room else R.string.rename)) },
        text = {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.room_name)) })
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

/** Shared canvas: renders board + desks; used by editor and seating screens. */
@Composable
fun RoomCanvas(
    desks: List<Desk>,
    modifier: Modifier = Modifier,
    onTapEmpty: ((Float, Float) -> Unit)? = null,
    deskContent: @Composable (Desk) -> Unit,
    deskModifier: @Composable (Desk, canvasW: Float, canvasH: Float) -> Modifier = { _, _, _ -> Modifier },
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val density = LocalDensity.current
        val canvasW = with(density) { maxWidth.toPx() }
        val canvasH = with(density) { maxHeight.toPx() }
        val deskPx = with(density) { DESK_SIZE.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(onTapEmpty != null) {
                    if (onTapEmpty != null) {
                        detectTapGestures { pos ->
                            onTapEmpty(pos.x / canvasW, pos.y / canvasH)
                        }
                    }
                },
        )
        // board marker
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .fillMaxWidth(0.5f)
                .height(10.dp)
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
        )
        Text(
            stringResource(R.string.board),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
        )
        desks.forEach { d ->
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (d.x * canvasW - deskPx / 2).roundToInt(),
                            (d.y * canvasH - deskPx / 2).roundToInt(),
                        )
                    }
                    .size(DESK_SIZE)
                    .then(deskModifier(d, canvasW, canvasH)),
            ) {
                deskContent(d)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomEditorScreen(roomId: Long, onBack: () -> Unit) {
    val vm: RoomEditorViewModel =
        viewModel(factory = RoomEditorViewModel.factory(LocalContext.current.appContainer, roomId))
    val desks by vm.desks.collectAsStateWithLifecycle()
    val room by vm.room.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Desk?>(null) }
    // live drag offsets in px, per desk
    val dragOffsets = remember { mutableStateOf(mapOf<Long, Offset>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(room?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                stringResource(R.string.room_editor_help),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            RoomCanvas(
                desks = desks,
                onTapEmpty = { x, y -> vm.addDesk(x, y) },
                deskModifier = { d, cw, ch ->
                    val extra = dragOffsets.value[d.id] ?: Offset.Zero
                    Modifier
                        .offset { IntOffset(extra.x.roundToInt(), extra.y.roundToInt()) }
                        .pointerInput(d.id) {
                            detectDragGestures(
                                onDrag = { change, amount ->
                                    change.consume()
                                    val cur = dragOffsets.value[d.id] ?: Offset.Zero
                                    dragOffsets.value = dragOffsets.value + (d.id to cur + amount)
                                },
                                onDragEnd = {
                                    val off = dragOffsets.value[d.id] ?: Offset.Zero
                                    vm.moveDesk(d, d.x + off.x / cw, d.y + off.y / ch)
                                    dragOffsets.value = dragOffsets.value - d.id
                                },
                                onDragCancel = { dragOffsets.value = dragOffsets.value - d.id },
                            )
                        }
                        .pointerInput(d.id) {
                            detectTapGestures(onLongPress = { deleting = d })
                        }
                },
                deskContent = { d ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${desks.indexOfFirst { it.id == d.id } + 1}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                },
            )
        }
    }

    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.confirm_delete_desk)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteDesk(d); deleting = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

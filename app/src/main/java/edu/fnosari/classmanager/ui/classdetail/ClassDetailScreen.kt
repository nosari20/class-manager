package edu.fnosari.classmanager.ui.classdetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.ui.common.pronoteTopBarColors
import edu.fnosari.classmanager.appContainer
import edu.fnosari.classmanager.data.Student
import edu.fnosari.classmanager.ui.common.PhotoUtil
import edu.fnosari.classmanager.ui.timetable.TimetableEditor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClassDetailScreen(
    classId: Long,
    onOpenStudent: (Long) -> Unit,
    onPicker: () -> Unit,
    onGroups: () -> Unit,
    onSeating: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vm: ClassDetailViewModel =
        viewModel(factory = ClassDetailViewModel.factory(context.appContainer, classId))
    val schoolClass by vm.schoolClass.collectAsStateWithLifecycle()
    val students by vm.students.collectAsStateWithLifecycle()
    val slots by vm.slots.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Student?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pronoteTopBarColors(),
                title = { Text(schoolClass?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onPicker) {
                        Icon(Icons.Default.Casino, stringResource(R.string.random_picker))
                    }
                    IconButton(onClick = onGroups) {
                        Icon(Icons.Default.Groups, stringResource(R.string.groups))
                    }
                    IconButton(onClick = onSeating) {
                        Icon(Icons.Default.Chair, stringResource(R.string.seating_plans))
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.PersonAdd, stringResource(R.string.add_student))
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.students)) })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.timetable)) })
            }
            if (tab == 0) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(students, key = { it.id }) { s ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.combinedClickable(
                                onClick = { onOpenStudent(s.id) },
                                onLongClick = { editing = s },
                            ),
                        ) {
                            StudentAvatar(s, 72.dp)
                            Text(
                                "${s.firstName}\n${s.lastName}",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                val rooms by vm.rooms.collectAsStateWithLifecycle()
                TimetableEditor(slots, rooms, onAdd = vm::addSlot, onDelete = vm::deleteSlot)
            }
        }
    }

    if (showAdd) {
        StudentDialog(null, onDismiss = { showAdd = false },
            onSave = { l, f, uri -> vm.addStudent(l, f, uri, context); showAdd = false },
            onDelete = null)
    }
    editing?.let { s ->
        StudentDialog(s, onDismiss = { editing = null },
            onSave = { l, f, uri -> vm.updateStudent(s, l, f, uri, context); editing = null },
            onDelete = { vm.deleteStudent(s); editing = null })
    }
}

@Composable
fun StudentAvatar(s: Student, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    if (s.photoPath != null) {
        AsyncImage(
            model = PhotoUtil.photoFile(context, s.photoPath),
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
private fun StudentDialog(
    existing: Student?,
    onDismiss: () -> Unit,
    onSave: (String, String, Uri?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var last by remember { mutableStateOf(existing?.lastName ?: "") }
    var first by remember { mutableStateOf(existing?.firstName ?: "") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCapture by remember { mutableStateOf<Uri?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri }
    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) photoUri = pendingCapture }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) R.string.add_student else R.string.edit_student))
        },
        text = {
            Column {
                OutlinedTextField(last, { last = it },
                    label = { Text(stringResource(R.string.last_name)) })
                OutlinedTextField(first, { first = it },
                    label = { Text(stringResource(R.string.first_name)) })
                Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pickLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Icon(Icons.Default.PhotoLibrary, null)
                    }
                    OutlinedButton(onClick = {
                        val (uri, _) = PhotoUtil.newCaptureUri(context)
                        pendingCapture = uri
                        captureLauncher.launch(uri)
                    }) {
                        Icon(Icons.Default.PhotoCamera, null)
                    }
                    if (photoUri != null) {
                        AsyncImage(model = photoUri, contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape))
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }, Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = last.isNotBlank() || first.isNotBlank(),
                onClick = { onSave(last, first, photoUri) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.confirm_delete_student)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

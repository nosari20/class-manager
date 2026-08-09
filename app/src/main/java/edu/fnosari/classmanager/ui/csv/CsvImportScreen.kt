package edu.fnosari.classmanager.ui.csv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.appContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(onDone: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: CsvImportViewModel =
        viewModel(factory = CsvImportViewModel.factory(context.appContainer))

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.load(context, uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_csv)) },
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
            val table = vm.table
            if (table == null) {
                item {
                    Button(
                        onClick = {
                            pickLauncher.launch(
                                arrayOf("text/*", "text/csv", "application/csv",
                                    "application/vnd.ms-excel", "*/*")
                            )
                        },
                        Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.FileUpload, null)
                        Text(stringResource(R.string.pick_csv_file), Modifier.padding(start = 8.dp))
                    }
                    vm.error?.let { err ->
                        Text(
                            stringResource(
                                if (err == "empty") R.string.csv_error_empty else R.string.csv_error_read
                            ),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                item {
                    Column {
                        ColumnPicker(
                            headers = table.headers,
                            selected = vm.lastNameCol,
                            onSelect = { vm.lastNameCol = it },
                            label = stringResource(R.string.column_last_name),
                        )
                        ColumnPicker(
                            headers = table.headers,
                            selected = vm.firstNameCol,
                            onSelect = { vm.firstNameCol = it },
                            label = stringResource(R.string.column_first_name),
                        )
                        if (table.skippedLines.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.skipped_lines,
                                    table.skippedLines.joinToString()
                                ),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
                items(vm.preview().take(10)) { (last, first) ->
                    ListItem(headlineContent = { Text("$last $first") })
                }
                item {
                    Column {
                        OutlinedTextField(
                            vm.className, { vm.className = it },
                            label = { Text(stringResource(R.string.class_name)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            vm.classLevel, { vm.classLevel = it },
                            label = { Text(stringResource(R.string.class_level)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { vm.import(onDone) },
                            enabled = vm.lastNameCol != null && vm.firstNameCol != null &&
                                vm.className.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text(stringResource(R.string.import_confirm))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnPicker(
    headers: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    label: String,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { headers.getOrNull(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            headers.forEachIndexed { i, h ->
                DropdownMenuItem(text = { Text(h) }, onClick = { onSelect(i); expanded = false })
            }
        }
    }
}

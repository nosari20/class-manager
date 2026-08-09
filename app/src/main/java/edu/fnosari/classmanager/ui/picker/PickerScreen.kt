package edu.fnosari.classmanager.ui.picker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.material3.CardDefaults
import edu.fnosari.classmanager.ui.classdetail.StudentAvatar
import edu.fnosari.classmanager.ui.common.stripeEdge
import edu.fnosari.classmanager.ui.theme.classColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PickerScreen(classId: Long, onBack: () -> Unit) {
    val vm: PickerViewModel =
        viewModel(factory = PickerViewModel.factory(LocalContext.current.appContainer, classId))
    val students by vm.students.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val cycleJustReset by vm.cycleJustReset.collectAsStateWithLifecycle()
    val today = vm.today()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pronoteTopBarColors(),
                title = { Text(stringResource(R.string.random_picker)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.resetCycle() }) {
                        Icon(Icons.Default.RestartAlt, stringResource(R.string.reset_cycle))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (cycleJustReset) {
                Text(
                    stringResource(R.string.cycle_restarted),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            val accent = classColor(classId)
            Card(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn()) togetherWith fadeOut()
                    },
                    label = "pick",
                ) { s ->
                    Column(
                        Modifier.fillMaxWidth().stripeEdge(accent).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (s == null) {
                            Icon(Icons.Default.Casino, null, Modifier.size(96.dp))
                            Text(
                                stringResource(R.string.tap_to_pick),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            StudentAvatar(s, 140.dp)
                            Text(
                                "${s.firstName} ${s.lastName}",
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            }

            val picked = students.count { it.pickedInCurrentCycle }
            Text("$picked/${students.size}", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(
                progress = { if (students.isEmpty()) 0f else picked.toFloat() / students.size },
                color = accent,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            Button(onClick = { vm.pick() }, Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(R.string.pick), style = MaterialTheme.typography.titleMedium)
            }

            Text(
                stringResource(R.string.absent_today),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                students.forEach { s ->
                    FilterChip(
                        selected = s.absentTodayDate == today,
                        onClick = { vm.toggleAbsent(s) },
                        label = { Text("${s.firstName} ${s.lastName.firstOrNull() ?: ' '}.") },
                    )
                }
            }
        }
    }
}

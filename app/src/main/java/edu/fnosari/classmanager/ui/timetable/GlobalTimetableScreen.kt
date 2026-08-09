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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.appContainer
import edu.fnosari.classmanager.data.OneOffSlot
import edu.fnosari.classmanager.data.SchoolClass
import edu.fnosari.classmanager.data.SlotCancellation
import edu.fnosari.classmanager.data.TimetableSlot
import edu.fnosari.classmanager.domain.occurrencesOn
import edu.fnosari.classmanager.ui.common.AccentPill
import edu.fnosari.classmanager.ui.common.SectionLabel
import edu.fnosari.classmanager.ui.common.pronoteTopBarColors
import edu.fnosari.classmanager.ui.common.stripeEdge
import edu.fnosari.classmanager.ui.theme.classColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class GlobalTimetableState(
    val slots: List<TimetableSlot> = emptyList(),
    val cancellations: List<SlotCancellation> = emptyList(),
    val oneOffs: List<OneOffSlot> = emptyList(),
    val classes: Map<Long, SchoolClass> = emptyMap(),
    val roomNames: Map<Long, String> = emptyMap(),
    val weekARef: LocalDate? = null,
)

class GlobalTimetableViewModel(container: AppContainer) : ViewModel() {
    private val db = container.db

    val state = combine(
        combine(
            db.timetableDao().allSlots(),
            db.timetableDao().allCancellations(),
            db.timetableDao().allOneOffs(),
        ) { s, c, o -> Triple(s, c, o) },
        db.classDao().all(),
        db.seatingDao().rooms(),
        container.settings.weekARef,
    ) { (slots, cancellations, oneOffs), classes, rooms, ref ->
        GlobalTimetableState(
            slots = slots,
            cancellations = cancellations,
            oneOffs = oneOffs,
            classes = classes.associateBy { it.id },
            roomNames = rooms.associate { it.id to it.name },
            weekARef = ref?.let(LocalDate::parse),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GlobalTimetableState())

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { GlobalTimetableViewModel(container) }
        }
    }
}

/** Read-only week view of all classes' courses, browsable week by week. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalTimetableScreen(onBack: () -> Unit, onOpenClass: (Long) -> Unit) {
    val vm: GlobalTimetableViewModel =
        viewModel(factory = GlobalTimetableViewModel.factory(LocalContext.current.appContainer))
    val state by vm.state.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    var weekStart by remember { mutableStateOf(today.minusDays((today.dayOfWeek.value - 1).toLong())) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pronoteTopBarColors(),
                title = { Text(stringResource(R.string.timetable)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            val week = (0..6L).map { off ->
                val date = weekStart.plusDays(off)
                date to occurrencesOn(
                    date, state.slots, state.cancellations, state.oneOffs, state.weekARef,
                )
            }
            if (week.all { it.second.isEmpty() }) {
                Text(
                    stringResource(R.string.week_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                for ((date, occ) in week) {
                    if (occ.isEmpty()) continue
                    item(key = "day$date") {
                        Text(
                            "${dayName(date.dayOfWeek.value)} ${date.dayOfMonth}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(occ, key = { "occ-$date-${it.classId}-${it.slotId ?: "o${it.oneOffId}"}-${it.startTime}" }) { o ->
                        val accent = classColor(o.classId)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.width(64.dp)) {
                                Text(o.startTime, fontWeight = FontWeight.Bold)
                                Text(
                                    o.endTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Card(
                                onClick = { onOpenClass(o.classId) },
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().stripeEdge(accent)
                                        .padding(start = 24.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                                ) {
                                    Text(
                                        state.classes[o.classId]?.name ?: "",
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Row(Modifier.padding(top = 4.dp)) {
                                        o.roomId?.let { state.roomNames[it] }?.let {
                                            AccentPill(it, accent)
                                        }
                                        if (o.oneOffId != null) {
                                            AccentPill(
                                                stringResource(R.string.one_off),
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                                Modifier.padding(start = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

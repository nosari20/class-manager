package edu.fnosari.classmanager.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import edu.fnosari.classmanager.ui.common.AccentPill
import edu.fnosari.classmanager.ui.common.SectionLabel
import edu.fnosari.classmanager.ui.common.pronoteTopBarColors
import edu.fnosari.classmanager.ui.common.stripeEdge
import edu.fnosari.classmanager.ui.theme.classColor
import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenSeating: (Long) -> Unit,
    onOpenSeatingPlans: (Long) -> Unit,
    onOpenStudent: (Long) -> Unit,
    onOpenTimetable: () -> Unit,
) {
    val vm: TodayViewModel =
        viewModel(factory = TodayViewModel.factory(LocalContext.current.appContainer))
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pronoteTopBarColors(),
                title = {
                    Text(
                        state.date.format(
                            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
                        ).replaceFirstChar { it.uppercase() },
                    )
                },
                actions = {
                    IconButton(onClick = onOpenTimetable) {
                        Icon(Icons.Default.CalendarMonth, stringResource(R.string.timetable))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item { SectionLabel(stringResource(R.string.today_courses), Modifier.padding(bottom = 8.dp)) }
            if (state.courses.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_courses_today),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            items(state.courses, key = { "c${it.occurrence.slotId ?: "o${it.occurrence.oneOffId}"}-${it.occurrence.startTime}" }) { c ->
                val accent = classColor(c.schoolClass.id)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(72.dp)) {
                        Text(
                            c.occurrence.startTime,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (c.isPast) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            c.occurrence.endTime,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Card(
                        Modifier
                            .weight(1f)
                            .clickable {
                                scope.launch {
                                    val planId = vm.resolvePlan(c)
                                    if (planId != null) onOpenSeating(planId)
                                    else onOpenSeatingPlans(c.schoolClass.id)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                c.isCurrent -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().stripeEdge(accent)
                                .padding(start = 26.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                        ) {
                            if (c.isCurrent) {
                                SectionLabel(stringResource(R.string.current_course))
                            }
                            Text(
                                c.schoolClass.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(Modifier.padding(top = 6.dp)) {
                                c.roomName?.let { AccentPill(it, accent) }
                                if (c.occurrence.oneOffId != null) {
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

            item {
                SectionLabel(
                    stringResource(R.string.today_reminders),
                    Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            }
            if (state.reminders.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_reminders_today),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.reminders, key = { "r${it.reminder.id}" }) { tr ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { tr.student?.let { onOpenStudent(it.id) } },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(
                                tr.reminder.text,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (tr.reminder.done) TextDecoration.LineThrough else null,
                            )
                            Text(
                                (tr.student?.let { "${it.firstName} ${it.lastName} — " } ?: "") +
                                    DateFormat.getTimeInstance(DateFormat.SHORT)
                                        .format(Date(tr.reminder.dueAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(
                            checked = tr.reminder.done,
                            onCheckedChange = { if (!tr.reminder.done) vm.markReminderDone(tr.reminder) },
                        )
                    }
                }
            }
        }
    }
}

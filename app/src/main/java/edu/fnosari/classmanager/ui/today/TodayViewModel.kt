package edu.fnosari.classmanager.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.Reminder
import edu.fnosari.classmanager.data.SchoolClass
import edu.fnosari.classmanager.data.Student
import edu.fnosari.classmanager.domain.Occurrence
import edu.fnosari.classmanager.domain.occurrencesOn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayCourse(
    val occurrence: Occurrence,
    val schoolClass: SchoolClass,
    val roomName: String?,
    val isCurrent: Boolean,
    val isPast: Boolean,
)

data class TodayReminder(
    val reminder: Reminder,
    val student: Student?,
)

data class TodayState(
    val date: LocalDate = LocalDate.now(),
    val courses: List<TodayCourse> = emptyList(),
    val reminders: List<TodayReminder> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {
    private val db = container.db

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    private val timetableData = combine(
        db.timetableDao().allSlots(),
        db.timetableDao().allCancellations(),
        db.timetableDao().allOneOffs(),
        container.settings.weekARef,
    ) { slots, cancels, oneOffs, ref -> Quad(slots, cancels, oneOffs, ref) }

    val state: StateFlow<TodayState> = ticker.flatMapLatest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        combine(
            timetableData,
            db.classDao().all(),
            db.seatingDao().rooms(),
            db.reminderDao().remindersBetween(dayStart, dayEnd),
            db.studentDao().allStudents(),
        ) { (slots, cancels, oneOffs, refStr), classes, rooms, reminders, students ->
            val byClass = classes.associateBy { it.id }
            val roomNames = rooms.associate { it.id to it.name }
            val studentById = students.associateBy { it.id }
            val ref = refStr?.let(LocalDate::parse)
            val now = LocalTime.now()
            val courses = occurrencesOn(today, slots, cancels, oneOffs, ref).mapNotNull { occ ->
                byClass[occ.classId]?.let { c ->
                    val start = LocalTime.parse(occ.startTime)
                    val end = LocalTime.parse(occ.endTime)
                    TodayCourse(
                        occurrence = occ,
                        schoolClass = c,
                        roomName = occ.roomId?.let { roomNames[it] },
                        isCurrent = !now.isBefore(start) && now.isBefore(end),
                        isPast = !now.isBefore(end),
                    )
                }
            }
            TodayState(
                date = today,
                courses = courses,
                reminders = reminders.map { TodayReminder(it, studentById[it.studentId]) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayState())

    fun markReminderDone(r: Reminder) = viewModelScope.launch {
        db.reminderDao().markDone(r.id)
        container.alarms.cancelReminder(r.id)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { TodayViewModel(container) }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

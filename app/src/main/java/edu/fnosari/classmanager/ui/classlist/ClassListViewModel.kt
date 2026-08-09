package edu.fnosari.classmanager.ui.classlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.AppDatabase
import edu.fnosari.classmanager.data.SchoolClass
import edu.fnosari.classmanager.data.SettingsRepository
import edu.fnosari.classmanager.data.TimetableSlot
import edu.fnosari.classmanager.domain.currentSlot
import edu.fnosari.classmanager.domain.nextSlotWithTime
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClassRow(val schoolClass: SchoolClass, val studentCount: Int)

data class CourseBanner(
    val schoolClass: SchoolClass,
    val slot: TimetableSlot,
    val roomName: String?,
    val isCurrent: Boolean,
    val startAt: LocalDateTime?,   // set for upcoming course
)

@OptIn(ExperimentalCoroutinesApi::class)
class ClassListViewModel(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val banner: StateFlow<CourseBanner?> = combine(
        db.classDao().all(),
        db.timetableDao().allSlots(),
        db.seatingDao().rooms(),
        settings.weekARef,
        minuteTicker,
    ) { classes, slots, rooms, weekARefStr, _ ->
        val byClass = classes.associateBy { it.id }
        val roomNames = rooms.associate { it.id to it.name }
        val ref = weekARefStr?.let(LocalDate::parse)
        val now = LocalDateTime.now()
        val current = currentSlot(now, slots, ref)
        if (current != null) {
            byClass[current.classId]?.let { c ->
                CourseBanner(c, current, current.roomId?.let { roomNames[it] }, true, null)
            }
        } else {
            nextSlotWithTime(now, slots, ref)?.let { (slot, start) ->
                byClass[slot.classId]?.let { c ->
                    CourseBanner(c, slot, slot.roomId?.let { roomNames[it] }, false, start)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Latest seating plan for the banner's class+room, if any. */
    suspend fun resolvePlan(b: CourseBanner): Long? =
        b.slot.roomId?.let { db.seatingDao().latestPlanFor(b.schoolClass.id, it)?.id }

    val classes: StateFlow<List<ClassRow>> = db.classDao().all()
        .flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList())
            else combine(list.map { c ->
                db.classDao().studentCount(c.id).map { ClassRow(c, it) }
            }) { it.toList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String, level: String) = viewModelScope.launch {
        db.classDao().insert(SchoolClass(name = name.trim(), level = level.trim()))
    }

    fun rename(c: SchoolClass, name: String, level: String) = viewModelScope.launch {
        db.classDao().update(c.copy(name = name.trim(), level = level.trim()))
    }

    fun delete(c: SchoolClass) = viewModelScope.launch { db.classDao().delete(c) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ClassListViewModel(container.db, container.settings) }
        }
    }
}

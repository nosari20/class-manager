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

@OptIn(ExperimentalCoroutinesApi::class)
class ClassListViewModel(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) : ViewModel() {
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

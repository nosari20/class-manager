package edu.fnosari.classmanager.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.Student
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PickerViewModel(container: AppContainer, private val classId: Long) : ViewModel() {
    private val db = container.db

    fun today(): String = LocalDate.now().toString()

    val students = db.studentDao().studentsIn(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _current = MutableStateFlow<Student?>(null)
    val current: StateFlow<Student?> = _current

    private val _cycleJustReset = MutableStateFlow(false)
    val cycleJustReset: StateFlow<Boolean> = _cycleJustReset

    fun pick() = viewModelScope.launch {
        var eligible = db.studentDao().eligibleForPick(classId, today())
        _cycleJustReset.value = false
        if (eligible.isEmpty()) {
            db.studentDao().resetCycle(classId)
            _cycleJustReset.value = true
            eligible = db.studentDao().eligibleForPick(classId, today())
        }
        val chosen = eligible.randomOrNull() ?: return@launch
        db.studentDao().setPicked(chosen.id, true)
        _current.value = chosen
    }

    fun resetCycle() = viewModelScope.launch {
        db.studentDao().resetCycle(classId)
        _current.value = null
        _cycleJustReset.value = false
    }

    fun toggleAbsent(s: Student) = viewModelScope.launch {
        db.studentDao().setAbsent(s.id, if (s.absentTodayDate == today()) null else today())
    }

    companion object {
        fun factory(container: AppContainer, classId: Long) = viewModelFactory {
            initializer { PickerViewModel(container, classId) }
        }
    }
}

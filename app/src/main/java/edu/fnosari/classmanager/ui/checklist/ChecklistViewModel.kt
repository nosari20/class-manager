package edu.fnosari.classmanager.ui.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.Checklist
import edu.fnosari.classmanager.data.ChecklistEntry
import edu.fnosari.classmanager.data.SchoolClass
import edu.fnosari.classmanager.data.Student
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A checklist plus how many of the class have handed it in. */
data class ChecklistRow(val checklist: Checklist, val done: Int, val total: Int)

class ChecklistsViewModel(container: AppContainer, private val classId: Long) : ViewModel() {
    private val dao = container.db.checklistDao()
    private val students = container.db.studentDao().studentsIn(classId)

    val rows: StateFlow<List<ChecklistRow>> = combine(
        dao.checklistsFor(classId),
        dao.progressFor(classId),
        students,
    ) { checklists, progress, roster ->
        val doneByChecklist = progress.associate { it.checklistId to it.doneCount }
        checklists.map { ChecklistRow(it, doneByChecklist[it.id] ?: 0, roster.size) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(title: String, dueDate: String?) = viewModelScope.launch {
        dao.insert(Checklist(classId = classId, title = title.trim(), dueDate = dueDate))
    }

    fun rename(c: Checklist, title: String, dueDate: String?) = viewModelScope.launch {
        dao.update(c.copy(title = title.trim(), dueDate = dueDate))
    }

    fun delete(c: Checklist) = viewModelScope.launch { dao.delete(c) }

    companion object {
        fun factory(container: AppContainer, classId: Long) = viewModelFactory {
            initializer { ChecklistsViewModel(container, classId) }
        }
    }
}

data class ChecklistDetailState(
    val checklist: Checklist? = null,
    val schoolClass: SchoolClass? = null,
    val students: List<Student> = emptyList(),
    val doneIds: Set<Long> = emptySet(),
) {
    val missing: List<Student> get() = students.filter { it.id !in doneIds }
}

class ChecklistDetailViewModel(
    container: AppContainer,
    private val checklistId: Long,
) : ViewModel() {
    private val db = container.db
    private val dao = db.checklistDao()

    private val checklistFlow = dao.byIdFlow(checklistId)

    val state: StateFlow<ChecklistDetailState> = combine(
        checklistFlow,
        dao.entries(checklistId),
        db.classDao().all(),
        db.studentDao().allStudents(),
    ) { checklist, entries, classes, allStudents ->
        ChecklistDetailState(
            checklist = checklist,
            schoolClass = classes.firstOrNull { it.id == checklist?.classId },
            students = allStudents.filter { it.classId == checklist?.classId },
            doneIds = entries.map { it.studentId }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChecklistDetailState())

    fun toggle(studentId: Long, done: Boolean) = viewModelScope.launch {
        if (done) dao.check(ChecklistEntry(checklistId = checklistId, studentId = studentId))
        else dao.uncheck(checklistId, studentId)
    }

    fun markAll() = viewModelScope.launch {
        state.value.students.forEach {
            dao.check(ChecklistEntry(checklistId = checklistId, studentId = it.id))
        }
    }

    fun clearAll() = viewModelScope.launch { dao.clear(checklistId) }

    companion object {
        fun factory(container: AppContainer, checklistId: Long) = viewModelFactory {
            initializer { ChecklistDetailViewModel(container, checklistId) }
        }
    }
}

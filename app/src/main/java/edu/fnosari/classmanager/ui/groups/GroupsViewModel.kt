package edu.fnosari.classmanager.ui.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.Grouping
import edu.fnosari.classmanager.data.GroupingGroup
import edu.fnosari.classmanager.data.GroupingMember
import edu.fnosari.classmanager.data.SeparationConstraint
import edu.fnosari.classmanager.data.Student
import edu.fnosari.classmanager.domain.GroupResult
import edu.fnosari.classmanager.domain.SplitMode
import edu.fnosari.classmanager.domain.computeSizes
import edu.fnosari.classmanager.domain.generateGroups
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DraftGroups(val groups: List<List<Student>>, val violations: Set<Pair<Long, Long>>)

class GroupsViewModel(container: AppContainer, private val classId: Long) : ViewModel() {
    private val db = container.db

    val students = db.studentDao().studentsIn(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val constraints = db.groupDao().constraintsFor(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val groupings = db.groupDao().groupingsFor(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var splitMode by mutableStateOf(SplitMode.GROUPS_OF_N)
    var n by mutableIntStateOf(4)
    var excludeAbsent by mutableStateOf(true)

    private val _draft = MutableStateFlow<DraftGroups?>(null)
    val draft: StateFlow<DraftGroups?> = _draft

    private val _infeasible = MutableStateFlow<List<Pair<Long, Long>>?>(null)
    val infeasible: StateFlow<List<Pair<Long, Long>>?> = _infeasible

    private val _viewing = MutableStateFlow<Pair<Grouping, List<List<Student>>>?>(null)
    val viewing: StateFlow<Pair<Grouping, List<List<Student>>>?> = _viewing

    fun addConstraint(a: Long, b: Long) = viewModelScope.launch {
        if (a != b) {
            db.groupDao().insertConstraint(
                SeparationConstraint(classId = classId, studentAId = a, studentBId = b)
            )
        }
    }

    fun removeConstraint(c: SeparationConstraint) = viewModelScope.launch {
        db.groupDao().deleteConstraint(c)
    }

    fun generate() = viewModelScope.launch {
        _infeasible.value = null
        _viewing.value = null
        val today = LocalDate.now().toString()
        val pool = students.value.filter { !excludeAbsent || it.absentTodayDate != today }
        if (pool.isEmpty()) return@launch
        val seps = constraints.value.map { it.studentAId to it.studentBId }.toSet()
        val sizes = computeSizes(pool.size, splitMode, n)
        when (val r = generateGroups(pool.map { it.id }, seps, sizes)) {
            is GroupResult.Success -> {
                val byId = pool.associateBy { it.id }
                _draft.value = DraftGroups(r.groups.map { g -> g.map { byId.getValue(it) } }, emptySet())
            }
            is GroupResult.Infeasible -> _infeasible.value = r.clashingPairs
        }
    }

    fun moveStudent(student: Student, toGroup: Int) {
        val d = _draft.value ?: return
        val groups = d.groups.map { it.toMutableList() }
        groups.forEach { it.remove(student) }
        groups[toGroup].add(student)
        val seps = constraints.value.map { it.studentAId to it.studentBId }.toSet()
        val violations = mutableSetOf<Pair<Long, Long>>()
        for (g in groups) for (a in g) for (b in g) {
            if (a.id < b.id && ((a.id to b.id) in seps || (b.id to a.id) in seps)) {
                violations.add(a.id to b.id)
            }
        }
        _draft.value = DraftGroups(groups.map { it.toList() }, violations)
    }

    fun saveDraft(name: String) = viewModelScope.launch {
        val d = _draft.value ?: return@launch
        val gId = db.groupDao().insertGrouping(Grouping(classId = classId, name = name.trim()))
        d.groups.forEachIndexed { i, members ->
            val groupId = db.groupDao().insertGroup(GroupingGroup(groupingId = gId, index = i))
            members.forEach {
                db.groupDao().insertMember(GroupingMember(groupId = groupId, studentId = it.id))
            }
        }
        _draft.value = null
    }

    fun renameGrouping(g: Grouping, name: String) = viewModelScope.launch {
        db.groupDao().updateGrouping(g.copy(name = name.trim()))
    }

    fun deleteGrouping(g: Grouping) = viewModelScope.launch {
        db.groupDao().deleteGrouping(g)
        if (_viewing.value?.first?.id == g.id) _viewing.value = null
    }

    fun viewGrouping(g: Grouping) = viewModelScope.launch {
        val byId = students.value.associateBy { it.id }
        val groups = db.groupDao().groupsOf(g.id).map { gg ->
            db.groupDao().membersOf(gg.id).mapNotNull { byId[it.studentId] }
        }
        _draft.value = null
        _infeasible.value = null
        _viewing.value = g to groups
    }

    companion object {
        fun factory(container: AppContainer, classId: Long) = viewModelFactory {
            initializer { GroupsViewModel(container, classId) }
        }
    }
}

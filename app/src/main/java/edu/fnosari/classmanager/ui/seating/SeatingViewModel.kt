package edu.fnosari.classmanager.ui.seating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.Desk
import edu.fnosari.classmanager.data.Room
import edu.fnosari.classmanager.data.SeatAssignment
import edu.fnosari.classmanager.data.SeatingPlan
import edu.fnosari.classmanager.data.Student
import edu.fnosari.classmanager.domain.violatingDesks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val ADJACENCY_THRESHOLD = 0.15f

class SeatingPlansViewModel(container: AppContainer, private val classId: Long) : ViewModel() {
    private val dao = container.db.seatingDao()

    val plans = dao.plansFor(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rooms = dao.rooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun create(name: String, roomId: Long): Long =
        dao.insertPlan(SeatingPlan(classId = classId, roomId = roomId, name = name.trim()))

    fun rename(p: SeatingPlan, name: String) = viewModelScope.launch {
        dao.updatePlan(p.copy(name = name.trim()))
    }

    fun delete(p: SeatingPlan) = viewModelScope.launch { dao.deletePlan(p) }

    companion object {
        fun factory(container: AppContainer, classId: Long) = viewModelFactory {
            initializer { SeatingPlansViewModel(container, classId) }
        }
    }
}

data class SeatingUiState(
    val plan: SeatingPlan? = null,
    val room: Room? = null,
    val desks: List<Desk> = emptyList(),
    val seats: Map<Long, Map<Int, Student>> = emptyMap(),   // deskId -> seatIndex -> student
    val unassigned: List<Student> = emptyList(),
    val violatingDeskIds: Set<Long> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class SeatingViewModel(container: AppContainer, private val planId: Long) : ViewModel() {
    private val db = container.db
    private val dao = db.seatingDao()

    val state: StateFlow<SeatingUiState> = flow { emit(dao.planById(planId)) }
        .flatMapLatest { plan ->
            if (plan == null) return@flatMapLatest flowOf(SeatingUiState())
            combine(
                dao.desks(plan.roomId),
                dao.assignments(planId),
                db.studentDao().studentsIn(plan.classId),
                db.groupDao().constraintsFor(plan.classId),
                flow { emit(dao.roomById(plan.roomId)) },
            ) { desks, assignments, students, constraints, room ->
                val byId = students.associateBy { it.id }
                val seats: Map<Long, Map<Int, Student>> = assignments
                    .mapNotNull { a -> byId[a.studentId]?.let { Triple(a.deskId, a.seatIndex, it) } }
                    .groupBy({ it.first }, { it.second to it.third })
                    .mapValues { (_, list) -> list.toMap() }
                val seatedIds = seats.values.flatMap { it.values }.map { it.id }.toSet()
                val seps = constraints.map { it.studentAId to it.studentBId }.toSet()
                SeatingUiState(
                    plan = plan,
                    room = room,
                    desks = desks,
                    seats = seats,
                    unassigned = students.filter { it.id !in seatedIds },
                    violatingDeskIds = violatingDesks(
                        desks,
                        seats.mapValues { (_, m) -> m.values.map { it.id } },
                        seps,
                        ADJACENCY_THRESHOLD,
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeatingUiState())

    fun assign(deskId: Long, seatIndex: Int, studentId: Long) = viewModelScope.launch {
        dao.unassignStudent(planId, studentId) // student moves if already seated elsewhere
        dao.assign(SeatAssignment(planId = planId, deskId = deskId, seatIndex = seatIndex, studentId = studentId))
    }

    fun unassign(deskId: Long, seatIndex: Int) = viewModelScope.launch {
        dao.unassign(planId, deskId, seatIndex)
    }

    companion object {
        fun factory(container: AppContainer, planId: Long) = viewModelFactory {
            initializer { SeatingViewModel(container, planId) }
        }
    }
}

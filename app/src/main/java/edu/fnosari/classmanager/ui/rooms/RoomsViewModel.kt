package edu.fnosari.classmanager.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.Desk
import edu.fnosari.classmanager.data.Room
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoomsViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.db.seatingDao()

    val rooms = dao.rooms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String) = viewModelScope.launch { dao.insertRoom(Room(name = name.trim())) }
    fun rename(r: Room, name: String) = viewModelScope.launch { dao.updateRoom(r.copy(name = name.trim())) }
    fun delete(r: Room) = viewModelScope.launch { dao.deleteRoom(r) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RoomsViewModel(container) }
        }
    }
}

class RoomEditorViewModel(container: AppContainer, val roomId: Long) : ViewModel() {
    private val dao = container.db.seatingDao()

    val desks = dao.desks(roomId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val room = kotlinx.coroutines.flow.flow { emit(dao.roomById(roomId)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun addDesk(x: Float, y: Float, seats: Int) = viewModelScope.launch {
        dao.insertDesk(Desk(roomId = roomId, x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f), seats = seats))
    }

    fun setSeats(d: Desk, seats: Int) = viewModelScope.launch {
        if (seats < d.seats) dao.unassignSeatsFrom(d.id, seats)
        dao.updateDesk(d.copy(seats = seats))
    }

    fun rotate(d: Desk) = viewModelScope.launch {
        dao.updateDesk(d.copy(vertical = !d.vertical))
    }

    fun moveDesk(d: Desk, x: Float, y: Float) = viewModelScope.launch {
        dao.updateDesk(d.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)))
    }

    fun deleteDesk(d: Desk) = viewModelScope.launch { dao.deleteDesk(d) }

    companion object {
        fun factory(container: AppContainer, roomId: Long) = viewModelFactory {
            initializer { RoomEditorViewModel(container, roomId) }
        }
    }
}

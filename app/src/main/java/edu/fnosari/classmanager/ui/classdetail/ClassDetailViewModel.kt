package edu.fnosari.classmanager.ui.classdetail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.SchoolClass
import edu.fnosari.classmanager.data.Student
import edu.fnosari.classmanager.data.TimetableSlot
import edu.fnosari.classmanager.data.WeekParityTag
import edu.fnosari.classmanager.ui.common.PhotoUtil
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClassDetailViewModel(
    private val container: AppContainer,
    private val classId: Long,
) : ViewModel() {
    private val db = container.db

    val schoolClass: StateFlow<SchoolClass?> = flow { emit(db.classDao().byId(classId)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val students: StateFlow<List<Student>> = db.studentDao().studentsIn(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val slots: StateFlow<List<TimetableSlot>> = db.timetableDao().slotsFor(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rooms = db.seatingDao().rooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<edu.fnosari.classmanager.data.Room>())

    fun addStudent(last: String, first: String, photoUri: Uri?, context: Context) =
        viewModelScope.launch(Dispatchers.IO) {
            val path = photoUri?.let {
                runCatching { PhotoUtil.importPhoto(context, it, container.photosDir) }.getOrNull()
            }
            db.studentDao().insert(
                Student(classId = classId, lastName = last.trim(), firstName = first.trim(), photoPath = path)
            )
        }

    fun updateStudent(s: Student, last: String, first: String, photoUri: Uri?, context: Context) =
        viewModelScope.launch(Dispatchers.IO) {
            val path = photoUri?.let {
                runCatching { PhotoUtil.importPhoto(context, it, container.photosDir) }.getOrNull()
            } ?: s.photoPath
            db.studentDao().update(s.copy(lastName = last.trim(), firstName = first.trim(), photoPath = path))
        }

    fun deleteStudent(s: Student) = viewModelScope.launch(Dispatchers.IO) {
        db.studentDao().delete(s)
        s.photoPath?.let { File(container.photosDir.parentFile, it).delete() }
    }

    fun addSlot(day: Int, start: String, end: String, parity: WeekParityTag, roomId: Long?) =
        viewModelScope.launch {
            db.timetableDao().insert(
                TimetableSlot(classId = classId, dayOfWeek = day, startTime = start,
                    endTime = end, weekParity = parity, roomId = roomId)
            )
        }

    fun deleteSlot(t: TimetableSlot) = viewModelScope.launch { db.timetableDao().delete(t) }

    companion object {
        fun factory(container: AppContainer, classId: Long) = viewModelFactory {
            initializer { ClassDetailViewModel(container, classId) }
        }
    }
}

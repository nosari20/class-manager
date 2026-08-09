package edu.fnosari.classmanager.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.CustomField
import edu.fnosari.classmanager.data.Note
import edu.fnosari.classmanager.data.Reminder
import edu.fnosari.classmanager.data.ReminderType
import edu.fnosari.classmanager.domain.nextLessonStart
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReminderCreateResult { OK, NO_TIMETABLE }

class StudentDetailViewModel(
    private val container: AppContainer,
    private val studentId: Long,
) : ViewModel() {
    private val db = container.db

    val student = db.studentDao().byIdFlow(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val notes = db.noteDao().notesFor(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val fields = db.customFieldDao().fieldsFor(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reminders = db.reminderDao().remindersFor(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addNote(text: String) = viewModelScope.launch {
        db.noteDao().insert(Note(studentId = studentId, text = text.trim()))
    }

    fun updateNote(n: Note, text: String) = viewModelScope.launch {
        db.noteDao().update(n.copy(text = text.trim()))
    }

    fun deleteNote(n: Note) = viewModelScope.launch { db.noteDao().delete(n) }

    fun addField(key: String, value: String) = viewModelScope.launch {
        db.customFieldDao().insert(CustomField(studentId = studentId, key = key.trim(), value = value.trim()))
    }

    fun updateField(f: CustomField, key: String, value: String) = viewModelScope.launch {
        db.customFieldDao().update(f.copy(key = key.trim(), value = value.trim()))
    }

    fun deleteField(f: CustomField) = viewModelScope.launch { db.customFieldDao().delete(f) }

    suspend fun addReminder(text: String, type: ReminderType, fixedAt: Long?): ReminderCreateResult {
        val s = student.value ?: return ReminderCreateResult.OK
        val dueAt: Long = when (type) {
            ReminderType.FIXED_DATETIME, ReminderType.MORNING_DIGEST -> fixedAt!!
            ReminderType.NEXT_LESSON -> {
                val slots = db.timetableDao().slotsForOnce(s.classId)
                val ref = container.settings.weekARef.first()?.let(LocalDate::parse)
                val next = nextLessonStart(LocalDateTime.now(), slots, ref)
                    ?: return ReminderCreateResult.NO_TIMETABLE
                next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        val id = db.reminderDao().insert(
            Reminder(studentId = studentId, text = text.trim(), type = type, dueAt = dueAt)
        )
        db.reminderDao().byId(id)?.let { container.alarms.scheduleReminder(it) }
        return ReminderCreateResult.OK
    }

    fun markDone(r: Reminder) = viewModelScope.launch {
        db.reminderDao().markDone(r.id)
        container.alarms.cancelReminder(r.id)
    }

    fun deleteReminder(r: Reminder) = viewModelScope.launch {
        db.reminderDao().delete(r)
        container.alarms.cancelReminder(r.id)
    }

    companion object {
        fun factory(container: AppContainer, studentId: Long) = viewModelFactory {
            initializer { StudentDetailViewModel(container, studentId) }
        }
    }
}

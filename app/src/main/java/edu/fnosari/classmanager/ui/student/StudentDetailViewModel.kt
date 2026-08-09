package edu.fnosari.classmanager.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.CustomField
import edu.fnosari.classmanager.data.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    companion object {
        fun factory(container: AppContainer, studentId: Long) = viewModelFactory {
            initializer { StudentDetailViewModel(container, studentId) }
        }
    }
}

package edu.fnosari.classmanager.ui.csv

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.SchoolClass
import edu.fnosari.classmanager.data.Student
import edu.fnosari.classmanager.domain.CsvParser
import edu.fnosari.classmanager.domain.CsvTable
import edu.fnosari.classmanager.domain.FIRST_NAME_HEADERS
import edu.fnosari.classmanager.domain.LAST_NAME_HEADERS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CsvImportViewModel(private val container: AppContainer) : ViewModel() {
    var table by mutableStateOf<CsvTable?>(null)
    var lastNameCol by mutableStateOf<Int?>(null)
    var firstNameCol by mutableStateOf<Int?>(null)
    var className by mutableStateOf("")
    var classLevel by mutableStateOf("")
    var error by mutableStateOf<String?>(null)

    fun load(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val t = CsvParser.parse(bytes)
            if (t.headers.isEmpty() || t.rows.isEmpty()) {
                error = "empty"
                return@launch
            }
            val saved = container.settings.csvMapping.first()
            table = t
            lastNameCol = saved?.first?.takeIf { it < t.headers.size }
                ?: CsvParser.guessColumn(t.headers, LAST_NAME_HEADERS)
            firstNameCol = saved?.second?.takeIf { it < t.headers.size }
                ?: CsvParser.guessColumn(t.headers, FIRST_NAME_HEADERS)
            error = null
        } catch (e: Exception) {
            error = "read"
        }
    }

    fun preview(): List<Pair<String, String>> {
        val t = table ?: return emptyList()
        val l = lastNameCol ?: return emptyList()
        val f = firstNameCol ?: return emptyList()
        return t.rows.map { it.getOrElse(l) { "" } to it.getOrElse(f) { "" } }
    }

    fun import(onDone: (Long) -> Unit) = viewModelScope.launch {
        val t = table ?: return@launch
        val l = lastNameCol ?: return@launch
        val f = firstNameCol ?: return@launch
        val classId = container.db.classDao().insert(
            SchoolClass(name = className.trim(), level = classLevel.trim())
        )
        container.db.studentDao().insertAll(
            t.rows
                .filter { it.getOrElse(l) { "" }.isNotBlank() }
                .map {
                    Student(
                        classId = classId,
                        lastName = it.getOrElse(l) { "" }.trim(),
                        firstName = it.getOrElse(f) { "" }.trim(),
                    )
                }
        )
        container.settings.setCsvMapping(l, f)
        onDone(classId)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CsvImportViewModel(container) }
        }
    }
}

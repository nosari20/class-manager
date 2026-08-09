package edu.fnosari.classmanager.ui.settings

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
import edu.fnosari.classmanager.backup.BackupCheck
import edu.fnosari.classmanager.backup.BackupCrypto
import edu.fnosari.classmanager.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val digestTime = container.settings.digestTime
        .stateIn(viewModelScope, SharingStarted.Eagerly, "07:00")
    val weekARef = container.settings.weekARef
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // null | "confirm" | "done" | "backup_done" | invalid reason
    var restoreState by mutableStateOf<String?>(null)
    private var pendingRestore: ByteArray? = null

    // null | synced event count | -1 on failure
    var calendarSyncResult by mutableStateOf<Int?>(null)

    fun syncCalendar() = viewModelScope.launch(Dispatchers.IO) {
        calendarSyncResult = try {
            container.calendarSync.sync()
        } catch (e: Exception) {
            -1
        }
    }

    fun setDigestTime(t: String) = viewModelScope.launch {
        container.settings.setDigestTime(t)
        container.alarms.scheduleDailyDigest()
    }

    fun setWeekARef(monday: String) = viewModelScope.launch {
        container.settings.setWeekARef(monday)
    }

    // password chosen in the backup dialog, used when the file picker returns
    var backupPassword: String? = null

    fun backupTo(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)!!.use {
                container.backup.writeBackup(it, backupPassword)
            }
            restoreState = "backup_done"
        } catch (e: Exception) {
            restoreState = "backup_failed"
        } finally {
            backupPassword = null
        }
    }

    fun startRestore(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            if (BackupCrypto.isEncrypted(bytes)) {
                pendingRestore = bytes
                restoreState = "password"
            } else {
                checkAndConfirm(bytes)
            }
        } catch (e: Exception) {
            restoreState = "not_a_zip"
        }
    }

    fun submitRestorePassword(password: String) = viewModelScope.launch(Dispatchers.IO) {
        val encrypted = pendingRestore ?: return@launch
        val plain = BackupCrypto.decrypt(encrypted, password)
        if (plain == null) {
            restoreState = "bad_password"
        } else {
            checkAndConfirm(plain)
        }
    }

    private fun checkAndConfirm(zipBytes: ByteArray) {
        when (val check = BackupManager.validate(zipBytes)) {
            is BackupCheck.Ok -> {
                pendingRestore = zipBytes
                restoreState = "confirm"
            }
            is BackupCheck.Invalid -> restoreState = check.reason
        }
    }

    fun confirmRestore() = viewModelScope.launch(Dispatchers.IO) {
        pendingRestore?.let { container.backup.restore(it) }
        pendingRestore = null
        restoreState = "done"
    }

    fun dismissDialog() {
        pendingRestore = null
        restoreState = null
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}

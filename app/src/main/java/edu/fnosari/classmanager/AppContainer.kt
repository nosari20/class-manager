package edu.fnosari.classmanager

import android.content.Context
import edu.fnosari.classmanager.backup.BackupManager
import edu.fnosari.classmanager.calendar.CalendarSyncManager
import edu.fnosari.classmanager.data.AppDatabase
import edu.fnosari.classmanager.data.SettingsRepository
import edu.fnosari.classmanager.notifications.AlarmScheduler
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    var db: AppDatabase = AppDatabase.build(appContext)
        private set
    val settings: SettingsRepository = SettingsRepository(appContext)
    val photosDir: File = File(appContext.filesDir, "photos").apply { mkdirs() }
    val alarms: AlarmScheduler by lazy { AlarmScheduler(appContext, this) }
    val backup: BackupManager by lazy { BackupManager(appContext, this) }
    val calendarSync: CalendarSyncManager by lazy { CalendarSyncManager(appContext, this) }

    // cached so the locale wrap in Activity.attachBaseContext and the first theme frame
    // don't have to wait on DataStore
    @Volatile var language: String = "system"
        private set
    @Volatile var theme: String = "system"

    fun primeUiPrefs() {
        val (lang, th) = settings.readUiPrefsBlocking()
        language = lang
        theme = th
    }

    fun setLanguageCache(tag: String) { language = tag }

    fun reopenDb() {
        db = AppDatabase.build(appContext)
    }
}

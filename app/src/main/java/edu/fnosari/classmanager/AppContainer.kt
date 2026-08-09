package edu.fnosari.classmanager

import android.content.Context
import edu.fnosari.classmanager.data.AppDatabase
import edu.fnosari.classmanager.data.SettingsRepository
import java.io.File

class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.build(context)
    val settings: SettingsRepository = SettingsRepository(context)
    val photosDir: File = File(context.filesDir, "photos").apply { mkdirs() }
}

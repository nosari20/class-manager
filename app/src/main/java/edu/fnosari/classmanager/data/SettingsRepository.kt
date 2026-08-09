package edu.fnosari.classmanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val WEEK_A_REF = stringPreferencesKey("week_a_ref")
        val DIGEST_TIME = stringPreferencesKey("digest_time")
        val CSV_LAST = intPreferencesKey("csv_last_col")
        val CSV_FIRST = intPreferencesKey("csv_first_col")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
    }

    val weekARef: Flow<String?> = context.dataStore.data.map { it[Keys.WEEK_A_REF] }
    val digestTime: Flow<String> = context.dataStore.data.map { it[Keys.DIGEST_TIME] ?: "07:00" }
    val csvMapping: Flow<Pair<Int, Int>?> = context.dataStore.data.map { p ->
        val l = p[Keys.CSV_LAST]
        val f = p[Keys.CSV_FIRST]
        if (l != null && f != null) l to f else null
    }

    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "system" }
    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }

    /**
     * Reads language and theme off the main thread's normal flow collection. Both are needed
     * before the first frame (locale wrapping happens in attachBaseContext), so they are read
     * once at startup instead of being awaited.
     */
    fun readUiPrefsBlocking(): Pair<String, String> = runBlocking {
        val p = context.dataStore.data.first()
        (p[Keys.LANGUAGE] ?: "system") to (p[Keys.THEME] ?: "system")
    }

    suspend fun setLanguage(tag: String) { context.dataStore.edit { it[Keys.LANGUAGE] = tag } }
    suspend fun setTheme(t: String) { context.dataStore.edit { it[Keys.THEME] = t } }
    suspend fun setWeekARef(date: String) { context.dataStore.edit { it[Keys.WEEK_A_REF] = date } }
    suspend fun setDigestTime(t: String) { context.dataStore.edit { it[Keys.DIGEST_TIME] = t } }
    suspend fun setCsvMapping(last: Int, first: Int) {
        context.dataStore.edit { it[Keys.CSV_LAST] = last; it[Keys.CSV_FIRST] = first }
    }
}

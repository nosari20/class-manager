package edu.fnosari.classmanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import edu.fnosari.classmanager.ui.theme.Palette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "settings")

/** The three preferences needed before the first frame is drawn. */
data class UiPrefs(val language: String, val theme: String, val accentColor: Int)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val WEEK_A_REF = stringPreferencesKey("week_a_ref")
        val DIGEST_TIME = stringPreferencesKey("digest_time")
        val CSV_LAST = intPreferencesKey("csv_last_col")
        val CSV_FIRST = intPreferencesKey("csv_first_col")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val ACCENT = intPreferencesKey("accent_color")
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
    val accentColor: Flow<Int> = context.dataStore.data.map { it[Keys.ACCENT] ?: Palette.DEFAULT }

    /**
     * Reads language and theme off the main thread's normal flow collection. Both are needed
     * before the first frame (locale wrapping happens in attachBaseContext), so they are read
     * once at startup instead of being awaited.
     */
    fun readUiPrefsBlocking(): UiPrefs = runBlocking {
        val p = context.dataStore.data.first()
        UiPrefs(
            language = p[Keys.LANGUAGE] ?: "system",
            theme = p[Keys.THEME] ?: "system",
            accentColor = p[Keys.ACCENT] ?: Palette.DEFAULT,
        )
    }

    suspend fun setLanguage(tag: String) { context.dataStore.edit { it[Keys.LANGUAGE] = tag } }
    suspend fun setTheme(t: String) { context.dataStore.edit { it[Keys.THEME] = t } }
    suspend fun setAccentColor(argb: Int) { context.dataStore.edit { it[Keys.ACCENT] = argb } }
    suspend fun setWeekARef(date: String) { context.dataStore.edit { it[Keys.WEEK_A_REF] = date } }
    suspend fun setDigestTime(t: String) { context.dataStore.edit { it[Keys.DIGEST_TIME] = t } }
    suspend fun setCsvMapping(last: Int, first: Int) {
        context.dataStore.edit { it[Keys.CSV_LAST] = last; it[Keys.CSV_FIRST] = first }
    }
}

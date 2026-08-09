package edu.fnosari.classmanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val WEEK_A_REF = stringPreferencesKey("week_a_ref")
        val DIGEST_TIME = stringPreferencesKey("digest_time")
        val CSV_LAST = intPreferencesKey("csv_last_col")
        val CSV_FIRST = intPreferencesKey("csv_first_col")
    }

    val weekARef: Flow<String?> = context.dataStore.data.map { it[Keys.WEEK_A_REF] }
    val digestTime: Flow<String> = context.dataStore.data.map { it[Keys.DIGEST_TIME] ?: "07:00" }
    val csvMapping: Flow<Pair<Int, Int>?> = context.dataStore.data.map { p ->
        val l = p[Keys.CSV_LAST]
        val f = p[Keys.CSV_FIRST]
        if (l != null && f != null) l to f else null
    }

    suspend fun setWeekARef(date: String) { context.dataStore.edit { it[Keys.WEEK_A_REF] = date } }
    suspend fun setDigestTime(t: String) { context.dataStore.edit { it[Keys.DIGEST_TIME] = t } }
    suspend fun setCsvMapping(last: Int, first: Int) {
        context.dataStore.edit { it[Keys.CSV_LAST] = last; it[Keys.CSV_FIRST] = first }
    }
}

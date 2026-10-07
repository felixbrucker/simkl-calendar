package com.felixbrucker.simklcalendar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.syncMetadataDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "simkl_sync_settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "simkl_sync_prefs")) }
)

data class SyncMetadataPreferences(
    val lastCalendarJsonSync: Long = 0L,
    val lastActivitiesAll: String? = null,
    val calendarLastModifiedAt: Map<String, Long> = emptyMap(),
    val calendarLastModifiedHeader: Map<String, String> = emptyMap()
)

@Singleton
class SyncMetadataRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.syncMetadataDataStore

    companion object {
        private val KEY_LAST_CALENDAR_JSON_SYNC = longPreferencesKey("last_calendar_json_sync")
        private val KEY_LAST_ACTIVITIES_ALL = stringPreferencesKey("last_activities_all")
    }

    val preferencesFlow: Flow<SyncMetadataPreferences> = dataStore.data.map { preferences ->
        val calendarLastModifiedAt = mutableMapOf<String, Long>()
        val calendarLastModifiedHeader = mutableMapOf<String, String>()

        preferences.asMap().forEach { (key, value) ->
            if (key.name.startsWith("cal_json_last_mod_")) {
                (value as? Long)?.let { calendarLastModifiedAt[key.name] = it }
            } else if (key.name.startsWith("cal_json_header_")) {
                (value as? String)?.let { calendarLastModifiedHeader[key.name] = it }
            }
        }

        SyncMetadataPreferences(
            lastCalendarJsonSync = preferences[KEY_LAST_CALENDAR_JSON_SYNC] ?: 0L,
            lastActivitiesAll = preferences[KEY_LAST_ACTIVITIES_ALL],
            calendarLastModifiedAt = calendarLastModifiedAt,
            calendarLastModifiedHeader = calendarLastModifiedHeader
        )
    }

    suspend fun setLastCalendarJsonSync(timestamp: Long) {
        dataStore.edit { it[KEY_LAST_CALENDAR_JSON_SYNC] = timestamp }
    }

    suspend fun setLastActivitiesAll(timestamp: String?) {
        dataStore.edit { preferences ->
            if (timestamp != null) {
                preferences[KEY_LAST_ACTIVITIES_ALL] = timestamp
            } else {
                preferences.remove(KEY_LAST_ACTIVITIES_ALL)
            }
        }
    }

    suspend fun setCalendarLastModifiedAt(key: String, timestamp: Long) {
        dataStore.edit { it[longPreferencesKey(key)] = timestamp }
    }

    suspend fun setCalendarLastModifiedHeader(key: String, header: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = header }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

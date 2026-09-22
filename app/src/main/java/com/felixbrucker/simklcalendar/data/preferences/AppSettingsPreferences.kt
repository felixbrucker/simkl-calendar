package com.felixbrucker.simklcalendar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context = context,
                sharedPreferencesName = "notification_prefs",
                keysToMigrate = setOf("sync_interval_hours")
            )
        )
    }
)

data class AppSettingsPreferences(
    val syncIntervalHours: Int = 12
)

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.appSettingsDataStore

    companion object {
        private val KEY_SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
    }

    val preferencesFlow: Flow<AppSettingsPreferences> = dataStore.data.map { preferences ->
        AppSettingsPreferences(
            syncIntervalHours = preferences[KEY_SYNC_INTERVAL_HOURS] ?: 12
        )
    }

    suspend fun setSyncIntervalHours(hours: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_SYNC_INTERVAL_HOURS] = hours
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

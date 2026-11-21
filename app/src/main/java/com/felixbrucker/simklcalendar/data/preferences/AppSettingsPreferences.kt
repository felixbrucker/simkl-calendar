package com.felixbrucker.simklcalendar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.squareup.moshi.JsonClass

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

@JsonClass(generateAdapter = true)
data class AppSettingsPreferences(
    val syncIntervalHours: Int = 12,
    val isSentryEnabled: Boolean = true,
    val showDebugActions: Boolean = false,
    val trendingPeriod: String = "today_100"
)

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.appSettingsDataStore

    companion object {
        private val KEY_SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        private val KEY_IS_SENTRY_ENABLED = booleanPreferencesKey("is_sentry_enabled")
        private val KEY_SHOW_DEBUG_ACTIONS = booleanPreferencesKey("show_debug_actions")
        private val KEY_TRENDING_PERIOD = stringPreferencesKey("trending_period")
    }

    val preferencesFlow: Flow<AppSettingsPreferences> = dataStore.data.map { preferences ->
        AppSettingsPreferences(
            syncIntervalHours = preferences[KEY_SYNC_INTERVAL_HOURS] ?: 12,
            isSentryEnabled = preferences[KEY_IS_SENTRY_ENABLED] ?: true,
            showDebugActions = preferences[KEY_SHOW_DEBUG_ACTIONS] ?: false,
            trendingPeriod = preferences[KEY_TRENDING_PERIOD] ?: "today_100"
        )
    }

    suspend fun setSyncIntervalHours(hours: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_SYNC_INTERVAL_HOURS] = hours
        }
    }

    suspend fun setIsSentryEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_SENTRY_ENABLED] = enabled
        }
    }

    suspend fun setShowDebugActions(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SHOW_DEBUG_ACTIONS] = enabled
        }
    }

    suspend fun setTrendingPeriod(period: String) {
        dataStore.edit { preferences ->
            preferences[KEY_TRENDING_PERIOD] = period
        }
    }

    suspend fun setAll(prefs: AppSettingsPreferences) {
        dataStore.edit { preferences ->
            preferences[KEY_SYNC_INTERVAL_HOURS] = prefs.syncIntervalHours
            preferences[KEY_IS_SENTRY_ENABLED] = prefs.isSentryEnabled
            preferences[KEY_SHOW_DEBUG_ACTIONS] = prefs.showDebugActions
            preferences[KEY_TRENDING_PERIOD] = prefs.trendingPeriod
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

package com.felixbrucker.simklcalendar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.squareup.moshi.JsonClass

val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context = context,
                sharedPreferencesName = "notification_prefs",
                keysToMigrate = setOf(
                    "use_exact_alarms",
                    "default_notify_airing",
                    "default_notify_season_finished",
                    "default_notify_movie_theater",
                    "default_notify_movie_digital"
                )
            )
        )
    }
)

@JsonClass(generateAdapter = true)
data class NotificationPreferences(
    val useExactAlarms: Boolean = false,
    val defaultNotifyAiring: Boolean = false,
    val defaultNotifySeasonFinished: Boolean = true,
    val defaultNotifyMovieTheater: Boolean = false,
    val defaultNotifyMovieDigital: Boolean = true,
    val notifyNewUntrackedTrendingMovies: Boolean = false,
    val notifyNewUntrackedTrendingTv: Boolean = false,
    val notifyNewUntrackedTrendingAnime: Boolean = false
) {
    val isAnyTrendingNotificationEnabled: Boolean
        get() = notifyNewUntrackedTrendingMovies || notifyNewUntrackedTrendingTv || notifyNewUntrackedTrendingAnime
}

@Singleton
class NotificationRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.notificationDataStore

    companion object {
        private val KEY_USE_EXACT_ALARMS = booleanPreferencesKey("use_exact_alarms")
        private val KEY_NOTIFY_AIRING = booleanPreferencesKey("default_notify_airing")
        private val KEY_NOTIFY_SEASON_FINISHED = booleanPreferencesKey("default_notify_season_finished")
        private val KEY_NOTIFY_MOVIE_THEATER = booleanPreferencesKey("default_notify_movie_theater")
        private val KEY_NOTIFY_MOVIE_DIGITAL = booleanPreferencesKey("default_notify_movie_digital")
        private val KEY_NOTIFY_TRENDING_MOVIES = booleanPreferencesKey("notify_new_untracked_trending_movies")
        private val KEY_NOTIFY_TRENDING_TV = booleanPreferencesKey("notify_new_untracked_trending_tv")
        private val KEY_NOTIFY_TRENDING_ANIME = booleanPreferencesKey("notify_new_untracked_trending_anime")
    }

    val preferencesFlow: Flow<NotificationPreferences> = dataStore.data.map { preferences ->
        NotificationPreferences(
            useExactAlarms = preferences[KEY_USE_EXACT_ALARMS] ?: false,
            defaultNotifyAiring = preferences[KEY_NOTIFY_AIRING] ?: false,
            defaultNotifySeasonFinished = preferences[KEY_NOTIFY_SEASON_FINISHED] ?: true,
            defaultNotifyMovieTheater = preferences[KEY_NOTIFY_MOVIE_THEATER] ?: false,
            defaultNotifyMovieDigital = preferences[KEY_NOTIFY_MOVIE_DIGITAL] ?: true,
            notifyNewUntrackedTrendingMovies = preferences[KEY_NOTIFY_TRENDING_MOVIES] ?: false,
            notifyNewUntrackedTrendingTv = preferences[KEY_NOTIFY_TRENDING_TV] ?: false,
            notifyNewUntrackedTrendingAnime = preferences[KEY_NOTIFY_TRENDING_ANIME] ?: false
        )
    }

    suspend fun setUseExactAlarms(enabled: Boolean) {
        dataStore.edit { it[KEY_USE_EXACT_ALARMS] = enabled }
    }

    suspend fun setDefaultNotifyAiring(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_AIRING] = enabled }
    }

    suspend fun setDefaultNotifySeasonFinished(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_SEASON_FINISHED] = enabled }
    }

    suspend fun setDefaultNotifyMovieTheater(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_MOVIE_THEATER] = enabled }
    }

    suspend fun setDefaultNotifyMovieDigital(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_MOVIE_DIGITAL] = enabled }
    }

    suspend fun setNotifyNewUntrackedTrendingMovies(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_TRENDING_MOVIES] = enabled }
    }

    suspend fun setNotifyNewUntrackedTrendingTv(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_TRENDING_TV] = enabled }
    }

    suspend fun setNotifyNewUntrackedTrendingAnime(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_TRENDING_ANIME] = enabled }
    }

    suspend fun setAll(prefs: NotificationPreferences) {
        dataStore.edit { preferences ->
            preferences[KEY_USE_EXACT_ALARMS] = prefs.useExactAlarms
            preferences[KEY_NOTIFY_AIRING] = prefs.defaultNotifyAiring
            preferences[KEY_NOTIFY_SEASON_FINISHED] = prefs.defaultNotifySeasonFinished
            preferences[KEY_NOTIFY_MOVIE_THEATER] = prefs.defaultNotifyMovieTheater
            preferences[KEY_NOTIFY_MOVIE_DIGITAL] = prefs.defaultNotifyMovieDigital
            preferences[KEY_NOTIFY_TRENDING_MOVIES] = prefs.notifyNewUntrackedTrendingMovies
            preferences[KEY_NOTIFY_TRENDING_TV] = prefs.notifyNewUntrackedTrendingTv
            preferences[KEY_NOTIFY_TRENDING_ANIME] = prefs.notifyNewUntrackedTrendingAnime
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}


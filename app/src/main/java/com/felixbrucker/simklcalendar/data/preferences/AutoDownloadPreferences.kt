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

val Context.autoDownloadDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auto_download_settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "auto_download_prefs"),
            SharedPreferencesMigration(
                context = context,
                sharedPreferencesName = "notification_prefs",
                keysToMigrate = setOf("search_interval_hours")
            )
        )
    }
)

data class AutoDownloadPreferences(
    val quality: String = "1080p",
    val preferHevc: Boolean = true,
    val autoDownloadUnwatchedTv: Boolean = false,
    val autoDownloadUnwatchedAnime: Boolean = false,
    val autoDownloadUnwatchedMovie: Boolean = false,
    val autoDownloadSeasonUnwatchedTv: Boolean = false,
    val autoDownloadSeasonUnwatchedAnime: Boolean = false,
    val preferredKeywords: List<String> = emptyList(),
    val ignoreKeywords: List<String> = emptyList(),
    val searchIntervalHours: Int = 12
)

@Singleton
class AutoDownloadRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.autoDownloadDataStore

    companion object {
        private val KEY_QUALITY = stringPreferencesKey("quality")
        private val KEY_PREFER_HEVC = booleanPreferencesKey("prefer_hevc")
        private val KEY_UNWATCHED_TV = booleanPreferencesKey("auto_download_unwatched_tv")
        private val KEY_UNWATCHED_ANIME = booleanPreferencesKey("auto_download_unwatched_anime")
        private val KEY_UNWATCHED_MOVIE = booleanPreferencesKey("auto_download_unwatched_movie")
        private val KEY_SEASON_UNWATCHED_TV = booleanPreferencesKey("auto_download_season_unwatched_tv")
        private val KEY_SEASON_UNWATCHED_ANIME = booleanPreferencesKey("auto_download_season_unwatched_anime")
        private val KEY_PREFERRED_KEYWORDS = stringPreferencesKey("preferred_keywords")
        private val KEY_IGNORE_KEYWORDS = stringPreferencesKey("ignore_keywords")
        private val KEY_SEARCH_INTERVAL_HOURS = intPreferencesKey("search_interval_hours")
    }

    val preferencesFlow: Flow<AutoDownloadPreferences> = dataStore.data.map { preferences ->
        AutoDownloadPreferences(
            quality = preferences[KEY_QUALITY] ?: "1080p",
            preferHevc = preferences[KEY_PREFER_HEVC] ?: true,
            autoDownloadUnwatchedTv = preferences[KEY_UNWATCHED_TV] ?: false,
            autoDownloadUnwatchedAnime = preferences[KEY_UNWATCHED_ANIME] ?: false,
            autoDownloadUnwatchedMovie = preferences[KEY_UNWATCHED_MOVIE] ?: false,
            autoDownloadSeasonUnwatchedTv = preferences[KEY_SEASON_UNWATCHED_TV] ?: false,
            autoDownloadSeasonUnwatchedAnime = preferences[KEY_SEASON_UNWATCHED_ANIME] ?: false,
            preferredKeywords = preferences[KEY_PREFERRED_KEYWORDS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
            ignoreKeywords = preferences[KEY_IGNORE_KEYWORDS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
            searchIntervalHours = preferences[KEY_SEARCH_INTERVAL_HOURS] ?: 12
        )
    }

    suspend fun setQuality(quality: String) {
        dataStore.edit { it[KEY_QUALITY] = quality }
    }

    suspend fun setPreferHevc(prefer: Boolean) {
        dataStore.edit { it[KEY_PREFER_HEVC] = prefer }
    }

    suspend fun setAutoDownloadUnwatchedTv(enabled: Boolean) {
        dataStore.edit { it[KEY_UNWATCHED_TV] = enabled }
    }

    suspend fun setAutoDownloadUnwatchedAnime(enabled: Boolean) {
        dataStore.edit { it[KEY_UNWATCHED_ANIME] = enabled }
    }

    suspend fun setAutoDownloadUnwatchedMovie(enabled: Boolean) {
        dataStore.edit { it[KEY_UNWATCHED_MOVIE] = enabled }
    }

    suspend fun setAutoDownloadSeasonUnwatchedTv(enabled: Boolean) {
        dataStore.edit { it[KEY_SEASON_UNWATCHED_TV] = enabled }
    }

    suspend fun setAutoDownloadSeasonUnwatchedAnime(enabled: Boolean) {
        dataStore.edit { it[KEY_SEASON_UNWATCHED_ANIME] = enabled }
    }

    suspend fun setPreferredKeywords(keywords: List<String>) {
        dataStore.edit { it[KEY_PREFERRED_KEYWORDS] = keywords.joinToString("\n") }
    }

    suspend fun setIgnoreKeywords(keywords: List<String>) {
        dataStore.edit { it[KEY_IGNORE_KEYWORDS] = keywords.joinToString("\n") }
    }

    suspend fun setSearchIntervalHours(hours: Int) {
        dataStore.edit { it[KEY_SEARCH_INTERVAL_HOURS] = hours }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

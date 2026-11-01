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
    val deleteFileOnWatched: Boolean = false,
    val deleteEmptyParentDirectory: Boolean = false,
    val preferredKeywords: List<String> = emptyList(),
    val ignoreKeywords: List<String> = emptyList(),
    val searchIntervalHours: Int = 12,
    val postProcessTvEnabled: Boolean = false,
    val postProcessAnimeEnabled: Boolean = false,
    val postProcessMovieEnabled: Boolean = false,
    val postProcessReplaceSpacesWithDotsTv: Boolean = true,
    val postProcessReplaceSpacesWithDotsAnime: Boolean = false,
    val postProcessReplaceSpacesWithDotsMovie: Boolean = true,
    val postProcessTemplateTv: String = "{title}.{episode_slug}.{resolution}.{source}-{release_group}",
    val postProcessTemplateAnime: String = "[{release_group}] {title_romaji} - {episode} [{resolution}]",
    val postProcessTemplateMovie: String = "{title}.{year}.{resolution}.{source}-{release_group}"
)

@Singleton
class AutoDownloadRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.autoDownloadDataStore

    companion object {
        const val DEFAULT_TEMPLATE_TV = "{title}.{episode_slug}.{resolution}.{source}-{release_group}"
        const val DEFAULT_TEMPLATE_ANIME = "[{release_group}] {title_romaji} - {episode} [{resolution}]"
        const val DEFAULT_TEMPLATE_MOVIE = "{title}.{year}.{resolution}.{source}-{release_group}"

        private val KEY_QUALITY = stringPreferencesKey("quality")
        private val KEY_PREFER_HEVC = booleanPreferencesKey("prefer_hevc")
        private val KEY_UNWATCHED_TV = booleanPreferencesKey("auto_download_unwatched_tv")
        private val KEY_UNWATCHED_ANIME = booleanPreferencesKey("auto_download_unwatched_anime")
        private val KEY_UNWATCHED_MOVIE = booleanPreferencesKey("auto_download_unwatched_movie")
        private val KEY_SEASON_UNWATCHED_TV = booleanPreferencesKey("auto_download_season_unwatched_tv")
        private val KEY_SEASON_UNWATCHED_ANIME = booleanPreferencesKey("auto_download_season_unwatched_anime")
        private val KEY_DELETE_FILE_ON_WATCHED = booleanPreferencesKey("delete_file_on_watched")
        private val KEY_DELETE_EMPTY_PARENT_DIRECTORY = booleanPreferencesKey("delete_empty_parent_directory")
        private val KEY_PREFERRED_KEYWORDS = stringPreferencesKey("preferred_keywords")
        private val KEY_IGNORE_KEYWORDS = stringPreferencesKey("ignore_keywords")
        private val KEY_SEARCH_INTERVAL_HOURS = intPreferencesKey("search_interval_hours")

        private val KEY_POST_PROCESS_TV_ENABLED = booleanPreferencesKey("post_process_tv_enabled")
        private val KEY_POST_PROCESS_ANIME_ENABLED = booleanPreferencesKey("post_process_anime_enabled")
        private val KEY_POST_PROCESS_MOVIE_ENABLED = booleanPreferencesKey("post_process_movie_enabled")
        private val KEY_POST_PROCESS_REPLACE_SPACES_WITH_DOTS_TV =
            booleanPreferencesKey("post_process_replace_spaces_with_dots_tv")
        private val KEY_POST_PROCESS_REPLACE_SPACES_WITH_DOTS_ANIME =
            booleanPreferencesKey("post_process_replace_spaces_with_dots_anime")
        private val KEY_POST_PROCESS_REPLACE_SPACES_WITH_DOTS_MOVIE =
            booleanPreferencesKey("post_process_replace_spaces_with_dots_movie")
        private val KEY_POST_PROCESS_TEMPLATE_TV = stringPreferencesKey("post_process_template_tv")
        private val KEY_POST_PROCESS_TEMPLATE_ANIME = stringPreferencesKey("post_process_template_anime")
        private val KEY_POST_PROCESS_TEMPLATE_MOVIE = stringPreferencesKey("post_process_template_movie")
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
            deleteFileOnWatched = preferences[KEY_DELETE_FILE_ON_WATCHED] ?: false,
            deleteEmptyParentDirectory = preferences[KEY_DELETE_EMPTY_PARENT_DIRECTORY] ?: false,
            preferredKeywords = preferences[KEY_PREFERRED_KEYWORDS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
            ignoreKeywords = preferences[KEY_IGNORE_KEYWORDS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
            searchIntervalHours = preferences[KEY_SEARCH_INTERVAL_HOURS] ?: 12,
            postProcessTvEnabled = preferences[KEY_POST_PROCESS_TV_ENABLED] ?: false,
            postProcessAnimeEnabled = preferences[KEY_POST_PROCESS_ANIME_ENABLED] ?: false,
            postProcessMovieEnabled = preferences[KEY_POST_PROCESS_MOVIE_ENABLED] ?: false,
            postProcessReplaceSpacesWithDotsTv = preferences[KEY_POST_PROCESS_REPLACE_SPACES_WITH_DOTS_TV] ?: true,
            postProcessReplaceSpacesWithDotsAnime = preferences[KEY_POST_PROCESS_REPLACE_SPACES_WITH_DOTS_ANIME] ?: false,
            postProcessReplaceSpacesWithDotsMovie = preferences[KEY_POST_PROCESS_REPLACE_SPACES_WITH_DOTS_MOVIE] ?: true,
            postProcessTemplateTv = preferences[KEY_POST_PROCESS_TEMPLATE_TV] ?: DEFAULT_TEMPLATE_TV,
            postProcessTemplateAnime = preferences[KEY_POST_PROCESS_TEMPLATE_ANIME] ?: DEFAULT_TEMPLATE_ANIME,
            postProcessTemplateMovie = preferences[KEY_POST_PROCESS_TEMPLATE_MOVIE] ?: DEFAULT_TEMPLATE_MOVIE
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

    suspend fun setDeleteFileOnWatched(enabled: Boolean) {
        dataStore.edit { it[KEY_DELETE_FILE_ON_WATCHED] = enabled }
    }

    suspend fun setDeleteEmptyParentDirectory(enabled: Boolean) {
        dataStore.edit { it[KEY_DELETE_EMPTY_PARENT_DIRECTORY] = enabled }
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

    suspend fun setPostProcessTvEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_POST_PROCESS_TV_ENABLED] = enabled }
    }

    suspend fun setPostProcessAnimeEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_POST_PROCESS_ANIME_ENABLED] = enabled }
    }

    suspend fun setPostProcessMovieEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_POST_PROCESS_MOVIE_ENABLED] = enabled }
    }

    suspend fun setPostProcessReplaceSpacesWithDotsTv(enabled: Boolean) {
        dataStore.edit { it[KEY_POST_PROCESS_REPLACE_SPACES_WITH_DOTS_TV] = enabled }
    }

    suspend fun setPostProcessReplaceSpacesWithDotsAnime(enabled: Boolean) {
        dataStore.edit { it[KEY_POST_PROCESS_REPLACE_SPACES_WITH_DOTS_ANIME] = enabled }
    }

    suspend fun setPostProcessReplaceSpacesWithDotsMovie(enabled: Boolean) {
        dataStore.edit { it[KEY_POST_PROCESS_REPLACE_SPACES_WITH_DOTS_MOVIE] = enabled }
    }

    suspend fun setPostProcessTemplateTv(template: String) {
        dataStore.edit { it[KEY_POST_PROCESS_TEMPLATE_TV] = template }
    }

    suspend fun setPostProcessTemplateAnime(template: String) {
        dataStore.edit { it[KEY_POST_PROCESS_TEMPLATE_ANIME] = template }
    }

    suspend fun setPostProcessTemplateMovie(template: String) {
        dataStore.edit { it[KEY_POST_PROCESS_TEMPLATE_MOVIE] = template }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

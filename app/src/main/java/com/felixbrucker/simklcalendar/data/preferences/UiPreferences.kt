package com.felixbrucker.simklcalendar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.uiDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ui_settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "ui_prefs")) }
)

enum class ViewMode {
    CALENDAR,
    TABLE
}

data class UiPreferences(
    val viewMode: ViewMode = ViewMode.CALENDAR,
    val filterShowTv: Boolean = true,
    val filterShowAnime: Boolean = true,
    val filterShowMovies: Boolean = true,
    val filterOnlyUnwatched: Boolean = true,
    val filterOnlyPremieres: Boolean = false,
    val filterOnlyFinales: Boolean = false,
    val filterOnlyDigitalDvd: Boolean = false,
    val filterShowEarlier: Boolean = false
)

@Singleton
class UiRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.uiDataStore

    companion object {
        private val KEY_VIEW_MODE = stringPreferencesKey("view_mode")
        private val KEY_FILTER_SHOW_TV = booleanPreferencesKey("filter_show_tv")
        private val KEY_FILTER_SHOW_ANIME = booleanPreferencesKey("filter_show_anime")
        private val KEY_FILTER_SHOW_MOVIES = booleanPreferencesKey("filter_show_movies")
        private val KEY_FILTER_ONLY_UNWATCHED = booleanPreferencesKey("filter_only_unwatched")
        private val KEY_FILTER_ONLY_PREMIERES = booleanPreferencesKey("filter_only_premieres")
        private val KEY_FILTER_ONLY_FINALES = booleanPreferencesKey("filter_only_finales")
        private val KEY_FILTER_ONLY_DIGITAL_DVD = booleanPreferencesKey("filter_only_digital_dvd")
        private val KEY_FILTER_SHOW_EARLIER = booleanPreferencesKey("filter_show_earlier")
    }

    val preferencesFlow: Flow<UiPreferences> = dataStore.data.map { preferences ->
        UiPreferences(
            viewMode = preferences[KEY_VIEW_MODE]?.let {
                try { ViewMode.valueOf(it) } catch (_: Exception) { ViewMode.CALENDAR }
            } ?: ViewMode.CALENDAR,
            filterShowTv = preferences[KEY_FILTER_SHOW_TV] ?: true,
            filterShowAnime = preferences[KEY_FILTER_SHOW_ANIME] ?: true,
            filterShowMovies = preferences[KEY_FILTER_SHOW_MOVIES] ?: true,
            filterOnlyUnwatched = preferences[KEY_FILTER_ONLY_UNWATCHED] ?: true,
            filterOnlyPremieres = preferences[KEY_FILTER_ONLY_PREMIERES] ?: false,
            filterOnlyFinales = preferences[KEY_FILTER_ONLY_FINALES] ?: false,
            filterOnlyDigitalDvd = preferences[KEY_FILTER_ONLY_DIGITAL_DVD] ?: false,
            filterShowEarlier = preferences[KEY_FILTER_SHOW_EARLIER] ?: false
        )
    }

    suspend fun setViewMode(viewMode: ViewMode) {
        dataStore.edit { it[KEY_VIEW_MODE] = viewMode.name }
    }

    suspend fun updateFilters(transform: (UiPreferences) -> UiPreferences) {
        dataStore.edit { preferences ->
            val current = UiPreferences(
                viewMode = preferences[KEY_VIEW_MODE]?.let {
                    try { ViewMode.valueOf(it) } catch (_: Exception) { ViewMode.CALENDAR }
                } ?: ViewMode.CALENDAR,
                filterShowTv = preferences[KEY_FILTER_SHOW_TV] ?: true,
                filterShowAnime = preferences[KEY_FILTER_SHOW_ANIME] ?: true,
                filterShowMovies = preferences[KEY_FILTER_SHOW_MOVIES] ?: true,
                filterOnlyUnwatched = preferences[KEY_FILTER_ONLY_UNWATCHED] ?: true,
                filterOnlyPremieres = preferences[KEY_FILTER_ONLY_PREMIERES] ?: false,
                filterOnlyFinales = preferences[KEY_FILTER_ONLY_FINALES] ?: false,
                filterOnlyDigitalDvd = preferences[KEY_FILTER_ONLY_DIGITAL_DVD] ?: false,
                filterShowEarlier = preferences[KEY_FILTER_SHOW_EARLIER] ?: false
            )
            val updated = transform(current)
            preferences[KEY_FILTER_SHOW_TV] = updated.filterShowTv
            preferences[KEY_FILTER_SHOW_ANIME] = updated.filterShowAnime
            preferences[KEY_FILTER_SHOW_MOVIES] = updated.filterShowMovies
            preferences[KEY_FILTER_ONLY_UNWATCHED] = updated.filterOnlyUnwatched
            preferences[KEY_FILTER_ONLY_PREMIERES] = updated.filterOnlyPremieres
            preferences[KEY_FILTER_ONLY_FINALES] = updated.filterOnlyFinales
            preferences[KEY_FILTER_ONLY_DIGITAL_DVD] = updated.filterOnlyDigitalDvd
            preferences[KEY_FILTER_SHOW_EARLIER] = updated.filterShowEarlier
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

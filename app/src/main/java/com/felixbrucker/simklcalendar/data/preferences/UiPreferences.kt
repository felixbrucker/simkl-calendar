package com.felixbrucker.simklcalendar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.squareup.moshi.JsonClass

val Context.uiDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ui_settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "ui_prefs")) }
)

@JsonClass(generateAdapter = false)
enum class ViewMode {
    CALENDAR,
    TABLE
}

@JsonClass(generateAdapter = true)
data class UiPreferences(
    val viewMode: ViewMode = ViewMode.CALENDAR,
    val filterShowTv: Boolean = true,
    val filterShowAnime: Boolean = true,
    val filterShowMovies: Boolean = true,
    val filterOnlyUnwatched: Boolean = true,
    val filterOnlyPremieres: Boolean = false,
    val filterOnlyFinales: Boolean = false,
    val filterOnlyDigitalDvd: Boolean = false,
    val filterShowEarlier: Boolean = false,
    val trendingExcludeOnMyLists: Boolean = true,
    val trendingShowMovies: Boolean = true,
    val trendingShowTv: Boolean = true,
    val trendingShowAnime: Boolean = true,
    val trendingMinRating: Float = 0f,
    val trendingMinYear: Int = 0,
    val trendingHasDvd: Boolean = false,
    val trendingFilterSidebarVisible: Boolean = false,
    val hideWatchlistItemsInSimilar: Boolean = false,
    val hideWatchlistItemsInRecommended: Boolean = false
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
        private val KEY_TRENDING_EXCLUDE_ON_MY_LISTS = booleanPreferencesKey("trending_exclude_on_my_lists")
        private val KEY_TRENDING_SHOW_MOVIES = booleanPreferencesKey("trending_show_movies")
        private val KEY_TRENDING_SHOW_TV = booleanPreferencesKey("trending_show_tv")
        private val KEY_TRENDING_SHOW_ANIME = booleanPreferencesKey("trending_show_anime")
        private val KEY_TRENDING_MIN_RATING = floatPreferencesKey("trending_min_rating")
        private val KEY_TRENDING_MIN_YEAR = intPreferencesKey("trending_min_year")
        private val KEY_TRENDING_HAS_DVD = booleanPreferencesKey("trending_has_dvd")
        private val KEY_TRENDING_FILTER_SIDEBAR_VISIBLE = booleanPreferencesKey("trending_filter_sidebar_visible")
        private val KEY_HIDE_WATCHLIST_ITEMS_IN_SIMILAR = booleanPreferencesKey("hide_watchlist_items_in_similar")
        private val KEY_HIDE_WATCHLIST_ITEMS_IN_RECOMMENDED = booleanPreferencesKey("hide_watchlist_items_in_recommended")
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
            filterShowEarlier = preferences[KEY_FILTER_SHOW_EARLIER] ?: false,
            trendingExcludeOnMyLists = preferences[KEY_TRENDING_EXCLUDE_ON_MY_LISTS] ?: true,
            trendingShowMovies = preferences[KEY_TRENDING_SHOW_MOVIES] ?: true,
            trendingShowTv = preferences[KEY_TRENDING_SHOW_TV] ?: true,
            trendingShowAnime = preferences[KEY_TRENDING_SHOW_ANIME] ?: true,
            trendingMinRating = preferences[KEY_TRENDING_MIN_RATING] ?: 0f,
            trendingMinYear = preferences[KEY_TRENDING_MIN_YEAR] ?: 0,
            trendingHasDvd = preferences[KEY_TRENDING_HAS_DVD] ?: false,
            trendingFilterSidebarVisible = preferences[KEY_TRENDING_FILTER_SIDEBAR_VISIBLE] ?: false,
            hideWatchlistItemsInSimilar = preferences[KEY_HIDE_WATCHLIST_ITEMS_IN_SIMILAR] ?: false,
            hideWatchlistItemsInRecommended = preferences[KEY_HIDE_WATCHLIST_ITEMS_IN_RECOMMENDED] ?: false
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
                filterShowEarlier = preferences[KEY_FILTER_SHOW_EARLIER] ?: false,
                trendingExcludeOnMyLists = preferences[KEY_TRENDING_EXCLUDE_ON_MY_LISTS] ?: true,
                trendingShowMovies = preferences[KEY_TRENDING_SHOW_MOVIES] ?: true,
                trendingShowTv = preferences[KEY_TRENDING_SHOW_TV] ?: true,
                trendingShowAnime = preferences[KEY_TRENDING_SHOW_ANIME] ?: true,
                trendingMinRating = preferences[KEY_TRENDING_MIN_RATING] ?: 0f,
                trendingMinYear = preferences[KEY_TRENDING_MIN_YEAR] ?: 0,
                trendingHasDvd = preferences[KEY_TRENDING_HAS_DVD] ?: false,
                trendingFilterSidebarVisible = preferences[KEY_TRENDING_FILTER_SIDEBAR_VISIBLE] ?: false,
                hideWatchlistItemsInSimilar = preferences[KEY_HIDE_WATCHLIST_ITEMS_IN_SIMILAR] ?: false,
                hideWatchlistItemsInRecommended = preferences[KEY_HIDE_WATCHLIST_ITEMS_IN_RECOMMENDED] ?: false
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
            preferences[KEY_TRENDING_EXCLUDE_ON_MY_LISTS] = updated.trendingExcludeOnMyLists
            preferences[KEY_TRENDING_SHOW_MOVIES] = updated.trendingShowMovies
            preferences[KEY_TRENDING_SHOW_TV] = updated.trendingShowTv
            preferences[KEY_TRENDING_SHOW_ANIME] = updated.trendingShowAnime
            preferences[KEY_TRENDING_MIN_RATING] = updated.trendingMinRating
            preferences[KEY_TRENDING_MIN_YEAR] = updated.trendingMinYear
            preferences[KEY_TRENDING_HAS_DVD] = updated.trendingHasDvd
            preferences[KEY_TRENDING_FILTER_SIDEBAR_VISIBLE] = updated.trendingFilterSidebarVisible
            preferences[KEY_HIDE_WATCHLIST_ITEMS_IN_SIMILAR] = updated.hideWatchlistItemsInSimilar
            preferences[KEY_HIDE_WATCHLIST_ITEMS_IN_RECOMMENDED] = updated.hideWatchlistItemsInRecommended
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    suspend fun setAllPreferences(prefs: UiPreferences) {
        dataStore.edit { preferences ->
            preferences[KEY_VIEW_MODE] = prefs.viewMode.name
            preferences[KEY_FILTER_SHOW_TV] = prefs.filterShowTv
            preferences[KEY_FILTER_SHOW_ANIME] = prefs.filterShowAnime
            preferences[KEY_FILTER_SHOW_MOVIES] = prefs.filterShowMovies
            preferences[KEY_FILTER_ONLY_UNWATCHED] = prefs.filterOnlyUnwatched
            preferences[KEY_FILTER_ONLY_PREMIERES] = prefs.filterOnlyPremieres
            preferences[KEY_FILTER_ONLY_FINALES] = prefs.filterOnlyFinales
            preferences[KEY_FILTER_ONLY_DIGITAL_DVD] = prefs.filterOnlyDigitalDvd
            preferences[KEY_FILTER_SHOW_EARLIER] = prefs.filterShowEarlier
            preferences[KEY_TRENDING_EXCLUDE_ON_MY_LISTS] = prefs.trendingExcludeOnMyLists
            preferences[KEY_TRENDING_SHOW_MOVIES] = prefs.trendingShowMovies
            preferences[KEY_TRENDING_SHOW_TV] = prefs.trendingShowTv
            preferences[KEY_TRENDING_SHOW_ANIME] = prefs.trendingShowAnime
            preferences[KEY_TRENDING_MIN_RATING] = prefs.trendingMinRating
            preferences[KEY_TRENDING_MIN_YEAR] = prefs.trendingMinYear
            preferences[KEY_TRENDING_HAS_DVD] = prefs.trendingHasDvd
            preferences[KEY_TRENDING_FILTER_SIDEBAR_VISIBLE] = prefs.trendingFilterSidebarVisible
        }
    }
}

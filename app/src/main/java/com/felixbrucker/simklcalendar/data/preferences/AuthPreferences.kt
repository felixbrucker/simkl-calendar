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

val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "simkl_auth_settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "simkl_pkce_auth")) }
)

data class AuthPreferences(
    val pkceCodeVerifier: String? = null,
    val pkceRedirectUri: String? = null,
    val pkceState: String? = null,
    val showAuthV2UpgradeHint: Boolean = false
)

interface AuthDataSource {
    val preferencesFlow: Flow<AuthPreferences>
    suspend fun setPkceParams(codeVerifier: String, redirectUri: String, state: String)
    suspend fun clearPkceParams()
    suspend fun setShowAuthV2UpgradeHint(show: Boolean)
    suspend fun clear()
}

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext context: Context
) : AuthDataSource {
    private val dataStore = context.authDataStore

    companion object {
        private val KEY_PKCE_CODE_VERIFIER = stringPreferencesKey("pkce_code_verifier")
        private val KEY_PKCE_REDIRECT_URI = stringPreferencesKey("pkce_redirect_uri")
        private val KEY_PKCE_STATE = stringPreferencesKey("pkce_state")
        private val KEY_SHOW_AUTH_V2_UPGRADE_HINT = booleanPreferencesKey("show_auth_v2_upgrade_hint")
    }

    override val preferencesFlow: Flow<AuthPreferences> = dataStore.data.map { preferences ->
        AuthPreferences(
            pkceCodeVerifier = preferences[KEY_PKCE_CODE_VERIFIER],
            pkceRedirectUri = preferences[KEY_PKCE_REDIRECT_URI],
            pkceState = preferences[KEY_PKCE_STATE],
            showAuthV2UpgradeHint = preferences[KEY_SHOW_AUTH_V2_UPGRADE_HINT] ?: false
        )
    }

    override suspend fun setPkceParams(codeVerifier: String, redirectUri: String, state: String) {
        dataStore.edit { preferences ->
            preferences[KEY_PKCE_CODE_VERIFIER] = codeVerifier
            preferences[KEY_PKCE_REDIRECT_URI] = redirectUri
            preferences[KEY_PKCE_STATE] = state
        }
    }

    override suspend fun clearPkceParams() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_PKCE_CODE_VERIFIER)
            preferences.remove(KEY_PKCE_REDIRECT_URI)
            preferences.remove(KEY_PKCE_STATE)
        }
    }

    override suspend fun setShowAuthV2UpgradeHint(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SHOW_AUTH_V2_UPGRADE_HINT] = show
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

package com.felixbrucker.simklcalendar.ui.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.preferences.AuthRepository
import com.felixbrucker.simklcalendar.data.util.OAuthCallbackEvent
import com.felixbrucker.simklcalendar.data.util.OAuthEventHub
import com.felixbrucker.simklcalendar.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    authRepo: AuthRepository,
    private val oAuthEventHub: OAuthEventHub
) : ViewModel() {

    val userToken: StateFlow<UserToken?> = userRepository.activeUserToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val showAuthV2UpgradeHint: StateFlow<Boolean> = authRepo.preferencesFlow
        .map { it.showAuthV2UpgradeHint }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        viewModelScope.launch {
            oAuthEventHub.oauthCallbackEvents.collect { event ->
                exchangeOAuthCode(event)
                oAuthEventHub.resetOAuthCallbackEvent()
            }
        }
    }

    fun isRealApiConfigured(): Boolean = userRepository.isRealApiConfigured()

    fun createAuthorizationUrl(redirectUri: String): String? {
        return userRepository.createAuthorizationUrl(redirectUri)
    }

    suspend fun exchangeOAuthCode(event: OAuthCallbackEvent) {
        _isSyncing.value = true
        try {
            val success = userRepository.exchangeOAuthCode(
                code = event.code,
                state = event.state,
                redirectUri = event.redirectUri
            )
            if (success) {
                Toast.makeText(context, "Successfully logged in", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Login failed", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            _isSyncing.value = false
        }
    }
}

package com.felixbrucker.simklcalendar.ui.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.preferences.AuthRepository
import com.felixbrucker.simklcalendar.data.repository.OAuthCodeEvent
import com.felixbrucker.simklcalendar.data.repository.OAuthRepository
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
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
    private val repository: SimklRepository,
    private val authRepo: AuthRepository,
    private val oAuthRepository: OAuthRepository
) : ViewModel() {

    val userToken: StateFlow<UserToken?> = repository.activeUserToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val showAuthV2UpgradeHint: StateFlow<Boolean> = authRepo.preferencesFlow
        .map { it.showAuthV2UpgradeHint }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        viewModelScope.launch {
            oAuthRepository.oauthCodeEvents.collect { event ->
                exchangeOAuthCode(event)
            }
        }
    }

    fun isRealApiConfigured(): Boolean = repository.isRealApiConfigured()

    fun createAuthorizationUrl(redirectUri: String = "simklcalendar://auth"): String? {
        return repository.createAuthorizationUrl(redirectUri)
    }

    suspend fun exchangeOAuthCode(event: OAuthCodeEvent) {
        _isSyncing.value = true
        try {
            val success = repository.exchangeOAuthCode(
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

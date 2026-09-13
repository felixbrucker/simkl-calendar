package com.felixbrucker.simklcalendar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = SimklRepository(application)

    private val _isAuthReady = MutableStateFlow(false)
    private val _userToken = MutableStateFlow<UserToken?>(null)
    
    data class AuthState(val isReady: Boolean, val token: UserToken?)
    
    val authState: StateFlow<AuthState> = combine(_isAuthReady, _userToken) { ready, token ->
        AuthState(ready, token)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthState(false, null))

    private val _pendingDetailKey = MutableStateFlow<String?>(null)
    val pendingDetailKey: StateFlow<String?> = _pendingDetailKey.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        viewModelScope.launch {
            repository.activeUserToken.collect { token ->
                _userToken.value = token
                _isAuthReady.value = true
            }
        }
    }

    fun setPendingDetailKey(key: String) {
        _pendingDetailKey.value = key
    }

    fun clearPendingDetailKey() {
        _pendingDetailKey.value = null
    }

    fun exchangeOAuthCode(
        code: String,
        state: String? = null,
        redirectUri: String? = null,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            val success = repository.exchangeOAuthCode(code = code, state = state, redirectUri = redirectUri)
            _isSyncing.value = false
            if (success) onSuccess() else onFailure()
        }
    }

    fun isTorrentServiceInstalled(): Boolean {
        return repository.torrentServiceHelper.isInstalled.value
    }

    fun isRealApiConfigured(): Boolean {
        return repository.isRealApiConfigured()
    }

    fun createAuthorizationUrl(redirectUri: String): String? {
        return repository.createAuthorizationUrl(redirectUri)
    }

    val userToken: StateFlow<UserToken?> = _userToken.asStateFlow()
}

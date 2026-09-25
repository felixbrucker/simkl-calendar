package com.felixbrucker.simklcalendar.data.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class OAuthCallbackEvent(
    val code: String,
    val state: String,
    val redirectUri: String
)

@Singleton
class OAuthEventHub @Inject constructor() {
    private val _oauthCallbackEvents = MutableSharedFlow<OAuthCallbackEvent>(replay = 1, extraBufferCapacity = 1)
    val oauthCallbackEvents: SharedFlow<OAuthCallbackEvent> = _oauthCallbackEvents.asSharedFlow()

    fun onOAuthCallbackReceived(code: String, state: String, redirectUri: String) {
        _oauthCallbackEvents.tryEmit(OAuthCallbackEvent(code, state, redirectUri))
    }
}

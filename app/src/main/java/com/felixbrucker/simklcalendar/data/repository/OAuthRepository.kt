package com.felixbrucker.simklcalendar.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class OAuthCodeEvent(
    val code: String,
    val state: String?,
    val redirectUri: String
)

@Singleton
class OAuthRepository @Inject constructor() {
    private val _oauthCodeEvents = MutableSharedFlow<OAuthCodeEvent>(replay = 1, extraBufferCapacity = 1)
    val oauthCodeEvents: SharedFlow<OAuthCodeEvent> = _oauthCodeEvents.asSharedFlow()

    fun onOAuthCodeReceived(code: String, state: String?, redirectUri: String = "simklcalendar://auth") {
        _oauthCodeEvents.tryEmit(OAuthCodeEvent(code, state, redirectUri))
    }
}

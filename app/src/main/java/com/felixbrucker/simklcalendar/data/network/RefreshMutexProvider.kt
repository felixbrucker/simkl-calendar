package com.felixbrucker.simklcalendar.data.network

import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefreshMutexProvider @Inject constructor() {
    val mutex: Mutex = Mutex()
}

package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationSettingRepository @Inject constructor(
    private val settingDao: NotificationSettingDao
) {
    val notificationSettings: Flow<List<NotificationSetting>> = settingDao.getAllSettings()

    suspend fun toggleNotificationSetting(simklId: Int, notifyEveryEpisode: Boolean, notifyAiredLastEpisode: Boolean) = withContext(Dispatchers.IO) {
        settingDao.saveSetting(
            NotificationSetting(
                simklId = simklId,
                notifyEveryEpisode = notifyEveryEpisode,
                notifyAiredLastEpisode = notifyAiredLastEpisode
            )
        )
    }
}

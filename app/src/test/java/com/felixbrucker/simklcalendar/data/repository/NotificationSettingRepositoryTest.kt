package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingRepositoryTest {

    private lateinit var settingDao: NotificationSettingDao
    private lateinit var notificationSettingRepository: NotificationSettingRepository

    @Before
    fun setUp() {
        settingDao = mockk(relaxed = true)
        notificationSettingRepository = NotificationSettingRepository(settingDao)
    }

    @Test
    fun testToggleNotificationSetting() = runTest {
        notificationSettingRepository.toggleNotificationSetting(simklId = 77, notifyEveryEpisode = true, notifyAiredLastEpisode = false)

        coVerify {
            settingDao.saveSetting(
                NotificationSetting(
                    simklId = 77,
                    notifyEveryEpisode = true,
                    notifyAiredLastEpisode = false
                )
            )
        }
    }
}

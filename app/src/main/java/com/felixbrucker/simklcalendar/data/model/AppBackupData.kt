package com.felixbrucker.simklcalendar.data.model

import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsPreferences
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadPreferences
import com.felixbrucker.simklcalendar.data.preferences.NotificationPreferences
import com.felixbrucker.simklcalendar.data.preferences.UiPreferences
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppBackupData(
    val version: Int = 1,
    val appSettings: AppSettingsPreferences? = null,
    val autoDownload: AutoDownloadPreferences? = null,
    val notification: NotificationPreferences? = null,
    val ui: UiPreferences? = null,
    val notificationSettings: List<NotificationSetting> = emptyList(),
    val itemDownloadSettings: List<ItemDownloadSettings> = emptyList(),
    val customSearchLinks: List<CustomSearchLink> = emptyList()
)

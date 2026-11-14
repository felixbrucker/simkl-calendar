package com.felixbrucker.simklcalendar.data.backup

import android.content.Context
import android.net.Uri
import com.felixbrucker.simklcalendar.data.database.CustomSearchLinkDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.AppBackupData
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsRepository
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.preferences.NotificationRepository
import com.felixbrucker.simklcalendar.data.preferences.UiRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettingsRepo: AppSettingsRepository,
    private val autoDownloadRepo: AutoDownloadRepository,
    private val notificationRepo: NotificationRepository,
    private val uiRepo: UiRepository,
    private val notificationSettingDao: NotificationSettingDao,
    private val itemDownloadSettingsDao: ItemDownloadSettingsDao,
    private val customSearchLinkDao: CustomSearchLinkDao,
    private val watchlistDao: WatchlistDao,
    private val moshi: Moshi
) {
    suspend fun exportSettings(
        uri: Uri,
        exportAppSettings: Boolean,
        exportNotificationSettings: Boolean,
        exportDownloadSettings: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val appSettings = if (exportAppSettings) appSettingsRepo.preferencesFlow.first() else null
            val autoDownload = if (exportAppSettings) autoDownloadRepo.preferencesFlow.first() else null
            val notification = if (exportAppSettings) notificationRepo.preferencesFlow.first() else null
            val ui = if (exportAppSettings) uiRepo.preferencesFlow.first() else null
            val searchLinks = if (exportAppSettings) customSearchLinkDao.getAllSearchLinksList() else emptyList()

            val notifSettings = if (exportNotificationSettings) notificationSettingDao.getAllNotificationSettings() else emptyList()
            val downloadSettings = if (exportDownloadSettings) itemDownloadSettingsDao.getAllItemDownloadSettings() else emptyList()

            val backupData = AppBackupData(
                version = 1,
                appSettings = appSettings,
                autoDownload = autoDownload,
                notification = notification,
                ui = ui,
                customSearchLinks = searchLinks,
                notificationSettings = notifSettings,
                itemDownloadSettings = downloadSettings
            )

            val json = moshi.adapter(AppBackupData::class.java).toJson(backupData)
                ?: throw IllegalAccessException("Failed to serialize backup data")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.writer().use { it.write(json) }
            } ?: throw IllegalAccessException("Could not open output stream for URI")

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export settings")
            Result.failure(e)
        }
    }

    suspend fun readBackupMetadata(uri: Uri): Result<AppBackupData> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: throw IllegalAccessException("Could not open input stream for URI")

            val backupData = moshi.adapter(AppBackupData::class.java).fromJson(json)
                ?: throw IllegalAccessException("Invalid backup file content")

            Result.success(backupData)
        } catch (e: Exception) {
            Timber.e(e, "Failed to read backup metadata")
            Result.failure(e)
        }
    }

    suspend fun importSettings(
        backupData: AppBackupData,
        importAppSettings: Boolean,
        importNotificationSettings: Boolean,
        importDownloadSettings: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (importAppSettings) {
                backupData.appSettings?.let { appSettingsRepo.setAll(it) }
                backupData.autoDownload?.let { autoDownloadRepo.setAll(it) }
                backupData.notification?.let { notificationRepo.setAll(it) }
                backupData.ui?.let { uiRepo.setAllPreferences(it) }

                if (backupData.customSearchLinks.isNotEmpty()) {
                    customSearchLinkDao.clearAll()
                    for (link in backupData.customSearchLinks) {
                        customSearchLinkDao.insertSearchLink(link.copy(id = 0))
                    }
                }
            }

            val trackedIds = watchlistDao.getAllTrackedIds().toSet()
            var importedNotifCount = 0
            var excludedNotifCount = 0

            if (importNotificationSettings) {
                for (notifSetting in backupData.notificationSettings) {
                    if (trackedIds.contains(notifSetting.simklId)) {
                        notificationSettingDao.saveSetting(notifSetting)
                        importedNotifCount++
                    } else {
                        excludedNotifCount++
                    }
                }
            } else {
                excludedNotifCount = backupData.notificationSettings.size
            }

            var importedDlCount = 0
            var excludedDlCount = 0

            if (importDownloadSettings) {
                for (dlSetting in backupData.itemDownloadSettings) {
                    if (trackedIds.contains(dlSetting.simklId)) {
                        itemDownloadSettingsDao.insertOrUpdate(dlSetting)
                        importedDlCount++
                    } else {
                        excludedDlCount++
                    }
                }
            } else {
                excludedDlCount = backupData.itemDownloadSettings.size
            }

            val parts = mutableListOf<String>()
            if (importAppSettings) {
                parts.add("appSettings=true")
                parts.add("customSearchLinks=${backupData.customSearchLinks.size}")
            }
            if (importNotificationSettings) {
                parts.add("notificationSettings=$importedNotifCount")
                if (excludedNotifCount > 0) {
                    parts.add("excludedNotificationSettings=$excludedNotifCount")
                }
            }
            if (importDownloadSettings) {
                parts.add("downloadSettings=$importedDlCount")
                if (excludedDlCount > 0) {
                    parts.add("excludedDownloadSettings=$excludedDlCount")
                }
            }
            Timber.i("Settings imported: version=${backupData.version}, ${parts.joinToString(", ")}")

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import settings")
            Result.failure(e)
        }
    }
}

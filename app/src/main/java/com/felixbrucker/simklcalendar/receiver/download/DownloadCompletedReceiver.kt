package com.felixbrucker.simklcalendar.receiver.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DownloadCompletedReceiver: BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    companion object {
        private const val TAG = "DownloadCompletedReceiver"
        const val ACTION_DOWNLOAD_COMPLETED = "com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_COMPLETED"
        const val EXTRA_ITEM_PRIMARY_KEY = "extra_item_primary_key"
    }
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != ACTION_DOWNLOAD_COMPLETED) {
            return
        }
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        val pendingResult = goAsync()
        scope.launch {
            try {
                val repo = SimklRepository(context)
                repo.updateDownloadTaskId(itemPrimaryKey, null, MediaStatus.DOWNLOADED)
                Log.d(TAG, "Updated item $itemPrimaryKey to DOWNLOADED status and cleared taskId")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling download completion", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

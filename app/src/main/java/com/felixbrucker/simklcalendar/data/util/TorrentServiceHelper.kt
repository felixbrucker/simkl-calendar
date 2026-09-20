package com.felixbrucker.simklcalendar.data.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import timber.log.Timber
import com.felixbrucker.torrenthttpdownloader.AddTorrentParams
import com.felixbrucker.torrenthttpdownloader.IAddTorrentCallback
import com.felixbrucker.torrenthttpdownloader.ITorrentDownloadService
import com.felixbrucker.torrenthttpdownloader.TorrentProgressStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class DownloadProgress(
    val taskId: String,
    val uri: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val downloadSpeed: Double,
    val isCompleted: Boolean = false,
    val error: String? = null
)

class TorrentServiceHelper(context: Context) {
    private val TAG = "TorrentServiceHelper"
    private val appContext = context.applicationContext
    private val SERVICE_PACKAGE = "com.felixbrucker.torrenthttpdownloader"
    private val SERVICE_ACTION = "com.felixbrucker.torrenthttpdownloader.ITorrentDownloadService"

    private val _service = MutableStateFlow<ITorrentDownloadService?>(null)
    val service: StateFlow<ITorrentDownloadService?> = _service.asStateFlow()

    private val _isBound = MutableStateFlow(false)
    val isBound: StateFlow<Boolean> = _isBound.asStateFlow()

    private val _isInstalled = MutableStateFlow(false)
    val isInstalled: StateFlow<Boolean> = _isInstalled.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    private val taskIdToUri = mutableMapOf<String, String>()

    init {
        refreshServiceStatus()
    }

    fun refreshServiceStatus() {
        val installed = checkIsInstalled()
        _isInstalled.value = installed
        if (!installed && _isBound.value) {
            unbind()
        }
    }

    private fun checkIsInstalled(): Boolean {
        return try {
            appContext.packageManager.getPackageInfo(SERVICE_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Timber.tag(TAG).d("Service connected")
            val serviceInterface = ITorrentDownloadService.Stub.asInterface(binder)
            _service.value = serviceInterface
            _isBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.tag(TAG).d("Service disconnected")
            _service.value = null
            _isBound.value = false
        }
    }

    fun bind(start: Boolean = false) {
        if (_isBound.value) return

        val intent = Intent(SERVICE_ACTION).apply {
            setPackage(SERVICE_PACKAGE)
        }

        try {
            if (start) {
                appContext.startForegroundService(intent)
            }
            val success = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!success) {
                Timber.tag(TAG).e("Failed to bind to Torrent Download Service")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error binding to service")
        }
    }

    fun unbind() {
        if (_isBound.value) {
            appContext.unbindService(connection)
            _service.value = null
            _isBound.value = false
        }
    }

    fun getProgress(taskId: String): TorrentProgressStats? {
        val s = _service.value ?: throw Exception("Service not available")
        return s.getProgress(taskId)
    }

    fun updateDownloadProgress(taskId: String, stats: TorrentProgressStats) {
        val uri = taskIdToUri[taskId]
        _downloads.value += (taskId to DownloadProgress(
                    taskId = taskId,
                    uri = uri,
                    bytesDownloaded = stats.bytesDownloaded,
                    totalBytes = stats.totalBytes,
                    downloadSpeed = stats.downloadSpeed
                ))
    }

    fun clearDownload(taskId: String) {
        _downloads.value -= taskId
        taskIdToUri.remove(taskId)
    }

    suspend fun addTorrent(
        uri: String,
        name: String?,
        destinationSubdirectory: String?,
        createSubfolderByName: Boolean = true,
        notifyOnCompletion: Boolean = true,
        fileSelectionMode: String = "ALL",
        onCompletionIntentUri: String? = null
    ): Result<String> {
        if (!_isBound.value) {
            bind(start = true)
        }

        val s = try {
            _service.filterNotNull().first()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return suspendCancellableCoroutine { continuation ->
            val params = AddTorrentParams().apply {
                this.uri = uri
                this.name = name
                this.destinationSubdirectory = destinationSubdirectory
                this.createSubfolderByName = createSubfolderByName
                this.notifyOnCompletion = notifyOnCompletion
                this.fileSelectionMode = fileSelectionMode
                this.onCompletionIntentUri = onCompletionIntentUri
            }

            try {
                s.addTorrent(params, object : IAddTorrentCallback.Stub() {
                    override fun onSuccess(taskId: String) {
                        taskIdToUri[taskId] = uri
                        if (continuation.isActive) {
                            continuation.resume(Result.success(taskId))
                        }
                    }

                    override fun onFailure(error: String) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception(error)))
                        }
                    }
                })
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }

    fun isServiceInstalled(): Boolean {
        return _isInstalled.value
    }

    companion object {
        @Volatile
        private var INSTANCE: TorrentServiceHelper? = null

        fun getInstance(context: Context): TorrentServiceHelper {
            return INSTANCE ?: synchronized(this) {
                val instance = TorrentServiceHelper(context)
                INSTANCE = instance
                instance
            }
        }
    }
}

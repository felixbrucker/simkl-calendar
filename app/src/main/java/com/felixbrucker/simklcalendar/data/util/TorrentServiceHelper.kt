package com.felixbrucker.simklcalendar.data.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.felixbrucker.torrenthttpdownloader.AddTorrentParams
import com.felixbrucker.torrenthttpdownloader.IAddTorrentCallback
import com.felixbrucker.torrenthttpdownloader.IRemoveTorrentCallback
import com.felixbrucker.torrenthttpdownloader.ITorrentDownloadCallback
import com.felixbrucker.torrenthttpdownloader.ITorrentDownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        if (installed && !_isBound.value) {
            bind()
        } else if (!installed && _isBound.value) {
            unbind()
        }
    }

    private fun checkIsInstalled(): Boolean {
        return try {
            appContext.packageManager.getPackageInfo(SERVICE_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private val downloadCallback = object : ITorrentDownloadCallback.Stub() {
        override fun onProgressUpdate(taskId: String, bytesDownloaded: Long, totalBytes: Long, downloadSpeed: Double) {
            val uri = taskIdToUri[taskId]
            _downloads.value = _downloads.value + (taskId to DownloadProgress(taskId, uri, bytesDownloaded, totalBytes, downloadSpeed))
        }

        override fun onDownloadCompleted(taskId: String) {
            val current = _downloads.value[taskId]
            if (current != null) {
                _downloads.value = _downloads.value + (taskId to current.copy(isCompleted = true))
            }
        }

        override fun onDownloadFailed(taskId: String, error: String) {
            val current = _downloads.value[taskId]
            if (current != null) {
                _downloads.value = _downloads.value + (taskId to current.copy(error = error))
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service connected")
            val serviceInterface = ITorrentDownloadService.Stub.asInterface(binder)
            _service.value = serviceInterface
            _isBound.value = true

            try {
                serviceInterface.registerCallback(downloadCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error registering callback", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            _service.value = null
            _isBound.value = false
        }
    }

    fun bind() {
        if (_isBound.value) return

        val intent = Intent(SERVICE_ACTION).apply {
            setPackage(SERVICE_PACKAGE)
        }

        try {
            val success = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!success) {
                Log.e(TAG, "Failed to bind to Torrent Download Service")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error binding to service", e)
        }
    }

    fun unbind() {
        if (_isBound.value) {
            try {
                _service.value?.unregisterCallback(downloadCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering callback", e)
            }
            appContext.unbindService(connection)
            _service.value = null
            _isBound.value = false
        }
    }

    fun addTorrent(
        uri: String,
        name: String?,
        destinationSubdirectory: String?,
        createSubfolderByName: Boolean = true,
        notifyOnCompletion: Boolean = true,
        fileSelectionMode: String = "ALL",
        onResult: (Boolean, String?) -> Unit
    ) {
        val s = _service.value
        if (s == null) {
            onResult(false, "Service not connected")
            return
        }

        val params = AddTorrentParams().apply {
            this.uri = uri
            this.name = name
            this.destinationSubdirectory = destinationSubdirectory
            this.createSubfolderByName = createSubfolderByName
            this.notifyOnCompletion = notifyOnCompletion
            this.fileSelectionMode = fileSelectionMode
        }

        try {
            s.addTorrent(params, object : IAddTorrentCallback.Stub() {
                override fun onSuccess(taskId: String) {
                    taskIdToUri[taskId] = uri
                    onResult(true, taskId)
                }

                override fun onFailure(error: String) {
                    onResult(false, error)
                }
            })
        } catch (e: Exception) {
            onResult(false, e.message)
        }
    }

    fun removeTorrent(taskId: String, deleteFiles: Boolean, deleteTorrentFile: Boolean, onResult: (Boolean, String?) -> Unit) {
        val s = _service.value
        if (s == null) {
            onResult(false, "Service not connected")
            return
        }

        try {
            s.removeTorrent(taskId, deleteFiles, deleteTorrentFile, object : IRemoveTorrentCallback.Stub() {
                override fun onSuccess(id: String) {
                    _downloads.value = _downloads.value - id
                    onResult(true, null)
                }

                override fun onFailure(error: String) {
                    onResult(false, error)
                }
            })
        } catch (e: Exception) {
            onResult(false, e.message)
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

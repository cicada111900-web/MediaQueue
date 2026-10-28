 package com.example.mediaqueue.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediaqueue.data.AppDatabase
import com.example.mediaqueue.data.DownloadItem
import com.example.mediaqueue.data.DownloadStatus
import com.example.mediaqueue.download.DirectDownloadEngine
import com.example.mediaqueue.FloatingButtonService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MediaQueueViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).downloadDao()

    private val engine = DirectDownloadEngine(
        File(app.getExternalFilesDir("Downloads"), "MediaQueue")
    )

    private val running = mutableSetOf<String>()
    private val maxConcurrent = 1

    private val _floatingButtonEnabled = MutableStateFlow(false)
    val floatingButtonEnabled: StateFlow<Boolean> = _floatingButtonEnabled

    val items: StateFlow<List<DownloadItem>> =
        dao.observeAll().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    init {
        viewModelScope.launch {
            val savedItems = dao.activeItems()
            savedItems.filter { it.status == DownloadStatus.DOWNLOADING }
                .forEach {
                    dao.updateStatus(it.id, DownloadStatus.QUEUED)
                }
            pumpQueue()
        }
    }

    fun addUrl(url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") &&
            !cleanUrl.startsWith("https://")
        ) return

        viewModelScope.launch {
            val position = (items.value.maxOfOrNull { it.queuePosition } ?: -1) + 1
            dao.upsert(
                DownloadItem(
                    id = UUID.randomUUID().toString(),
                    url = cleanUrl,
                    queuePosition = position
                )
            )
            pumpQueue()
        }
    }

    fun setFloatingButtonEnabled(enabled: Boolean) {
        val app = getApplication<Application>()

        if (enabled && !Settings.canDrawOverlays(app)) {
            _floatingButtonEnabled.value = false
            return
        }

        _floatingButtonEnabled.value = enabled

        val intent = Intent(app, FloatingButtonService::class.java)

        if (enabled) {
            app.startService(intent)
        } else {
            app.stopService(intent)
        }
    }

    fun pause(id: String) {
        viewModelScope.launch {
            running.remove(id)
            engine.stop(id)
            dao.updateStatus(id, DownloadStatus.PAUSED)
            pumpQueue()
        }
    }

    fun resume(id: String) {
        viewModelScope.launch {
            dao.updateStatus(id, DownloadStatus.QUEUED)
            pumpQueue()
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch {
            running.remove(id)
            engine.stop(id)
            dao.updateStatus(id, DownloadStatus.CANCELED)
            pumpQueue()
        }
    }

    fun retry(id: String) {
        viewModelScope.launch {
            dao.updateStatus(id, DownloadStatus.QUEUED)
            pumpQueue()
        }
    }

    private suspend fun pumpQueue() {
        val all = dao.activeItems()
        val activeCount = running.size

        if (activeCount >= maxConcurrent) return

        val next = all.firstOrNull {
            it.status == DownloadStatus.QUEUED && it.id !in running
        } ?: return

        running.add(next.id)
        dao.updateStatus(next.id, DownloadStatus.DOWNLOADING)

        engine.start(
            next,
            onProgress = { updated ->
                viewModelScope.launch {
                    dao.upsert(updated)
                }
            },
            onFinished = { finished ->
                viewModelScope.launch {
                    running.remove(next.id)
                    dao.upsert(finished)
                    pumpQueue()
                }
            }
        )
    }
}

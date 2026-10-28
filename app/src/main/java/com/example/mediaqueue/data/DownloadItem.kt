 package com.example.mediaqueue.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String? = null,
    val format: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progressPercent: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val queuePosition: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

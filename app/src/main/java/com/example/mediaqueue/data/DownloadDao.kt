 package com.example.mediaqueue.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY queuePosition ASC, createdAt ASC")
    fun observeAll(): Flow<List<DownloadItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DownloadItem>)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus)

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED') ORDER BY queuePosition ASC, createdAt ASC")
    suspend fun activeItems(): List<DownloadItem>

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)
}

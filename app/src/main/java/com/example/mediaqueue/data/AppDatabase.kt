 package com.example.mediaqueue.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import android.content.Context

class DownloadConverters {

    @TypeConverter
    fun statusToString(status: DownloadStatus): String {
        return status.name
    }

    @TypeConverter
    fun stringToStatus(value: String): DownloadStatus {
        return DownloadStatus.valueOf(value)
    }
}

@Database(
    entities = [DownloadItem::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DownloadConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mediaqueue.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

package com.aniplex.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aniplex.app.data.local.dao.CacheDao
import com.aniplex.app.data.local.dao.DownloadDao
import com.aniplex.app.data.local.entity.CacheEntity
import com.aniplex.app.data.local.entity.DownloadEntity

@Database(entities = [CacheEntity::class, DownloadEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun downloadDao(): DownloadDao
}

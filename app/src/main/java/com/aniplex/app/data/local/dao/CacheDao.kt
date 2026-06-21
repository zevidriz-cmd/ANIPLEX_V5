package com.aniplex.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aniplex.app.data.local.entity.CacheEntity

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: CacheEntity)

    @Query("SELECT * FROM api_cache WHERE cacheKey = :key")
    suspend fun getCache(key: String): CacheEntity?

    @Query("DELETE FROM api_cache WHERE cacheKey = :key")
    suspend fun deleteCache(key: String)

    @Query("DELETE FROM api_cache")
    suspend fun clearAllCache()
}

package com.aniplex.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_cache")
data class CacheEntity(
    @PrimaryKey val cacheKey: String,
    val jsonContent: String,
    val timestamp: Long
)

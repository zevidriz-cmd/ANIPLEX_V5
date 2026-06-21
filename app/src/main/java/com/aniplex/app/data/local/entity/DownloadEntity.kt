package com.aniplex.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val episodeId: String,
    val animeId: String,
    val animeTitle: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val posterUrl: String,
    val videoUrl: String?,
    val localFilePath: String?,
    val progress: Float,
    val status: String, // "QUEUED", "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED"
    val profileId: String?
)

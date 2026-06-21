package com.aniplex.app.data.local.dao

import androidx.room.*
import com.aniplex.app.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE episodeId = :episodeId")
    suspend fun getDownload(episodeId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE profileId = :profileId OR (profileId IS NULL AND :profileId IS NULL)")
    fun getDownloadsForProfile(profileId: String?): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloads(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE episodeId = :episodeId")
    suspend fun deleteDownload(episodeId: String)

    @Query("UPDATE downloads SET progress = :progress, status = :status WHERE episodeId = :episodeId")
    suspend fun updateProgress(episodeId: String, progress: Float, status: String)

    @Query("UPDATE downloads SET status = :status WHERE episodeId = :episodeId")
    suspend fun updateStatus(episodeId: String, status: String)

    @Query("UPDATE downloads SET localFilePath = :filePath, status = :status WHERE episodeId = :episodeId")
    suspend fun markCompleted(episodeId: String, filePath: String, status: String = "COMPLETED")
}

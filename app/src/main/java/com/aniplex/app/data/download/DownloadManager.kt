package com.aniplex.app.data.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

data class DownloadTask(
    val episodeId: String,
    val animeId: String,
    val animeTitle: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val posterUrl: String,
    val progress: MutableStateFlow<Float> = MutableStateFlow(0f),
    val status: MutableStateFlow<DownloadStatus> = MutableStateFlow(DownloadStatus.QUEUED),
    var videoUrl: String? = null
)

object DownloadManager {
    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloads: StateFlow<List<DownloadTask>> = _downloads.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var downloadDao: com.aniplex.app.data.local.dao.DownloadDao? = null
    private var appContext: Context? = null
    private var activeProfileId: String? = null
    private var observationJob: Job? = null

    // Captured media stream URLs from the WebView
    private val _streamUrls = mutableMapOf<String, String>()

    fun setStreamUrl(episodeId: String, url: String) {
        synchronized(_streamUrls) {
            _streamUrls[episodeId] = url
        }
    }

    fun getStreamUrl(episodeId: String): String? {
        return synchronized(_streamUrls) {
            _streamUrls[episodeId]
        }
    }

    fun setActiveProfileId(profileId: String?) {
        activeProfileId = profileId
        restartDatabaseObservation()
    }

    fun loadDownloads(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext

        // Initialize Room DB
        val db = androidx.room.Room.databaseBuilder(
            applicationContext,
            com.aniplex.app.data.local.database.AppDatabase::class.java,
            "aniplex_db"
        ).fallbackToDestructiveMigration().build()
        downloadDao = db.downloadDao()

        restartDatabaseObservation()
    }

    private fun restartDatabaseObservation() {
        val dao = downloadDao ?: return
        
        observationJob?.cancel()
        observationJob = scope.launch {
            val flow = if (activeProfileId != null) {
                dao.getDownloadsForProfile(activeProfileId)
            } else {
                dao.getAllDownloadsFlow()
            }

            flow.collect { entities ->
                val currentTasks = _downloads.value
                val updatedTasks = entities.map { entity ->
                    val existing = currentTasks.find { it.episodeId == entity.episodeId }
                    if (existing != null) {
                        existing.progress.value = entity.progress
                        existing.status.value = try {
                            DownloadStatus.valueOf(entity.status)
                        } catch (e: Exception) {
                            DownloadStatus.QUEUED
                        }
                        existing.videoUrl = entity.videoUrl
                        existing
                    } else {
                        DownloadTask(
                            episodeId = entity.episodeId,
                            animeId = entity.animeId,
                            animeTitle = entity.animeTitle,
                            episodeNumber = entity.episodeNumber,
                            episodeTitle = entity.episodeTitle,
                            posterUrl = entity.posterUrl,
                            progress = MutableStateFlow(entity.progress),
                            status = MutableStateFlow(
                                try {
                                    DownloadStatus.valueOf(entity.status)
                                } catch (e: Exception) {
                                    DownloadStatus.QUEUED
                                }
                            ),
                            videoUrl = entity.videoUrl
                        )
                    }
                }
                _downloads.value = updatedTasks
            }
        }
    }

    fun startDownload(
        context: Context,
        episodeId: String,
        animeId: String,
        animeTitle: String,
        episodeNumber: Int,
        episodeTitle: String,
        posterUrl: String,
        videoUrl: String? = null
    ) {
        val dao = downloadDao ?: return
        scope.launch(Dispatchers.IO) {
            val existing = dao.getDownload(episodeId)
            if (existing != null) {
                val currentStatus = try {
                    DownloadStatus.valueOf(existing.status)
                } catch (e: Exception) {
                    DownloadStatus.FAILED
                }
                if (currentStatus == DownloadStatus.PAUSED || currentStatus == DownloadStatus.FAILED) {
                    resumeDownload(context, episodeId, videoUrl)
                }
                return@launch
            }

            val finalUrl = videoUrl ?: getStreamUrl(episodeId)
            val entity = com.aniplex.app.data.local.entity.DownloadEntity(
                episodeId = episodeId,
                animeId = animeId,
                animeTitle = animeTitle,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                posterUrl = posterUrl,
                videoUrl = finalUrl,
                localFilePath = null,
                progress = 0f,
                status = "QUEUED",
                profileId = activeProfileId
            )
            dao.insertDownload(entity)
            enqueueDownloadWork(context, episodeId)
        }
    }

    private fun enqueueDownloadWork(context: Context, episodeId: String) {
        val constraintsBuilder = androidx.work.Constraints.Builder()
        
        // Read preference manager
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        val downloadOverCellular = userId?.let {
            com.aniplex.app.data.local.preferences.PreferenceManager(context).downloadOverCellular
        } ?: false
        
        if (!downloadOverCellular) {
            constraintsBuilder.setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
        } else {
            constraintsBuilder.setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
        }

        val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(androidx.work.workDataOf("episodeId" to episodeId))
            .setConstraints(constraintsBuilder.build())
            .addTag("download_$episodeId")
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "download_$episodeId",
            androidx.work.ExistingWorkPolicy.KEEP,
            downloadRequest
        )
    }

    fun pauseDownload(context: Context, episodeId: String) {
        scope.launch(Dispatchers.IO) {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("download_$episodeId")
            downloadDao?.updateStatus(episodeId, "PAUSED")
        }
    }

    fun resumeDownload(context: Context, episodeId: String, videoUrl: String? = null) {
        scope.launch(Dispatchers.IO) {
            val dao = downloadDao ?: return@launch
            val task = dao.getDownload(episodeId) ?: return@launch
            val updatedVideoUrl = videoUrl ?: task.videoUrl ?: getStreamUrl(episodeId)

            if (updatedVideoUrl != task.videoUrl) {
                dao.insertDownload(task.copy(videoUrl = updatedVideoUrl, status = "QUEUED"))
            } else {
                dao.updateStatus(episodeId, "QUEUED")
            }
            enqueueDownloadWork(context, episodeId)
        }
    }

    fun cancelDownload(context: Context, episodeId: String) {
        scope.launch(Dispatchers.IO) {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("download_$episodeId")
            deleteDownloadedFiles(context, episodeId)
            downloadDao?.deleteDownload(episodeId)
        }
    }

    fun deleteDownloadedFiles(context: Context, episodeId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = File(context.filesDir, "downloads")
                val mp4File = File(downloadsDir, "$episodeId.mp4")
                if (mp4File.exists()) {
                    mp4File.delete()
                }
                val hlsDir = File(downloadsDir, episodeId)
                if (hlsDir.exists()) {
                    hlsDir.deleteRecursively()
                }
            } catch (e: java.lang.Exception) {
                Log.e("DownloadManager", "Failed to delete downloaded files for $episodeId", e)
            }
        }
    }

    fun getDownloadedFile(context: Context, episodeId: String): File? {
        val downloadsDir = File(context.filesDir, "downloads")
        val mp4File = File(downloadsDir, "$episodeId.mp4")
        if (mp4File.exists() && mp4File.length() > 0) {
            return mp4File
        }
        val hlsFile = File(File(downloadsDir, episodeId), "index.m3u8")
        if (hlsFile.exists() && hlsFile.length() > 0) {
            return hlsFile
        }
        return null
    }

    private fun saveDownloads(context: Context) {}
}

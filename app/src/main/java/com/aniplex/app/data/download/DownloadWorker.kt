package com.aniplex.app.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.aniplex.app.R
import com.aniplex.app.data.local.dao.DownloadDao
import com.aniplex.app.data.local.database.AppDatabase
import com.aniplex.app.data.local.entity.DownloadEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

import dagger.hilt.android.EntryPointAccessors

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "downloads_channel"
    private val notificationId = 1001

    private var downloadDao: DownloadDao? = null

    companion object {
        private const val TAG = "DownloadWorker"
    }

    override suspend fun doWork(): Result {
        val episodeId = inputData.getString("episodeId") ?: return Result.failure()
        
        // Retrieve Room DB DownloadDao via Hilt EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadEntryPoint::class.java
        )
        downloadDao = entryPoint.downloadDao()

        val task = downloadDao?.getDownload(episodeId) ?: return Result.failure()
        
        createNotificationChannel()
        setForeground(createForegroundInfo(task.animeTitle, task.episodeTitle, 0))

        return withContext(Dispatchers.IO) {
            try {
                downloadDao?.updateStatus(episodeId, "DOWNLOADING")
                
                val downloadsDir = File(applicationContext.filesDir, "downloads")
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                var downloadUrl = task.videoUrl
                if (downloadUrl.isNullOrEmpty()) {
                    Log.d(TAG, "No cached stream URL. Resolving dynamically for episode $episodeId...")
                    downloadUrl = fetchStreamUrlFromApi(episodeId)
                    if (downloadUrl.isNullOrEmpty()) {
                        throw Exception("No stream URL available to download (resolution failed)")
                    }
                    // Save resolved videoUrl to database
                    downloadDao?.insertDownload(task.copy(videoUrl = downloadUrl))
                }

                Log.d(TAG, "Starting download for $episodeId from $downloadUrl")

                val client = OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()

                val localPath = if (downloadUrl.contains(".m3u8") || downloadUrl.contains("/hls/")) {
                    downloadHls(task, downloadUrl, downloadsDir, client)
                } else {
                    downloadMp4(task, downloadUrl, downloadsDir, client)
                }

                downloadDao?.markCompleted(episodeId, localPath)
                showCompletedNotification(task.animeTitle, task.episodeTitle)
                Result.success()
            } catch (e: CancellationException) {
                Log.d(TAG, "Download cancelled/paused for $episodeId")
                downloadDao?.updateStatus(episodeId, "PAUSED")
                notificationManager.cancel(notificationId)
                Result.success() // Pausing is a graceful cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $episodeId", e)
                downloadDao?.updateStatus(episodeId, "FAILED")
                showFailedNotification(task.animeTitle, task.episodeTitle)
                Result.failure()
            }
        }
    }

    private suspend fun fetchStreamUrlFromApi(episodeId: String): String? {
        return withContext(Dispatchers.IO) {
            val serversToTry = listOf("hd-1", "rapidcloud", "megastream")
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            
            for (srv in serversToTry) {
                try {
                    val baseUrl = "https://aniplex-proxy.f1886391.workers.dev/"
                    val url = "${baseUrl}api/v2/episode/sources?animeEpisodeId=$episodeId&server=$srv&category=sub"
                    Log.d(TAG, "Trying download resolve for server=$srv; URL: $url")
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept", "application/json")
                        .build()
                    
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: continue
                        val json = org.json.JSONObject(bodyString)
                        if (json.optBoolean("success", false)) {
                            val dataObj = json.optJSONObject("data")
                            if (dataObj != null) {
                                val sourcesArray = dataObj.optJSONArray("sources")
                                if (sourcesArray != null && sourcesArray.length() > 0) {
                                    val firstSource = sourcesArray.optJSONObject(0)
                                    if (firstSource != null) {
                                        val streamUrl = firstSource.optString("url")
                                        if (streamUrl.isNotEmpty()) {
                                            Log.d(TAG, "Resolved stream URL via server $srv: $streamUrl")
                                            return@withContext streamUrl
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed resolving download stream URL via server $srv", e)
                }
            }
            null
        }
    }

    private suspend fun downloadMp4(
        task: DownloadEntity,
        videoUrl: String,
        downloadsDir: File,
        client: OkHttpClient
    ): String {
        val file = File(downloadsDir, "${task.episodeId}.mp4")
        val request = Request.Builder().url(videoUrl).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Failed to download MP4: ${response.code}")
        val body = response.body ?: throw Exception("Empty response body")
        val contentLength = body.contentLength()
        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(file)
        val buffer = ByteArray(16384)
        var bytesRead: Int
        var totalBytesRead = 0L

        inputStream.use { input ->
            outputStream.use { output ->
                while (true) {
                    if (isStopped) {
                        throw CancellationException("Download stopped by WorkManager")
                    }
                    bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val progress = totalBytesRead.toFloat() / contentLength
                        downloadDao?.updateProgress(task.episodeId, progress, "DOWNLOADING")
                        updateProgressNotification(task.animeTitle, task.episodeTitle, (progress * 100).toInt())
                    }
                }
            }
        }
        return file.absolutePath
    }

    private suspend fun downloadHls(
        task: DownloadEntity,
        m3u8Url: String,
        downloadsDir: File,
        client: OkHttpClient
    ): String {
        val baseDir = File(downloadsDir, task.episodeId)
        if (!baseDir.exists()) baseDir.mkdirs()

        val responseText = client.newCall(Request.Builder().url(m3u8Url).build()).execute().body?.string()
            ?: throw Exception("Failed to fetch HLS playlist")

        var targetM3u8Url = m3u8Url
        var playlistText = responseText

        // Check if it is a master playlist
        if (responseText.contains("#EXT-X-STREAM-INF")) {
            val lines = responseText.split("\n")
            var subPlaylistPath = ""
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    subPlaylistPath = trimmed
                    break
                }
            }
            if (subPlaylistPath.isNotEmpty()) {
                val resolvedUrl = resolveUrl(m3u8Url, subPlaylistPath)
                targetM3u8Url = resolvedUrl
                playlistText = client.newCall(Request.Builder().url(resolvedUrl).build()).execute().body?.string()
                    ?: throw Exception("Failed to fetch sub-playlist")
            }
        }

        val lines = playlistText.split("\n")
        val segmentUrls = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                segmentUrls.add(resolveUrl(targetM3u8Url, trimmed))
            }
        }

        val totalSegments = segmentUrls.size
        if (totalSegments == 0) throw Exception("No segments found in playlist")

        val localM3u8Content = StringBuilder()
        var segmentIndex = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                localM3u8Content.append("\n")
            } else if (trimmed.startsWith("#")) {
                localM3u8Content.append(trimmed).append("\n")
            } else {
                val segUrl = segmentUrls[segmentIndex]
                val segFileName = "segment_$segmentIndex.ts"
                val segFile = File(baseDir, segFileName)

                // Download segment
                var success = false
                var retries = 3
                while (!success && retries > 0) {
                    if (isStopped) {
                        throw CancellationException("Download stopped by WorkManager")
                    }
                    try {
                        val req = Request.Builder().url(segUrl).build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            resp.body?.byteStream()?.use { input ->
                                FileOutputStream(segFile).use { output ->
                                    val buffer = ByteArray(8192)
                                    var bytes: Int
                                    while (input.read(buffer).also { bytes = it } != -1) {
                                        if (isStopped) {
                                            throw CancellationException("Download stopped by WorkManager")
                                        }
                                        output.write(buffer, 0, bytes)
                                    }
                                }
                            }
                            success = true
                        } else {
                            retries--
                            delay(500)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        retries--
                        delay(500)
                    }
                }
                if (!success) throw Exception("Failed to download HLS segment $segmentIndex")

                localM3u8Content.append(segFileName).append("\n")
                segmentIndex++

                // Update progress
                val progress = segmentIndex.toFloat() / totalSegments
                downloadDao?.updateProgress(task.episodeId, progress, "DOWNLOADING")
                updateProgressNotification(task.animeTitle, task.episodeTitle, (progress * 100).toInt())
            }
        }

        // Write rewritten local m3u8 file
        val localM3u8File = File(baseDir, "index.m3u8")
        localM3u8File.writeText(localM3u8Content.toString())
        return localM3u8File.absolutePath
    }

    private fun resolveUrl(baseUrl: String, relativePath: String): String {
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath
        }
        return try {
            val uri = URI(baseUrl)
            uri.resolve(relativePath).toString()
        } catch (e: Exception) {
            relativePath
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows downloading progress for episodes"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(animeTitle: String, episodeTitle: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading $animeTitle")
            .setContentText(episodeTitle)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateProgressNotification(animeTitle: String, episodeTitle: String, progress: Int) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading $animeTitle")
            .setContentText("$episodeTitle ($progress%)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun showCompletedNotification(animeTitle: String, episodeTitle: String) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Download Completed")
            .setContentText("$animeTitle - $episodeTitle")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(episodeTitle.hashCode(), notification)
    }

    private fun showFailedNotification(animeTitle: String, episodeTitle: String) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Download Failed")
            .setContentText("$animeTitle - $episodeTitle")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(episodeTitle.hashCode(), notification)
    }
}

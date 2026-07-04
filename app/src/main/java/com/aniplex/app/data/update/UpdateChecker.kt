package com.aniplex.app.data.update

import android.util.Log
import com.aniplex.app.BuildConfig
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = BuildConfig.UPDATE_URL
            Log.d("UpdateChecker", "Checking for updates at: $url")
            if (url == "mock://update") {
                Log.d("UpdateChecker", "Using mock update check")
                return@withContext UpdateInfo(
                    versionCode = BuildConfig.VERSION_CODE + 1,
                    versionName = "1.2.3-mock",
                    downloadUrl = "mock://download",
                    changelog = "• This is a mock OTA update for testing\n• Focusable button scaling works\n• Automatic download progress simulation"
                )
            }
            val request = Request.Builder()
                .url(url)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("UpdateChecker", "Failed to check updates: HTTP ${response.code}")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val remoteUpdate = gson.fromJson(bodyString, UpdateInfo::class.java)

                Log.d("UpdateChecker", "Remote update: $remoteUpdate, Current versionCode: ${BuildConfig.VERSION_CODE}")
                if (remoteUpdate.versionCode > BuildConfig.VERSION_CODE) {
                    return@withContext remoteUpdate
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Error checking for updates", e)
        }
        null
    }
}

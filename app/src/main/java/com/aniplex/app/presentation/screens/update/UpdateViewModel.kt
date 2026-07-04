package com.aniplex.app.presentation.screens.update

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.data.update.UpdateChecker
import com.aniplex.app.data.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking = _isChecking.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    var checkDismissed = false
        private set

    fun checkForUpdates(onComplete: (UpdateInfo?) -> Unit = {}) {
        if (_isChecking.value) return
        _isChecking.value = true
        viewModelScope.launch {
            try {
                val info = updateChecker.checkForUpdates()
                _updateInfo.value = info
                onComplete(info)
            } catch (e: Exception) {
                Log.e("UpdateViewModel", "Error checking for updates", e)
                onComplete(null)
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun dismissUpdate() {
        checkDismissed = true
        _updateInfo.value = null
    }

    fun downloadAndInstall(context: Context, downloadUrl: String) {
        if (_isDownloading.value) return
        _isDownloading.value = true
        _downloadProgress.value = 0f

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (downloadUrl.startsWith("mock://")) {
                    for (i in 1..100) {
                        kotlinx.coroutines.delay(25)
                        _downloadProgress.value = i.toFloat() / 100f
                    }
                    withContext(Dispatchers.Main) {
                        _isDownloading.value = false
                        _downloadProgress.value = 1f
                        Toast.makeText(context, "Mock update simulation complete!", Toast.LENGTH_SHORT).show()
                        dismissUpdate()
                    }
                    return@launch
                }
                val request = Request.Builder().url(downloadUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Failed to download: $response")

                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    val cacheDir = context.cacheDir
                    val apkFile = File(cacheDir, "update.apk")
                    if (apkFile.exists()) apkFile.delete()

                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0

                    body.byteStream().use { inputStream ->
                        apkFile.outputStream().use { outputStream ->
                            while (true) {
                                val read = inputStream.read(buffer)
                                if (read == -1) break
                                outputStream.write(buffer, 0, read)
                                bytesRead += read
                                if (contentLength > 0) {
                                    _downloadProgress.value = bytesRead.toFloat() / contentLength.toFloat()
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _isDownloading.value = false
                        _downloadProgress.value = 1f
                        installApk(context, apkFile)
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateViewModel", "Error downloading APK", e)
                withContext(Dispatchers.Main) {
                    _isDownloading.value = false
                    Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateViewModel", "Error launching APK installer", e)
            Toast.makeText(context, "Error starting installer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

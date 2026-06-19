package com.aniplex.app.presentation.screens.player

import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object DebugLogManager {
    private const val MAX_LOGS = 1000
    private val logQueue = ConcurrentLinkedQueue<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    var isLoggingEnabled: Boolean = false

    fun log(tag: String, msg: String, tr: Throwable? = null) {
        if (!isLoggingEnabled) return
        
        val timestamp = dateFormat.format(Date())
        val formattedMsg = "[$timestamp] [$tag] $msg" + (tr?.let { "\n${Log.getStackTraceString(it)}" } ?: "")
        
        // Print to logcat so it's still available there
        if (tr != null) {
            Log.e(tag, msg, tr)
        } else {
            Log.d(tag, msg)
        }

        logQueue.add(formattedMsg)
        while (logQueue.size > MAX_LOGS) {
            logQueue.poll()
        }
    }

    fun clear() {
        logQueue.clear()
    }

    fun getLogs(): List<String> {
        return logQueue.toList()
    }

    fun generateDebugReport(
        context: android.content.Context?,
        animeId: String?,
        episodeId: String?,
        selectedServer: String?,
        activeCategory: String?,
        capturedStreamUrl: String?,
        currentWebViewUrl: String?
    ): String {
        val sb = java.lang.StringBuilder()
        val separator = "==================================================\n"
        
        sb.append(separator)
        sb.append("         ANIPLEX ADVANCED DIAGNOSTICS REPORT      \n")
        sb.append(separator)
        
        // App / Device info
        sb.append("TIMESTAMP: ").append(dateFormat.format(Date())).append("\n")
        sb.append("DEVICE BRAND: ").append(Build.BRAND).append("\n")
        sb.append("DEVICE MODEL: ").append(Build.MODEL).append("\n")
        sb.append("ANDROID SDK: ").append(Build.VERSION.SDK_INT).append("\n")
        sb.append("MANUFACTURER: ").append(Build.MANUFACTURER).append("\n")
        sb.append("FINGERPRINT: ").append(Build.FINGERPRINT).append("\n")
        sb.append("\n")

        // Diagnostics metrics
        val allLogs = getLogs()
        val webErrors = allLogs.count { it.contains("WebView load error") || it.contains("WebView HTTP error") }
        val exoErrors = allLogs.count { it.contains("ExoPlayer error") }
        val videoSniffs = allLogs.count { it.contains("Sniffed possible video resource") || it.contains("Registering capturedStreamUrl") }
        val consoleLogs = allLogs.count { it.contains("WEB_CONSOLE") }

        sb.append("--- LOGS & TELEMETRY SUMMARY ---\n")
        sb.append("TOTAL LOG ENTRIES: ").append(allLogs.size).append("\n")
        sb.append("WEBVIEW ERRORS DETECTED: ").append(webErrors).append("\n")
        sb.append("EXOPLAYER ERRORS DETECTED: ").append(exoErrors).append("\n")
        sb.append("SNIFFED VIDEO RESOURCES: ").append(videoSniffs).append("\n")
        sb.append("WEB CONSOLE MESSAGE COUNT: ").append(consoleLogs).append("\n")
        sb.append("\n")
 
        // Media State info
        sb.append("--- PLAYBACK STATE ---\n")
        sb.append("ANIME ID: ").append(animeId ?: "N/A").append("\n")
        sb.append("EPISODE ID: ").append(episodeId ?: "N/A").append("\n")
        sb.append("SELECTED SERVER: ").append(selectedServer ?: "N/A").append("\n")
        sb.append("ACTIVE CATEGORY: ").append(activeCategory ?: "N/A").append("\n")
        sb.append("CAPTURED STREAM URL: ").append(capturedStreamUrl ?: "N/A").append("\n")
        sb.append("WEBVIEW CURRENT URL: ").append(currentWebViewUrl ?: "N/A").append("\n")
        sb.append("\n")
 
        // Cookies collection
        sb.append("--- COOKIES COLLECTION ---\n")
        val cookieDomains = listOf(
            "https://animeplay.cfd",
            "https://megaplay.buzz",
            "https://megacloud.tv",
            "https://rapidcloud.co",
            "https://rabbitstream.net"
        )
        val cookieManager = CookieManager.getInstance()
        cookieDomains.forEach { domain ->
            val cookies = cookieManager.getCookie(domain) ?: "NO_COOKIES_FOUND"
            sb.append("DOMAIN: ").append(domain).append("\n")
            sb.append("RUNTIME COOKIES: ").append(cookies).append("\n")
            if (context != null) {
                val persisted = CookieSaver.getCookiesForDomain(context, domain)
                sb.append("PERSISTED COOKIES: ").append(persisted.ifEmpty { "NONE_PERSISTED" }).append("\n")
            }
            sb.append("--------------------------------------------------\n")
        }
        sb.append("\n")
 
        // In-app Logs
        sb.append("--- APPLICATION IN-APP LOGS ---\n")
        allLogs.forEach { logEntry ->
            sb.append(logEntry).append("\n")
        }
        sb.append(separator)
 
        return sb.toString()
    }

    /**
     * Saves advanced debug reports directly to local device Downloads folder as a TXT file.
     */
    fun saveLogsToDownloads(context: android.content.Context, reportText: String): android.net.Uri? {
        val resolver = context.contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "aniplex_debug_report_$timestamp.txt"
        
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }
        
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            try {
                file.writeText(reportText)
                android.net.Uri.fromFile(file)
            } catch (e: Exception) {
                log("DEBUG_LOG_SAVE", "Exception saving legacy file to downloads: ${e.message}", e)
                null
            }
        }
        
        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(reportText.toByteArray())
                }
                log("DEBUG_LOG_SAVE", "Successfully downloaded/saved debug report: $fileName ($it)")
                return it
            } catch (e: java.lang.Exception) {
                log("DEBUG_LOG_SAVE", "Exception writing stream for mediaURI: ${e.message}", e)
                try {
                    resolver.delete(it, null, null)
                } catch (ignored: Exception) {}
            }
        }
        return null
    }
}

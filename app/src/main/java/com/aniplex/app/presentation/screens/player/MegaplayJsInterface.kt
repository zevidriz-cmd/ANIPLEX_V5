package com.aniplex.app.presentation.screens.player

import android.webkit.JavascriptInterface
import android.util.Log
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

class MegaplayJsInterface(
    private val onProgress: (currentTimeMs: Long, durationMs: Long) -> Unit,
    private val onStateChanged: (isPlaying: Boolean) -> Unit,
    private val onComplete: () -> Unit,
    private val onDebugInfo: (iframeCount: Int, jsInjected: Boolean, lastEvent: String, eventCount: Int) -> Unit = { _, _, _, _ -> }
) {
    @JavascriptInterface
    fun postMessage(message: String) {
        handleBridgeMessage(message)
    }

    @JavascriptInterface
    fun onBridgeMessage(message: String) {
        handleBridgeMessage(message)
    }

    private fun handleBridgeMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                val json = JSONObject(message)

                // Debug state
                if (json.has("event") && json.getString("event") == "debug") {
                    val count = json.optInt("iframeCount", 0)
                    val injected = json.optBoolean("jsInjected", false)
                    val lastEvent = json.optString("lastEvent", "none")
                    val events = json.optInt("eventCount", 0)
                    onDebugInfo(count, injected, lastEvent, events)
                    return@post
                }

                // Play/Pause state changes (action and event fields)
                if (json.has("action")) {
                    val action = json.getString("action")
                    if (action == "play") {
                        onStateChanged(true)
                    } else if (action == "pause") {
                        onStateChanged(false)
                    }
                }
                if (json.has("event")) {
                    val event = json.getString("event")
                    if (event == "play") {
                        onStateChanged(true)
                    } else if (event == "pause") {
                        onStateChanged(false)
                    }
                }

                // Complete event
                if (json.has("event") && json.getString("event") == "complete") {
                    onComplete()
                }

                // Time/Progress events
                val hasTimeEvent = json.has("event") && json.getString("event") == "time"
                val hasWatchingLog = (json.has("type") && json.getString("type") == "watching-log") || 
                                     (json.has("event") && json.getString("event") == "watching-log")
                
                if (hasTimeEvent || hasWatchingLog) {
                    val timeS = if (json.has("time")) json.optDouble("time", 0.0) else json.optDouble("currentTime", 0.0)
                    val durS = json.optDouble("duration", 0.0)
                    
                    if (timeS >= 0 && durS >= 0) {
                        onProgress((timeS * 1000).toLong(), (durS * 1000).toLong())
                    }
                    
                    if (json.has("paused")) {
                        val isPaused = json.optBoolean("paused", false)
                        onStateChanged(!isPaused)
                    }
                }
                
                DebugLogManager.log("ANIPLEX_BRIDGE", "Parsed on main thread: $message")
            } catch (e: Exception) {
                DebugLogManager.log("ANIPLEX_BRIDGE", "Parse failed: ${e.message} | raw: $message", e)
            }
        }
    }
}

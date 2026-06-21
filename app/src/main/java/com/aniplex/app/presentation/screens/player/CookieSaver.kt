package com.aniplex.app.presentation.screens.player

import android.content.Context
import android.util.Log
import android.webkit.CookieManager

object CookieSaver {
    private const val PREFS_NAME = "aniplex_saved_cookies"

    /**
     * Saves cookies for a specific domain to persistent Shared Preferences.
     */
    fun saveCookies(context: Context, domain: String, cookies: String?) {
        if (cookies.isNullOrBlank()) return
        
        val cleanDomain = getBaseDomain(domain)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Merge with existing cookies to prevent losing anything
        val existingCookies = prefs.getString(cleanDomain, "") ?: ""
        val mergedCookies = mergeCookieStrings(existingCookies, cookies)
        
        prefs.edit().putString(cleanDomain, mergedCookies).apply()
        
        DebugLogManager.log("COOKIE_SAVER", "Saved cookies for domain $cleanDomain: $mergedCookies")
        
        // Also ensure WebKit CookieManager has them synced and flushed to disk
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setCookie(cleanDomain, mergedCookies)
            cookieManager.flush()
        } catch (e: Exception) {
            DebugLogManager.log("COOKIE_SAVER", "Error flushing cookies for $cleanDomain to WebKit: ${e.message}")
        }
    }

    /**
     * Restores all saved cookies back into WebKit's CookieManager.
     */
    fun restoreCookies(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        
        val allEntries = prefs.all
        DebugLogManager.log("COOKIE_SAVER", "Restoring ${allEntries.size} saved domains from DB persistence...")
        
        allEntries.forEach { (domain, cookieValue) ->
            if (cookieValue is String && cookieValue.isNotEmpty()) {
                try {
                    cookieManager.setCookie(domain, cookieValue)
                    // Also try with protocol prefix just in case webview needs it
                    if (!domain.startsWith("http")) {
                        cookieManager.setCookie("https://$domain", cookieValue)
                        cookieManager.setCookie("http://$domain", cookieValue)
                    }
                    DebugLogManager.log("COOKIE_SAVER", "Restored cookies for $domain: $cookieValue")
                } catch (e: Exception) {
                    DebugLogManager.log("COOKIE_SAVER", "Failed to restore cookies for $domain: ${e.message}")
                }
            }
        }
        cookieManager.flush()
    }

    /**
     * Retrieves all saved cookies from SharedPreferences formatted as a single HTTP Header string.
     */
    fun getSavedCookiesHeader(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allEntries = prefs.all
        val allCookiesList = mutableListOf<String>()
        
        allEntries.forEach { (_, cookieValue) ->
            if (cookieValue is String && cookieValue.isNotEmpty()) {
                cookieValue.split("; ").forEach { pair ->
                    val trimmed = pair.trim()
                    if (trimmed.isNotEmpty() && !allCookiesList.contains(trimmed)) {
                        allCookiesList.add(trimmed)
                    }
                }
            }
        }
        
        return allCookiesList.joinToString("; ")
    }

    /**
     * Retrieves saved cookies for a specific domain.
     */
    fun getCookiesForDomain(context: Context, domain: String): String {
        val cleanDomain = getBaseDomain(domain)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(cleanDomain, "") ?: ""
    }

    private fun getBaseDomain(url: String): String {
        return try {
            val cleanUrl = if (!url.contains("://")) "https://$url" else url
            val uri = android.net.Uri.parse(cleanUrl)
            val host = uri.host ?: url
            // Return base domain if we have subdomain, else return host
            host
        } catch (e: Exception) {
            url
        }
    }

    private fun mergeCookieStrings(cookies1: String, cookies2: String): String {
        val map = mutableMapOf<String, String>()
        
        fun parseAndAddToMap(cookieStr: String) {
            cookieStr.split(";").forEach { pair ->
                val trimmed = pair.trim()
                if (trimmed.isNotEmpty()) {
                    val eqIndex = trimmed.indexOf('=')
                    if (eqIndex != -1) {
                        val key = trimmed.substring(0, eqIndex).trim()
                        val value = trimmed.substring(eqIndex + 1).trim()
                        if (key.isNotEmpty()) {
                            map[key] = value
                        }
                    }
                }
            }
        }

        parseAndAddToMap(cookies1)
        parseAndAddToMap(cookies2)
        
        return map.map { "${it.key}=${it.value}" }.joinToString("; ")
    }
}

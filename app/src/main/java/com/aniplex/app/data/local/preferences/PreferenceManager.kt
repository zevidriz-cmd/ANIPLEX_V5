package com.aniplex.app.data.local.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("aniplex_prefs", Context.MODE_PRIVATE)

    var defaultAudioCategory: String
        get() = prefs.getString("default_audio", "sub") ?: "sub"
        set(value) = prefs.edit().putString("default_audio", value).apply()

    var autoplayNextEpisode: Boolean
        get() = prefs.getBoolean("autoplay_next", true)
        set(value) = prefs.edit().putBoolean("autoplay_next", value).apply()

    var preferredQuality: String
        get() = prefs.getString("preferred_quality", "Auto") ?: "Auto"
        set(value) = prefs.edit().putString("preferred_quality", value).apply()

    var skipIntro: Boolean
        get() = prefs.getBoolean("skip_intro", true)
        set(value) = prefs.edit().putBoolean("skip_intro", value).apply()

    var skipOutro: Boolean
        get() = prefs.getBoolean("skip_outro", true)
        set(value) = prefs.edit().putBoolean("skip_outro", value).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1.0f)
        set(value) = prefs.edit().putFloat("playback_speed", value).apply()

    var subtitlesEnabled: Boolean
        get() = prefs.getBoolean("subtitles_enabled", true)
        set(value) = prefs.edit().putBoolean("subtitles_enabled", value).apply()

    var preferredAnimeVersion: String
        get() = prefs.getString("preferred_anime_version", "uncensored") ?: "uncensored"
        set(value) = prefs.edit().putString("preferred_anime_version", value).apply()

    var enableDiagnostics: Boolean
        get() = prefs.getBoolean("enable_diagnostics", false)
        set(value) = prefs.edit().putBoolean("enable_diagnostics", value).apply()

    var downloadOverCellular: Boolean
        get() = prefs.getBoolean("download_cellular", false)
        set(value) = prefs.edit().putBoolean("download_cellular", value).apply()

    fun getSelectedProfileId(userId: String): String? {
        return prefs.getString("selected_profile_id_$userId", null)
    }

    fun setSelectedProfileId(userId: String, profileId: String?) {
        prefs.edit().putString("selected_profile_id_$userId", profileId).apply()
    }

    fun getRecentSearches(): List<String> {
        val raw = prefs.getString("recent_searches", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("||").filter { it.isNotBlank() }
    }

    fun saveRecentSearches(searches: List<String>) {
        val joined = searches.joinToString("||")
        prefs.edit().putString("recent_searches", joined).apply()
    }
}

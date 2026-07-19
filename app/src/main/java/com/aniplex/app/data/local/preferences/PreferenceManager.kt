package com.aniplex.app.data.local.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("aniplex_prefs", Context.MODE_PRIVATE)

    var defaultAudioCategory: String
        get() = prefs.getString("default_audio", "sub") ?: "sub"
        set(value) {
            prefs.edit().putString("default_audio", value).apply()
            _defaultAudioCategoryFlow.value = value
        }

    private val _defaultAudioCategoryFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString("default_audio", "sub") ?: "sub")
    val defaultAudioCategoryFlow = _defaultAudioCategoryFlow.asStateFlow()

    var autoplayNextEpisode: Boolean
        get() = prefs.getBoolean("autoplay_next", true)
        set(value) {
            prefs.edit().putBoolean("autoplay_next", value).apply()
            _autoplayNextEpisodeFlow.value = value
        }

    private val _autoplayNextEpisodeFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("autoplay_next", true))
    val autoplayNextEpisodeFlow = _autoplayNextEpisodeFlow.asStateFlow()

    var preferredQuality: String
        get() = prefs.getString("preferred_quality", "Auto") ?: "Auto"
        set(value) {
            prefs.edit().putString("preferred_quality", value).apply()
            _preferredQualityFlow.value = value
        }

    private val _preferredQualityFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString("preferred_quality", "Auto") ?: "Auto")
    val preferredQualityFlow = _preferredQualityFlow.asStateFlow()

    var skipIntro: Boolean
        get() = prefs.getBoolean("skip_intro", true)
        set(value) {
            prefs.edit().putBoolean("skip_intro", value).apply()
            _skipIntroFlow.value = value
        }

    private val _skipIntroFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("skip_intro", true))
    val skipIntroFlow = _skipIntroFlow.asStateFlow()

    var skipOutro: Boolean
        get() = prefs.getBoolean("skip_outro", true)
        set(value) {
            prefs.edit().putBoolean("skip_outro", value).apply()
            _skipOutroFlow.value = value
        }

    private val _skipOutroFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("skip_outro", true))
    val skipOutroFlow = _skipOutroFlow.asStateFlow()

    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1.0f)
        set(value) {
            prefs.edit().putFloat("playback_speed", value).apply()
            _playbackSpeedFlow.value = value
        }

    private val _playbackSpeedFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getFloat("playback_speed", 1.0f))
    val playbackSpeedFlow = _playbackSpeedFlow.asStateFlow()

    var subtitlesEnabled: Boolean
        get() = prefs.getBoolean("subtitles_enabled", true)
        set(value) {
            prefs.edit().putBoolean("subtitles_enabled", value).apply()
            _subtitlesEnabledFlow.value = value
        }

    private val _subtitlesEnabledFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("subtitles_enabled", true))
    val subtitlesEnabledFlow = _subtitlesEnabledFlow.asStateFlow()

    var preferredAnimeVersion: String
        get() = prefs.getString("preferred_anime_version", "uncensored") ?: "uncensored"
        set(value) {
            prefs.edit().putString("preferred_anime_version", value).apply()
            _preferredAnimeVersionFlow.value = value
        }

    private val _preferredAnimeVersionFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString("preferred_anime_version", "uncensored") ?: "uncensored")
    val preferredAnimeVersionFlow = _preferredAnimeVersionFlow.asStateFlow()

    var enableDiagnostics: Boolean
        get() = prefs.getBoolean("enable_diagnostics", true)
        set(value) {
            prefs.edit().putBoolean("enable_diagnostics", value).apply()
            _enableDiagnosticsFlow.value = value
        }

    private val _enableDiagnosticsFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("enable_diagnostics", true))
    val enableDiagnosticsFlow = _enableDiagnosticsFlow.asStateFlow()

    var downloadOverCellular: Boolean
        get() = prefs.getBoolean("download_cellular", false)
        set(value) {
            prefs.edit().putBoolean("download_cellular", value).apply()
            _downloadOverCellularFlow.value = value
        }

    private val _downloadOverCellularFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("download_cellular", false))
    val downloadOverCellularFlow = _downloadOverCellularFlow.asStateFlow()

    var preferredProvider: String
        get() = prefs.getString("preferred_provider", "zoro") ?: "zoro"
        set(value) {
            prefs.edit().putString("preferred_provider", value).apply()
            _preferredProviderFlow.value = value
        }

    private val _preferredProviderFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString("preferred_provider", "zoro") ?: "zoro")
    val preferredProviderFlow = _preferredProviderFlow.asStateFlow()

    var subtitleSizeScale: Float
        get() = prefs.getFloat("subtitle_size_scale", 1.0f)
        set(value) {
            prefs.edit().putFloat("subtitle_size_scale", value).apply()
            _subtitleSizeScaleFlow.value = value
        }

    private val _subtitleSizeScaleFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getFloat("subtitle_size_scale", 1.0f))
    val subtitleSizeScaleFlow = _subtitleSizeScaleFlow.asStateFlow()

    var subtitleColor: String
        get() = prefs.getString("subtitle_color", "White") ?: "White"
        set(value) {
            prefs.edit().putString("subtitle_color", value).apply()
            _subtitleColorFlow.value = value
        }

    private val _subtitleColorFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString("subtitle_color", "White") ?: "White")
    val subtitleColorFlow = _subtitleColorFlow.asStateFlow()

    var subtitleBgOpacity: Float
        get() = prefs.getFloat("subtitle_bg_opacity", 0.35f)
        set(value) {
            prefs.edit().putFloat("subtitle_bg_opacity", value).apply()
            _subtitleBgOpacityFlow.value = value
        }

    private val _subtitleBgOpacityFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getFloat("subtitle_bg_opacity", 0.35f))
    val subtitleBgOpacityFlow = _subtitleBgOpacityFlow.asStateFlow()

    var subtitleStyle: String
        get() = prefs.getString("subtitle_style", "classic_outline") ?: "classic_outline"
        set(value) {
            prefs.edit().putString("subtitle_style", value).apply()
            _subtitleStyleFlow.value = value
        }

    private val _subtitleStyleFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString("subtitle_style", "classic_outline") ?: "classic_outline")
    val subtitleStyleFlow = _subtitleStyleFlow.asStateFlow()

    var subtitlePosition: Float
        get() = prefs.getFloat("subtitle_position", 0.10f)
        set(value) {
            prefs.edit().putFloat("subtitle_position", value).apply()
            _subtitlePositionFlow.value = value
        }

    private val _subtitlePositionFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getFloat("subtitle_position", 0.10f))
    val subtitlePositionFlow = _subtitlePositionFlow.asStateFlow()

    var hevcDecoderEnabled: Boolean
        get() = prefs.getBoolean("hevc_decoder_enabled", true)
        set(value) {
            prefs.edit().putBoolean("hevc_decoder_enabled", value).apply()
            _hevcDecoderEnabledFlow.value = value
        }

    private val _hevcDecoderEnabledFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("hevc_decoder_enabled", true))
    val hevcDecoderEnabledFlow = _hevcDecoderEnabledFlow.asStateFlow()

    var dolbyAtmosEnabled: Boolean
        get() = prefs.getBoolean("dolby_atmos_enabled", true)
        set(value) {
            prefs.edit().putBoolean("dolby_atmos_enabled", value).apply()
            _dolbyAtmosEnabledFlow.value = value
        }

    private val _dolbyAtmosEnabledFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("dolby_atmos_enabled", true))
    val dolbyAtmosEnabledFlow = _dolbyAtmosEnabledFlow.asStateFlow()

    var preferredAccentColor: String
        get() = prefs.getString("preferred_accent_color", "Purple Neon") ?: "Purple Neon"
        set(value) {
            prefs.edit().putString("preferred_accent_color", value).apply()
            _accentColorFlow.value = value
        }

    private val _accentColorFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString("preferred_accent_color", "Purple Neon") ?: "Purple Neon")
    val accentColorFlow = _accentColorFlow.asStateFlow()

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

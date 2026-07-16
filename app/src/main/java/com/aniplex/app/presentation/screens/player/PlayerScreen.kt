package com.aniplex.app.presentation.screens.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.ViewGroup
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner as LifecycleOwnerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.aniplex.app.data.download.DownloadManager
import com.aniplex.app.data.download.DownloadStatus
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.BuildConfig
import com.aniplex.app.theme.SurfaceDark
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import android.graphics.Color as AndroidColor
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.compose.material.ripple.rememberRipple
import com.aniplex.app.domain.model.Episode
import com.aniplex.app.domain.model.AnimeDetail
import com.aniplex.app.domain.model.SkipTimes

private val sharedOkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

@Stable
data class PlayerScreenState(
    val isLandscape: Boolean,
    val skipTimes: SkipTimes,
    val showSettings: Boolean,
    val episodes: List<Episode>,
    val uiState: PlayerUiState,
    val animeDetail: AnimeDetail?,
    val currentEpisode: Episode?,
    val likeCount: Int,
    val isLiked: Boolean,
    val dislikeCount: Int,
    val isDisliked: Boolean,
    val activeCategory: String,
    val playbackSpeed: Float,
    val subtitlesEnabled: Boolean,
    val qualityOption: String,
    val subtitleSizeSp: Float,
    val capturedStreamUrl: String?,
    val capturedSubtitles: List<SniffedSubtitle>,
    val currentSubtitleSelection: String,
    val durationMs: Long,
    val currentPositionMs: Long,
    val bufferedPositionMs: Long = 0L,
    val isBuffering: Boolean,
    val isPlaying: Boolean,
    val extractionState: ExtractionState,
    val exoPlayerRef: ExoPlayer?,
    val webViewRef: WebView?,
    val localResumePlayback: Boolean,
    val retryCount: Int,
    val timeoutKey: Int,
    val extractedUserAgent: String?,
    val extractedCookies: String?,
    val playbackPosition: Long,
    val downloadTaskStatus: DownloadStatus?,
    val taskProgress: Float,
    val initialProgress: Long,
    val autoplayNextEpisode: Boolean,
    val playbackError: String?,
    val retryPlaybackKey: Int,
    val isControlsVisible: Boolean,
    val controlsInteractionKey: Int,
    val gestureIndicatorType: String?,
    val gestureIndicatorValue: Float,
    val showGestureIndicator: Boolean,
    val showAudioDialog: Boolean,
    val showSubtitlesDialog: Boolean,
    val showQualityDialog: Boolean,
    val showSpeedDialog: Boolean,
    val showSubtitleSizeDialog: Boolean,
    val showVersionDialog: Boolean,
    val preferredAnimeVersion: String,
    val metadataScrollState: ScrollState,
    val settingsScrollState: ScrollState,
    val subtitleStyle: String,
    val showSubtitleStyleDialog: Boolean,
    val showEpisodesSelector: Boolean,
    val selectedServer: String = "s-1",
    val preferredProvider: String = "zoro",
    val showServerDialog: Boolean = false,
    val isDiagnosticsEnabled: Boolean = false,
    val subtitleColor: String = "White",
    val subtitleBgOpacity: Float = 0.35f,
    val subtitleSizeScale: Float = 1.0f,
    val showSubtitleColorDialog: Boolean = false,
    val showSubtitleBgOpacityDialog: Boolean = false,
    val subtitlePosition: Float = 0.10f,
    val screenFitMode: String = "Fit",
    val isUpNextDismissed: Boolean = false,
    val fallbackStatusMessage: String? = null
)

data class PlayerCallbacks(
    val onBackClick: () -> Unit,
    val onAnimeClick: () -> Unit,
    val toggleLike: () -> Unit,
    val toggleDislike: () -> Unit,
    val onDownloadClick: () -> Unit,
    val onNextEpisodeClick: (() -> Unit)?,
    val onSettingsClick: () -> Unit,
    val onSettingsBackClick: () -> Unit,
    val onAutoplayChange: (Boolean) -> Unit,
    val onAudioChange: (String) -> Unit,
    val onSubtitleSelectionChange: (String) -> Unit,
    val onQualityChange: (String) -> Unit,
    val onSpeedChange: (Float) -> Unit,
    val onSubtitleSizeChange: (Float) -> Unit,
    val onRetryClick: () -> Unit,
    val onStreamUrlCaptured: (String?) -> Unit,
    val onUserAgentExtracted: (String?) -> Unit,
    val onCookiesExtracted: (String?) -> Unit,
    val onExtractionStateChanged: (ExtractionState) -> Unit,
    val onDurationChanged: (Long) -> Unit,
    val onPositionChanged: (Long) -> Unit,
    val onBufferingChanged: (Boolean) -> Unit,
    val onPlayingChanged: (Boolean) -> Unit,
    val onWebViewRefChanged: (WebView?) -> Unit,
    val onExoPlayerRefChanged: (ExoPlayer?) -> Unit,
    val onRetryCountChanged: (Int) -> Unit,
    val onTimeoutKeyChanged: (Int) -> Unit,
    val onPlaybackPositionChanged: (Long) -> Unit,
    val onSaveProgress: (progress: Long, duration: Long) -> Unit,
    val onPlaybackErrorChanged: (String?) -> Unit,
    val onRetryPlaybackKeyChanged: (Int) -> Unit,
    val onControlsVisibilityToggle: () -> Unit,
    val onControlsInteraction: () -> Unit,
    val onGestureChanged: (type: String?, value: Float, show: Boolean) -> Unit,
    val onShowAudioDialogChange: (Boolean) -> Unit,
    val onShowSubtitlesDialogChange: (Boolean) -> Unit,
    val onShowQualityDialogChange: (Boolean) -> Unit,
    val onShowSpeedDialogChange: (Boolean) -> Unit,
    val onShowSubtitleSizeDialogChange: (Boolean) -> Unit,
    val onVersionChange: (String) -> Unit,
    val onShowVersionDialogChange: (Boolean) -> Unit,
    val onSubtitleStyleChange: (String) -> Unit,
    val onShowSubtitleStyleDialogChange: (Boolean) -> Unit,
    val onSubtitleColorChange: (String) -> Unit,
    val onShowSubtitleColorDialogChange: (Boolean) -> Unit,
    val onSubtitleBgOpacityChange: (Float) -> Unit,
    val onShowSubtitleBgOpacityDialogChange: (Boolean) -> Unit,
    val onSubtitleSizeScaleChange: (Float) -> Unit,
    val onShowEpisodesSelectorChange: (Boolean) -> Unit,
    val onEpisodeSelect: (Episode) -> Unit,
    val onServerSelected: (String) -> Unit,
    val onShowServerDialogChange: (Boolean) -> Unit,
    val onDebugClick: () -> Unit,
    val onDiagnosticsChange: (Boolean) -> Unit,
    val onScreenFitChange: (String) -> Unit = {},
    val onDismissUpNext: () -> Unit = {},
    val onIframeServerSwitch: (String) -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    episodeId: String,
    animeId: String,
    animeTitle: String,
    episodeNumber: Int,
    category: String,
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier,
    resumePlayback: Boolean,
    initialProgressParam: Long,
    isTv: Boolean = false,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var isLandscape by remember(configuration.orientation) {
        mutableStateOf(isTv || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }
    var showSettings by remember { mutableStateOf(false) }

    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val animeDetail by viewModel.animeDetail.collectAsStateWithLifecycle()
    val currentEpisode by viewModel.currentEpisode.collectAsStateWithLifecycle()
    val fallbackStatusMessage by viewModel.fallbackStatusMessage.collectAsStateWithLifecycle()
    var activeEpisodeId by remember(episodeId) { mutableStateOf(episodeId) }
    val currentEpNum = currentEpisode?.number ?: episodeNumber
    val skipTimes by viewModel.skipTimes.collectAsStateWithLifecycle()
    
    val likeCount by viewModel.likeCount.collectAsStateWithLifecycle()
    val isLiked by viewModel.isLiked.collectAsStateWithLifecycle()
    val dislikeCount by viewModel.dislikeCount.collectAsStateWithLifecycle()
    val isDisliked by viewModel.isDisliked.collectAsStateWithLifecycle()

    val (activeCategory, setActiveCategory) = remember { mutableStateOf(viewModel.defaultAudioCategory) }
    
    // Playback Settings States
    var playbackSpeed by remember { mutableStateOf(viewModel.playbackSpeed) }
    var subtitlesEnabled by remember { mutableStateOf(viewModel.subtitlesEnabled) }
    var qualityOption by remember { mutableStateOf(viewModel.preferredQuality) }
    var subtitleSizeSp by remember { mutableStateOf(18f) } // Default to 18sp
    var screenFitMode by remember { mutableStateOf("Fit") }
    var isUpNextDismissed by remember(activeEpisodeId) { mutableStateOf(false) }



    // Lifted playback and sniffer states
    var capturedStreamUrl by remember { mutableStateOf<String?>(null) }
    val capturedSubtitles = remember { mutableStateListOf<SniffedSubtitle>() }
    var hasAttemptedBackupSubtitles by remember { mutableStateOf(false) }
    var currentSubtitleSelection by remember { mutableStateOf("en") }
    var durationMs by remember { mutableStateOf(0L) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var bufferedPositionMs by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var extractionState by remember { mutableStateOf(ExtractionState.EXTRACTING) }
    
    var exoPlayerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var localResumePlayback by remember { mutableStateOf(resumePlayback) }
    
    var retryCount by remember { mutableStateOf(0) }
    var timeoutKey by remember { mutableStateOf(0) }
    var fallbackToNextServerRef by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    var extractedUserAgent by remember { mutableStateOf<String?>(null) }
    var extractedCookies by remember { mutableStateOf<String?>(null) }
    var interceptedReferer by remember { mutableStateOf<String?>(null) }
    var interceptedOrigin by remember { mutableStateOf<String?>(null) }
    var playbackPosition by remember { mutableStateOf(0L) }
    var serverSwitchPosition by remember { mutableStateOf<Long?>(null) }
    var lastEpisodeId by remember { mutableStateOf("") }
    var lastCategory by remember { mutableStateOf("") }
    var lastServer by remember { mutableStateOf("") }
    var sniffingEpisodeId by remember { mutableStateOf<String?>(null) }

    // Additional player states lifted from ExoVideoPlayer
    var retryPlaybackKey by remember { mutableStateOf(0) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var lastSavedProgressTime by remember { mutableStateOf(0L) }
    // Stable ref: defers playWhenReady until ExoPlayer reaches STATE_READY,
    // preventing audio from leaking while the video surface is still attaching.
    val pendingPlayWhenReady = remember { mutableStateOf(false) }
    var isControlsVisible by remember { mutableStateOf(false) }
    var controlsInteractionKey by remember { mutableStateOf(0) }
    var gestureIndicatorType by remember { mutableStateOf<String?>(null) }
    var gestureIndicatorValue by remember { mutableStateOf(0f) }
    var showGestureIndicator by remember { mutableStateOf(false) }

    val preferredProvider = viewModel.preferredProvider
    val availableServers = remember(preferredProvider) {
        if (preferredProvider == "gogoanime") {
            listOf("s-2", "s-1", "s-3")
        } else {
            listOf("s-1", "s-2", "s-3")
        }
    }

    var selectedServer by remember(preferredProvider) { mutableStateOf(availableServers.first()) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showDownloadServerDialog by remember { mutableStateOf(false) }
    var autoStartDownloadAfterExtraction by remember { mutableStateOf(false) }
    var isDiagnosticsEnabled by remember { mutableStateOf(DebugLogManager.isLoggingEnabled) }

    val getEmbedUrl = remember<(String, String, String) -> String> {
        { srv, epId, cat ->
            val serverDomain = when (srv) {
                "s-1" -> "https://animeplay.cfd"
                "s-2" -> "https://api.kryzox.xyz"
                "s-3" -> "https://cdn.4animo.xyz"
                else -> "https://animeplay.cfd"
            }
            if (cat.equals("dub", ignoreCase = true)) {
                "$serverDomain/stream/$srv/$epId/dub"
            } else {
                "$serverDomain/stream/$srv/$epId"
            }
        }
    }

    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitlesDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSubtitleSizeDialog by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var preferredAnimeVersion by remember { mutableStateOf(viewModel.preferredAnimeVersion) }
    var subtitleStyle by remember { mutableStateOf(viewModel.subtitleStyle) }
    var showSubtitleStyleDialog by remember { mutableStateOf(false) }
    var showEpisodesSelector by remember { mutableStateOf(false) }
    var showDebugExportDialog by remember { mutableStateOf(false) }

    var subtitleColor by remember { mutableStateOf(viewModel.subtitleColor) }
    var subtitleBgOpacity by remember { mutableStateOf(viewModel.subtitleBgOpacity) }
    var subtitleSizeScale by remember { mutableStateOf(viewModel.subtitleSizeScale) }
    var subtitlePosition by remember { mutableStateOf(viewModel.subtitlePosition) }
    var showSubtitleColorDialog by remember { mutableStateOf(false) }
    var showSubtitleBgOpacityDialog by remember { mutableStateOf(false) }

    // Scroll states
    val metadataScrollState = rememberScrollState()
    val settingsScrollState = rememberScrollState()

    val webView = remember {
        CookieSaver.restoreCookies(context)
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            settings.javaScriptEnabled = true
            // Require user gesture to prevent background sniffer WebView from ever playing audio
            // and stealing audio focus from the main ExoPlayer.
            settings.mediaPlaybackRequiresUserGesture = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    view?.evaluateJavascript("""
                        (function() {
                            var muteMedia = function() {
                                document.querySelectorAll('video, audio').forEach(function(el) {
                                    el.muted = true;
                                    el.volume = 0;
                                    el.setAttribute('muted', 'true');
                                });
                            };
                            muteMedia();
                            if (!window.hasMuteObserver) {
                                window.hasMuteObserver = true;
                                var observer = new MutationObserver(muteMedia);
                                observer.observe(document.documentElement, { childList: true, subtree: true });
                            }
                        })();
                    """.trimIndent(), null)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    val titleLower = title?.lowercase() ?: ""
                    DebugLogManager.log("ANIPLEX_PLAYER", "WebView title updated: $title")
                    val isExactError = titleLower == "error" || titleLower == "404" || titleLower == "502" || titleLower == "504"
                    val containsError = titleLower.contains("404 not found") || 
                            titleLower.contains("page not found") || 
                            titleLower.contains("bad gateway") || 
                            titleLower.contains("gateway timeout") || 
                            titleLower.contains("service unavailable") ||
                            titleLower.contains("connection error") ||
                            titleLower.contains("something went wrong") ||
                            titleLower.contains("forbidden") ||
                            titleLower.contains("failed") ||
                            titleLower.contains("oops")
                    
                    if (isExactError || containsError) {
                        DebugLogManager.log("ANIPLEX_PLAYER", "Received error in title: '$title'. Triggering server fallback...")
                        Handler(Looper.getMainLooper()).post {
                            fallbackToNextServerRef?.invoke()
                        }
                    }
                }

                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        val level = it.messageLevel()?.name ?: "LOG"
                        DebugLogManager.log("WEB_CONSOLE", "[$level] ${it.message()} [Source: ${it.sourceId()} @ line ${it.lineNumber()}]")
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }
            
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    DebugLogManager.log("ANIPLEX_PLAYER", "WebView started loading: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    DebugLogManager.log("ANIPLEX_PLAYER", "WebView completed loading: $url")
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    DebugLogManager.log("ANIPLEX_PLAYER", "WebView redirecting or overriding load to: ${request?.url}")
                    return super.shouldOverrideUrlLoading(view, request)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val desc = error?.description ?: "Unknown"
                        val code = error?.errorCode ?: -1
                        DebugLogManager.log(
                            "ANIPLEX_PLAYER",
                            "WebView load error on: ${request?.url} | Error: $desc (Code: $code)"
                        )
                        if (request?.isForMainFrame == true) {
                            val curProvider = viewModel.activeProvider.value ?: preferredProvider
                            if (curProvider == "zoro") {
                                DebugLogManager.log("ANIPLEX_PLAYER", "Main frame load error: $desc. Triggering fallback...")
                                Handler(Looper.getMainLooper()).post {
                                    fallbackToNextServerRef?.invoke()
                                }
                            } else {
                                DebugLogManager.log("ANIPLEX_PLAYER", "Main frame load error ignored since active provider is $curProvider")
                            }
                        }
                    } else {
                        DebugLogManager.log("ANIPLEX_PLAYER", "WebView load error")
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val status = errorResponse?.statusCode ?: -1
                    val phrase = errorResponse?.reasonPhrase ?: "Unknown"
                    DebugLogManager.log(
                        "ANIPLEX_PLAYER",
                        "WebView HTTP error response on URL: ${request?.url} | Status: $status | Reason: $phrase"
                    )
                    if (request?.isForMainFrame == true && (status == 404 || status >= 500)) {
                        val curProvider = viewModel.activeProvider.value ?: preferredProvider
                        if (curProvider == "zoro") {
                            DebugLogManager.log("ANIPLEX_PLAYER", "HTTP error $status on main frame. Triggering server fallback...")
                            Handler(Looper.getMainLooper()).post {
                                fallbackToNextServerRef?.invoke()
                            }
                        } else {
                            DebugLogManager.log("ANIPLEX_PLAYER", "HTTP error $status ignored since active provider is $curProvider")
                        }
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val curProvider = viewModel.activeProvider.value ?: preferredProvider
                    if (curProvider != "zoro") return null
                    
                    val currentSniffingId = sniffingEpisodeId
                    if (currentSniffingId == null) {
                        return null
                    }
                    
                    val url = request?.url?.toString() ?: return null
                    val urlLower = url.lowercase()
                    val cleanUrl = url.split("?")[0].lowercase()

                    // Subtitle sniffing
                    val subtitleMime = when {
                        cleanUrl.endsWith(".vtt") -> androidx.media3.common.MimeTypes.TEXT_VTT
                        cleanUrl.endsWith(".srt") -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                        cleanUrl.endsWith(".ass") || cleanUrl.endsWith(".ssa") -> "text/x-ass"
                        else -> null
                    }

                    if (subtitleMime != null) {
                        val langCode = when {
                            urlLower.contains("eng") || urlLower.contains("english") || urlLower.contains("-en") || urlLower.contains("_en") -> "en"
                            urlLower.contains("ara") || urlLower.contains("-ar") -> "ar"
                            urlLower.contains("spa") || urlLower.contains("-es") -> "es"
                            urlLower.contains("fre") || urlLower.contains("-fr") -> "fr"
                            else -> "en" // Automatically default to en instead of und so ExoPlayer auto-selects it
                        }
                        Handler(Looper.getMainLooper()).post {
                            if (capturedSubtitles.none { it.url == url }) {
                                capturedSubtitles.add(SniffedSubtitle(url, subtitleMime, langCode))
                                DebugLogManager.log("ANIPLEX_SUBS", "Collected Subtitle: $langCode | $url")
                            }
                        }
                    }

                    // Sniff for video URL (hls or mp4 fallback) - Tightened for stability
                    val isStaticResource = urlLower.contains(".gif") || urlLower.contains(".png") || urlLower.contains(".jpg") || urlLower.contains(".jpeg")
                    val isVideo = !isStaticResource && (
                        cleanUrl.contains(".m3u8") || cleanUrl.endsWith(".m3u8") || 
                        cleanUrl.contains(".mp4") || cleanUrl.endsWith(".mp4") ||
                        urlLower.contains("/m3u8") || urlLower.contains("index.m3u8") || 
                        urlLower.contains("master.m3u8") || urlLower.contains(".m3u8?") ||
                        (urlLower.contains("/hls/") && !urlLower.contains(".key"))
                    )
                    if (isVideo) {
                        DebugLogManager.log("ANIPLEX_PLAYER", "Sniffed possible video resource: $url")
                        
                        // Extract request headers
                        val headers = request?.requestHeaders
                        val interceptedCookie = headers?.entries?.firstOrNull { it.key.equals("cookie", ignoreCase = true) }?.value
                        val ref = headers?.entries?.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value
                        val orig = headers?.entries?.firstOrNull { it.key.equals("origin", ignoreCase = true) }?.value
                        
                        if (capturedStreamUrl == null) {
                            DebugLogManager.log("ANIPLEX_PLAYER", "Registering capturedStreamUrl source: $url")
                            DownloadManager.setStreamUrl(currentSniffingId, url)

                            // 1. Synchronously grab cookies from CookieManager for the specific domains before any page wiping
                            val cookieManager = android.webkit.CookieManager.getInstance()
                            val cookiesMewstream = cookieManager.getCookie("https://mewstream.buzz") ?: ""
                            val cookiesMegaplay = cookieManager.getCookie("https://megaplay.buzz") ?: ""
                            val cookiesAnimeplay = cookieManager.getCookie("https://animeplay.cfd") ?: ""
                            
                            val streamHost = try {
                                val uri = android.net.Uri.parse(url)
                                "${uri.scheme}://${uri.host}"
                            } catch (e: Exception) { "" }
                            val cookiesStream = if (streamHost.isNotEmpty()) cookieManager.getCookie(streamHost) ?: "" else ""

                            // 2. Combine these cookies into a single string
                            val combinedCookiesList = listOf(cookiesMewstream, cookiesMegaplay, cookiesAnimeplay, cookiesStream)
                                .flatMap { it.split("; ") }
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .distinct()
                            val combinedCookiesStr = combinedCookiesList.joinToString("; ")

                            // 3 & 4. Persist each captured set and flush cookie manager
                            if (cookiesMewstream.isNotEmpty()) CookieSaver.saveCookies(context, "https://mewstream.buzz", cookiesMewstream)
                            if (cookiesMegaplay.isNotEmpty()) CookieSaver.saveCookies(context, "https://megaplay.buzz", cookiesMegaplay)
                            if (cookiesAnimeplay.isNotEmpty()) CookieSaver.saveCookies(context, "https://animeplay.cfd", cookiesAnimeplay)
                            if (streamHost.isNotEmpty() && cookiesStream.isNotEmpty()) CookieSaver.saveCookies(context, streamHost, cookiesStream)
                            if (!interceptedCookie.isNullOrBlank()) {
                                CookieSaver.saveCookies(context, url, interceptedCookie)
                            }
                            cookieManager.flush()

                            Handler(Looper.getMainLooper()).post {
                                val ua = view?.settings?.userAgentString ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                extractedUserAgent = ua
                                
                                extractedCookies = combinedCookiesStr.ifEmpty { CookieSaver.getCookiesForDomain(context, url) }
                                
                                if (!ref.isNullOrBlank()) {
                                    DebugLogManager.log("ANIPLEX_PLAYER", "Intercepted video Referer: $ref")
                                    interceptedReferer = ref
                                }
                                if (!orig.isNullOrBlank()) {
                                    DebugLogManager.log("ANIPLEX_PLAYER", "Intercepted video Origin: $orig")
                                    interceptedOrigin = orig
                                }
                                
                                capturedStreamUrl = url

                                // 5. ONLY AFTER everything is captured and saved, navigate the WebView to about:blank on main thread
                                try {
                                    view?.evaluateJavascript("""
                                        (function() {
                                            document.querySelectorAll('video, audio').forEach(function(el) {
                                                el.pause();
                                                el.muted = true;
                                                el.src = '';
                                            });
                                            var frames = document.querySelectorAll('iframe');
                                            frames.forEach(function(f) {
                                                try { f.src = 'about:blank'; } catch(e) {}
                                            });
                                        })();
                                    """, null)
                                    view?.stopLoading()
                                    view?.loadUrl("about:blank")
                                    view?.onPause()
                                    DebugLogManager.log("ANIPLEX_PLAYER", "WebView successfully silenced and navigated to about:blank AFTER cookie preservation.")
                                } catch (e: Exception) {
                                    DebugLogManager.log("ANIPLEX_PLAYER", "Error cleaning WebView: ${e.message}")
                                }
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                Handler(Looper.getMainLooper()).post {
                    try {
                        val parent = webView.parent as? ViewGroup
                        parent?.removeView(webView)
                        webView.destroy()
                        DebugLogManager.log("ANIPLEX_PLAYER", "Successfully detached and destroyed sniffer WebView")
                    } catch (e: Exception) {
                        DebugLogManager.log("ANIPLEX_PLAYER", "Error cleaning sniffer WebView on main loop: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                DebugLogManager.log("ANIPLEX_PLAYER", "Error cleaning sniffer WebView on dispose: ${e.message}")
            }
        }
    }

    fun resetPlayerState(newEpisodeEmbedUrl: String, isNewEpisode: Boolean = true) {
        sniffingEpisodeId = null
        val player = exoPlayerRef
        if (player != null) {
            val pos = player.currentPosition
            val dur = player.duration
            if (pos > 0) {
                if (!isNewEpisode) {
                    serverSwitchPosition = pos
                    DebugLogManager.log("ANIPLEX_PLAYER", "Cached current position $pos in resetPlayerState for server switch (duration: $dur).")
                    if (dur > 0) {
                        val ep = currentEpisode ?: episodes.find { it.id == episodeId }
                        viewModel.saveProgress(
                            animeId = animeId,
                            animeTitle = animeTitle,
                            episodeId = ep?.id ?: episodeId,
                            episodeNumber = ep?.number ?: episodeNumber,
                            episodeTitle = ep?.title ?: "Episode ${ep?.number ?: episodeNumber}",
                            progress = pos,
                            duration = dur
                        )
                    }
                }
            }
        }

        capturedStreamUrl = null
        capturedSubtitles.clear()
        currentSubtitleSelection = "en"
        durationMs = 0L
        currentPositionMs = 0L
        isBuffering = true
        isPlaying = false
        extractionState = ExtractionState.EXTRACTING
        if (isNewEpisode) {
            playbackPosition = 0L
        }
        retryCount = 0
        timeoutKey++
        
        retryPlaybackKey = 0
        playbackError = null
        pendingPlayWhenReady.value = false
        isControlsVisible = false
        showGestureIndicator = false

        extractedUserAgent = null
        extractedCookies = null
        interceptedReferer = null
        interceptedOrigin = null
        
        hasAttemptedBackupSubtitles = false
        viewModel.setFallbackStatusMessage(null)

        fun loadNewUrlAfterReset(url: String) {
            try {
                exoPlayerRef?.stop()
                exoPlayerRef?.clearMediaItems()
                
                webView.apply {
                    stopLoading()
                    clearHistory()
                    onPause()
                    loadUrl("about:blank")
                }
                
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        webView.onResume()
                        sniffingEpisodeId = activeEpisodeId
                        webView.loadUrl(url)
                        DebugLogManager.log("ANIPLEX_PLAYER", "WebView loadUrl triggered for: $url after flush delay")
                    } catch (e: Exception) {
                        sniffingEpisodeId = null
                        DebugLogManager.log("ANIPLEX_PLAYER", "Error loading embed URL after delay: ${e.message}")
                    }
                }, 200)
            } catch (e: Exception) {
                DebugLogManager.log("ANIPLEX_PLAYER", "Error resetting player WebView state: ${e.message}")
            }
        }

        if (isNewEpisode) {
            val prefs = context.getSharedPreferences("aniplex_saved_cookies", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            try {
                // Wipe cache and storage to prevent Cloudflare/Zoro error leak across shows/episodes
                webView.clearCache(true)
                webView.clearFormData()
                android.webkit.WebStorage.getInstance().deleteAllData()
                
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        loadNewUrlAfterReset(newEpisodeEmbedUrl)
                    }
                }
                cookieManager.flush()
                
                DebugLogManager.log("ANIPLEX_PLAYER", "Cleaned WebView cache, storage, and persistent/session cookies for new episode.")
            } catch (e: Exception) {
                DebugLogManager.log("ANIPLEX_PLAYER", "Error cleaning WebView cache: ${e.message}")
                loadNewUrlAfterReset(newEpisodeEmbedUrl)
            }
        } else {
            loadNewUrlAfterReset(newEpisodeEmbedUrl)
        }
    }

    fun fallbackToNextServer() {
        val curProvider = viewModel.activeProvider.value ?: preferredProvider
        if (curProvider != "zoro") {
            DebugLogManager.log("ANIPLEX_PLAYER", "fallbackToNextServer ignored because active provider is $curProvider")
            return
        }
        val currentIndex = availableServers.indexOf(selectedServer)
        if (currentIndex != -1 && currentIndex < availableServers.lastIndex) {
            val nextServer = availableServers[currentIndex + 1]
            selectedServer = nextServer
            val msg = "Server ${currentIndex + 1} extraction failed. Switching to Server ${currentIndex + 2}..."
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            DebugLogManager.log("ANIPLEX_PLAYER", msg)
            
            val nextUrl = getEmbedUrl(nextServer, activeEpisodeId, activeCategory)
            resetPlayerState(nextUrl, isNewEpisode = false)
        } else {
            // All Zoro servers exhausted - run the full fallback chain
            // (Gogoanime → AnimePahe → MegaPlay Direct → MegaPlay Iframe)
            DebugLogManager.log("ANIPLEX_PLAYER", "All Zoro servers exhausted. Running multi-provider fallback chain...")
            android.widget.Toast.makeText(context, "Switching to backup providers...", android.widget.Toast.LENGTH_SHORT).show()
            viewModel.runFallbackChain(activeCategory)
        }
    }

    LaunchedEffect(Unit) {
        fallbackToNextServerRef = { fallbackToNextServer() }
    }

    LaunchedEffect(webView) {
        webViewRef = webView
    }

    LaunchedEffect(uiState) {
        val state = uiState
        when (state) {
            is PlayerUiState.Success -> {
                capturedStreamUrl = state.stream.videoUrl
                extractionState = ExtractionState.READY
            }
            is PlayerUiState.WebViewFallback -> {
                if (extractionState != ExtractionState.READY) {
                    extractionState = ExtractionState.EXTRACTING
                }
                if (state.subtitles.isNotEmpty()) {
                    val sniffed = state.subtitles.map { sub ->
                        val urlLower = sub.url.lowercase()
                        val labelLower = sub.label.lowercase()
                        val mimeType = if (urlLower.endsWith(".ass") || urlLower.endsWith(".ssa")) "text/x-ass" else androidx.media3.common.MimeTypes.TEXT_VTT
                        val lang = when {
                            urlLower.contains("eng") || urlLower.contains("english") || labelLower.contains("eng") || labelLower.contains("english") -> "en"
                            urlLower.contains("ara") || labelLower.contains("ara") || urlLower.contains("-ar") -> "ar"
                            urlLower.contains("spa") || labelLower.contains("spa") || urlLower.contains("-es") -> "es"
                            urlLower.contains("fre") || labelLower.contains("fre") || urlLower.contains("-fr") -> "fr"
                            else -> "en"
                        }
                        SniffedSubtitle(url = sub.url, mime = mimeType, langCode = lang)
                    }
                    sniffed.forEach { item ->
                        if (capturedSubtitles.none { it.url == item.url }) {
                            capturedSubtitles.add(item)
                        }
                    }
                    DebugLogManager.log("ANIPLEX_SUBS", "Pre-populated ${sniffed.size} subtitles from Zoro API response")
                }
            }
            is PlayerUiState.IframeFallback -> {
                // Iframe fallback is self-contained; mark extraction as READY
                extractionState = ExtractionState.READY
            }
            else -> {
                extractionState = ExtractionState.EXTRACTING
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    playbackSpeed = viewModel.playbackSpeed
                    subtitlesEnabled = viewModel.subtitlesEnabled
                    qualityOption = viewModel.preferredQuality
                    setActiveCategory(viewModel.defaultAudioCategory)
                    // Only resume playback if ExoPlayer is already prepared and ready.
                    // If it's still buffering or idle, the pendingPlayWhenReady flag
                    // will handle starting playback when STATE_READY is reached.
                    val player = exoPlayerRef
                    if (localResumePlayback && player != null && player.playbackState == Player.STATE_READY) {
                        player.play()
                    } else if (localResumePlayback && player != null && player.playbackState != Player.STATE_IDLE) {
                        // Player is buffering — set the pending flag so it auto-plays when ready
                        pendingPlayWhenReady.value = true
                    }
                    webViewRef?.onResume()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayerRef?.pause()
                    pendingPlayWhenReady.value = false
                    webViewRef?.onPause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        webViewRef?.let { webView ->
                            webView.stopLoading()
                            webView.loadUrl("about:blank")
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    val parent = webView.parent as? ViewGroup
                                    parent?.removeView(webView)
                                    webView.destroy()
                                } catch (e: Exception) {
                                    DebugLogManager.log("ANIPLEX_PLAYER", "Error destroying webViewRef: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        DebugLogManager.log("ANIPLEX_PLAYER", "Error in Lifecycle ON_DESTROY cleanup: ${e.message}")
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Fullscreen behavior on landscape
    DisposableEffect(isLandscape) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            if (isLandscape) {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Reset orientation to portrait and restore screen brightness on leaving player screen
    DisposableEffect(Unit) {
        onDispose {
            if (!isTv) {
                val activity = context as? Activity
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                
                // Reset screen brightness to system default on leaving
                activity?.runOnUiThread {
                    val window = activity.window
                    val lp = window.attributes
                    lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }
            }
        }
    }

    LaunchedEffect(activeEpisodeId, activeCategory, selectedServer) {
        val initialEmbedUrl = if (preferredProvider == "zoro") {
            getEmbedUrl(selectedServer, activeEpisodeId, activeCategory)
        } else {
            "about:blank"
        }
        localResumePlayback = resumePlayback
        
        val isNewEpisode = (activeEpisodeId != lastEpisodeId)
        val isFirstLoad = lastEpisodeId.isEmpty()
        
        // Caching current position of ExoPlayer if it's a server or category switch
        if (!isNewEpisode && (selectedServer != lastServer || activeCategory != lastCategory)) {
            val player = exoPlayerRef
            if (player != null) {
                val currentPos = player.currentPosition
                if (currentPos > 0) {
                    serverSwitchPosition = currentPos
                    DebugLogManager.log("ANIPLEX_PLAYER", "Cached current position $currentPos before manual switch")
                }
            }
        }

        // Save progress of the PREVIOUS episode before resetting the player state
        if (isNewEpisode && !isFirstLoad) {
            val player = exoPlayerRef
            if (player != null) {
                val pos = player.currentPosition
                val dur = player.duration
                if (pos > 0 && dur > 0) {
                    val prevEp = episodes.find { it.id == lastEpisodeId }
                    viewModel.saveProgress(
                        animeId = animeId,
                        animeTitle = animeTitle,
                        episodeId = prevEp?.id ?: lastEpisodeId,
                        episodeNumber = prevEp?.number ?: episodeNumber,
                        episodeTitle = prevEp?.title ?: "Episode ${prevEp?.number ?: episodeNumber}",
                        progress = pos,
                        duration = dur
                    )
                    DebugLogManager.log("ANIPLEX_PLAYER", "Saved progress for previous episode $lastEpisodeId: $pos/$dur")
                }
            }
        }
        
        lastEpisodeId = activeEpisodeId
        lastCategory = activeCategory
        lastServer = selectedServer
        
        resetPlayerState(initialEmbedUrl, isNewEpisode = isNewEpisode)
        val apiServer = when (selectedServer) {
            "s-1" -> "hd-1"
            "s-2" -> "rapidcloud"
            "s-3" -> "megastream"
            else -> "hd-1"
        }
        val flowStartProgress = if (isFirstLoad) initialProgressParam else 0L
        viewModel.initialize(animeId, activeEpisodeId, activeCategory, apiServer, flowStartProgress)
    }

    // Timeout sniffer LaunchedEffect
    LaunchedEffect(uiState, timeoutKey, extractionState, selectedServer) {
        val curUiState = uiState
        if (curUiState is PlayerUiState.WebViewFallback && extractionState == ExtractionState.EXTRACTING) {
            delay(25000) // Increase timeout to 25s
            if (capturedStreamUrl == null) {
                if (retryCount < 2) {
                    retryCount++
                    timeoutKey++
                    val retryUrl = getEmbedUrl(selectedServer, activeEpisodeId, activeCategory)
                    try {
                        webView.loadUrl(retryUrl)
                    } catch (e: Exception) {
                        DebugLogManager.log("ANIPLEX_PLAYER", "Failed to load retry URL: ${e.message}")
                    }
                } else {
                    fallbackToNextServer()
                }
            }
        }
    }

    LaunchedEffect(capturedStreamUrl) {
        val stream = capturedStreamUrl
        if (stream != null) {
            if (autoStartDownloadAfterExtraction) {
                autoStartDownloadAfterExtraction = false
                val epId = currentEpisode?.id ?: activeEpisodeId
                val epTitle = currentEpisode?.title ?: "Episode $currentEpNum"
                val poster = animeDetail?.poster ?: ""
                DownloadManager.startDownload(
                    context = context,
                    episodeId = epId,
                    animeId = animeId,
                    animeTitle = animeTitle,
                    episodeNumber = currentEpNum,
                    episodeTitle = epTitle,
                    posterUrl = poster,
                    videoUrl = stream
                )
                Toast.makeText(context, "Download started for Episode $currentEpNum using Server $selectedServer", Toast.LENGTH_SHORT).show()
                // Auto show a standard local android notification to user so they see the progress
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val channelId = "anistream_downloads"
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        channelId,
                        "Downloads",
                        android.app.NotificationManager.IMPORTANCE_LOW
                    )
                    notificationManager.createNotificationChannel(channel)
                }
                val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Preparing Download")
                    .setContentText("Downloading $animeTitle - Episode $currentEpNum...")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                notificationManager.notify(epId.hashCode(), builder.build())
            }
        }
    }

    // Cookies extraction LaunchedEffect - now simplified since extraction is handled synchronously in the interceptor
    LaunchedEffect(capturedStreamUrl, extractionState) {
        val stream = capturedStreamUrl
        if (stream != null && extractionState == ExtractionState.EXTRACTING) {
            delay(1000)
            extractionState = ExtractionState.READY
        }
    }

    // WebView url auto load check
    LaunchedEffect(uiState, webView, selectedServer, activeEpisodeId) {
        val curUiState = uiState
        if (curUiState is PlayerUiState.WebViewFallback) {
            val embedUrl = if (curUiState.embedUrl.isNotEmpty()) {
                curUiState.embedUrl
            } else {
                getEmbedUrl(selectedServer, activeEpisodeId, activeCategory)
            }
            if (webView.url != embedUrl) {
                try {
                    webView.loadUrl(embedUrl)
                } catch (e: Exception) {
                    DebugLogManager.log("ANIPLEX_PLAYER", "Failed to load auto-check URL: ${e.message}")
                }
            }
        }
    }

    val downloads by DownloadManager.downloads.collectAsStateWithLifecycle()
    val downloadTask = downloads.find { it.episodeId == (currentEpisode?.id ?: activeEpisodeId) }
    val taskStatus = downloadTask?.status?.collectAsStateWithLifecycle()?.value
    val taskProgress = downloadTask?.progress?.collectAsStateWithLifecycle()?.value ?: 0f
    
    val initialProgress by viewModel.initialProgress.collectAsStateWithLifecycle()

    val exoPlayer = remember {
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        if (!viewModel.hevcDecoderEnabled) {
            renderersFactory.setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                    .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                if (mimeType.equals(androidx.media3.common.MimeTypes.VIDEO_H265, ignoreCase = true)) {
                    emptyList()
                } else {
                    decoders
                }
            }
        }

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true, 65536))
            .setBufferDurationsMs(
                120_000, // minBufferMs: 120 seconds ahead safety cushion
                600_000, // maxBufferMs: keep background downloading up to 10 minutes (600 seconds)
                2_500,   // bufferForPlaybackMs: starts right away (2.5s) to feel super snappy
                5_000    // bufferForPlaybackAfterRebufferMs: robust 5 seconds cushion if a stall happens
            )
            .setTargetBufferBytes(300 * 1024 * 1024) // 300MB buffer memory limit
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(
                if (viewModel.dolbyAtmosEnabled) 
                    androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE 
                else 
                    androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH
            )
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    LaunchedEffect(exoPlayer) {
        exoPlayerRef = exoPlayer
    }
    var hasSeekedToInitialProgress by remember(activeEpisodeId) { mutableStateOf(false) }

    val onPlaybackEndedAction by rememberUpdatedState {
        val nextEp = episodes.find { it.number == currentEpNum + 1 }
        if (viewModel.autoplayNextEpisode && nextEp != null && !isUpNextDismissed) {
            localResumePlayback = false
            val defaultServer = availableServers.firstOrNull() ?: "s-1"
            selectedServer = defaultServer
            activeEpisodeId = nextEp.id
        }
    }

    val onPlayerErrorAction by rememberUpdatedState {
        val currentProvider = viewModel.activeProvider.value
        if (currentProvider != "zoro") {
            viewModel.handlePlaybackError(activeCategory)
        } else {
            if (retryCount < 2) {
                retryCount++
                capturedStreamUrl = null
                extractedUserAgent = null
                extractedCookies = null
                capturedSubtitles.clear()
                extractionState = ExtractionState.EXTRACTING
                timeoutKey++
                val retryUrl = getEmbedUrl(selectedServer, activeEpisodeId, activeCategory)
                try {
                    webView.loadUrl(retryUrl)
                } catch (e: Exception) {
                    DebugLogManager.log("ANIPLEX_PLAYER", "Failed to load error retry URL: ${e.message}")
                }
            } else {
                fallbackToNextServer()
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isPlaying = exoPlayer.playWhenReady && playbackState == Player.STATE_READY
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "STATE_IDLE"
                    Player.STATE_BUFFERING -> "STATE_BUFFERING"
                    Player.STATE_READY -> "STATE_READY"
                    Player.STATE_ENDED -> "STATE_ENDED"
                    else -> "STATE_UNKNOWN"
                }
                DebugLogManager.log("ANIPLEX_EXOPLAYER", "Playback state changed to: $stateName | playWhenReady: ${exoPlayer.playWhenReady}")
                
                if (playbackState == Player.STATE_READY) {
                    // Consume the deferred play flag — this ensures audio doesn't
                    // start until the video surface is attached and rendering.
                    if (pendingPlayWhenReady.value) {
                        exoPlayer.playWhenReady = true
                        pendingPlayWhenReady.value = false
                        DebugLogManager.log("ANIPLEX_PLAYER", "Deferred playWhenReady consumed on STATE_READY")
                    }
                    val pos = serverSwitchPosition
                    if (pos != null && pos > 0) {
                        serverSwitchPosition = null
                        exoPlayer.seekTo(pos)
                        DebugLogManager.log("ANIPLEX_PLAYER", "ExoPlayer successfully seeked to serverSwitchPosition: $pos")
                    }
                }

                if (playbackState == Player.STATE_ENDED) {
                    onPlaybackEndedAction()
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
                DebugLogManager.log("ANIPLEX_EXOPLAYER", "Is playing state changed: $isPlayingNow")
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                super.onTracksChanged(tracks)
                DebugLogManager.log("ANIPLEX_EXOPLAYER", "Stream tracks changed (video/audio/text paths updated)")
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorCodeName = PlaybackException.getErrorCodeName(error.errorCode)
                val errMsg = "ExoPlayer error: $errorCodeName (Code: ${error.errorCode}) | Message: ${error.message}"
                DebugLogManager.log("ANIPLEX_PLAYER", errMsg, error)
                
                val isSourceError = error.errorCode in 2000..2999
                if (isSourceError) {
                    onPlayerErrorAction()
                } else {
                    playbackError = error.localizedMessage ?: "Playback error"
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            
            // Save progress of the last active episode
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (pos > 0 && dur > 0) {
                val ep = currentEpisode ?: episodes.find { it.id == activeEpisodeId }
                viewModel.saveProgress(
                    animeId = animeId,
                    animeTitle = animeTitle,
                    episodeId = ep?.id ?: activeEpisodeId,
                    episodeNumber = ep?.number ?: episodeNumber,
                    episodeTitle = ep?.title ?: "Episode ${ep?.number ?: episodeNumber}",
                    progress = pos,
                    duration = dur
                )
            }
            
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    LaunchedEffect(capturedStreamUrl, retryPlaybackKey, capturedSubtitles.size) {
        val stream = capturedStreamUrl
        if (stream != null) {
            // Wait 800ms for WebView sniffer to capture subtitles
            kotlinx.coroutines.delay(800)

            // Trigger fallback if Zoro subtitles list is empty (Only if Zoro is active provider)
            val curProvider = viewModel.activeProvider.value ?: preferredProvider
            if (curProvider == "zoro" && capturedSubtitles.isEmpty() && !hasAttemptedBackupSubtitles) {
                hasAttemptedBackupSubtitles = true
                DebugLogManager.log("ANIPLEX_SUBS", "Zoro subtitles empty. Fetching backup subtitles from Gogo/AnimePahe fallback API...")
                viewModel.getBackupSubtitles { backupSubs ->
                    if (backupSubs.isNotEmpty()) {
                        val sniffed = backupSubs.map { sub ->
                            SniffedSubtitle(
                                url = sub.url,
                                mime = if (sub.url.endsWith(".ass")) "text/x-ass" else androidx.media3.common.MimeTypes.TEXT_VTT,
                                langCode = "en"
                            )
                        }
                        capturedSubtitles.addAll(sniffed)
                        // Trigger reload with backup subtitles
                        retryPlaybackKey++
                    } else {
                        DebugLogManager.log("ANIPLEX_SUBS", "No soft subtitles in backup. Swapping provider to Gogoanime for hard subtitles...")
                        viewModel.runFallbackChain(activeCategory)
                    }
                }
            }

            val userAgentHeader = extractedUserAgent ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            
            val referer = interceptedReferer ?: when {
                stream.contains("megacloud") || stream.contains("megashare") || stream.contains("megacloud.tv") -> "https://megacloud.tv/"
                stream.contains("rapidcloud") || stream.contains("rabbitstream") || stream.contains("rapidcloud.co") -> "https://rapidcloud.co/"
                stream.contains("animeplay") || stream.contains("animeplay.cfd") -> "https://animeplay.cfd/"
                else -> "https://animeplay.cfd/" // Default fallback referer to main site instead of stream host to prevent 403 Forbidden
            }
            
            val origin = interceptedOrigin ?: try {
                val uri = android.net.Uri.parse(referer)
                "${uri.scheme}://${uri.host}"
            } catch (e: Exception) {
                "https://animeplay.cfd"
            }

            val requestProperties = mutableMapOf(
                "Referer" to referer,
                "Origin" to origin,
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "Sec-Fetch-Dest" to "empty"
            )
            
            // Combine runtime captured cookies (if any) with persisted ones
            val runtimeCookies = extractedCookies ?: ""
            val savedCookies = CookieSaver.getSavedCookiesHeader(context)
            val combinedCookies = if (runtimeCookies.isNotEmpty()) {
                if (savedCookies.isNotEmpty()) "$runtimeCookies; $savedCookies" else runtimeCookies
            } else {
                savedCookies
            }
            
            if (combinedCookies.isNotEmpty()) {
                requestProperties["Cookie"] = combinedCookies
                DebugLogManager.log("ANIPLEX_PLAYER", "ExoPlayer using Cookie header: $combinedCookies")
            }
            
            DebugLogManager.log("ANIPLEX_PLAYER", "ExoPlayer preparing stream with Referer: $referer | Origin: $origin | UA: $userAgentHeader")

            val okHttpClient = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent(userAgentHeader)
                .setDefaultRequestProperties(requestProperties)

            val subtitleConfigs = capturedSubtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                    .setMimeType(sub.mime)
                    .setLanguage(sub.langCode)
                    .setLabel(if (sub.langCode == "en") "English" else "Language: ${sub.langCode}")
                    .setSelectionFlags(if (sub.langCode == "en") C.SELECTION_FLAG_DEFAULT else 0)
                    .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                    .build()
            }

            val mediaItem = MediaItem.Builder()
                .setUri(stream)
                .setSubtitleConfigurations(subtitleConfigs)
                .build()

            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

            val isInitialized = exoPlayer.playbackState != Player.STATE_IDLE
            val currentPos = if (isInitialized) exoPlayer.currentPosition else -1L
            val playWhenReadyState = if (isInitialized) exoPlayer.playWhenReady else false

            exoPlayer.setMediaSource(mediaSource)

            // Apply Subtitle Defaults (Keep trackSelectionParameters logic)
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, currentSubtitleSelection == "off")
                .setPreferredTextLanguage(if (currentSubtitleSelection == "off") "en" else currentSubtitleSelection)
                .build()

            if (currentPos > 0L) {
                exoPlayer.seekTo(currentPos)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = playWhenReadyState
                DebugLogManager.log("ANIPLEX_PLAYER", "ExoPlayer updated media source and restored position to: $currentPos")
            } else {
                if (!hasSeekedToInitialProgress && localResumePlayback && initialProgress > 0L) {
                    exoPlayer.seekTo(initialProgress)
                    hasSeekedToInitialProgress = true
                    DebugLogManager.log("ANIPLEX_PLAYER", "Successfully seeked inside stream LaunchedEffect to saved progress: $initialProgress")
                }
                exoPlayer.prepare()
                // Don't start playback immediately — defer until STATE_READY
                // so audio doesn't leak while the video surface is still attaching.
                exoPlayer.playWhenReady = false
                pendingPlayWhenReady.value = localResumePlayback
            }
        }
    }

    // Only trigger this effect when the initialProgress actually arrives from Firestore or the player ref changes
    LaunchedEffect(initialProgress, exoPlayerRef, capturedStreamUrl) {
        if (!hasSeekedToInitialProgress && localResumePlayback && initialProgress > 0L && capturedStreamUrl != null) {
            val player = exoPlayerRef
            if (player != null) {
                player.seekTo(initialProgress)
                hasSeekedToInitialProgress = true
                DebugLogManager.log("ANIPLEX_PLAYER", "Successfully seeked to async saved progress: $initialProgress")
            }
        }
    }

    LaunchedEffect(playbackSpeed, exoPlayer) {
        exoPlayer?.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(currentSubtitleSelection, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        val disabled = currentSubtitleSelection == "off"
        val builder = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disabled)
        if (!disabled) {
            builder.setPreferredTextLanguage(currentSubtitleSelection)
        }
        player.trackSelectionParameters = builder.build()
    }

    LaunchedEffect(qualityOption, activeCategory, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        val builder = player.trackSelectionParameters.buildUpon()
        if (qualityOption.contains("1080")) {
            builder.setMaxVideoSize(1920, 1080)
        } else if (qualityOption.contains("720")) {
            builder.setMaxVideoSize(1280, 720)
        } else if (qualityOption.contains("480")) {
            builder.setMaxVideoSize(854, 480)
        } else if (qualityOption.contains("360")) {
            builder.setMaxVideoSize(640, 360)
        } else {
            builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        builder.setPreferredAudioLanguage(if (activeCategory == "dub") "en" else "ja")
        player.trackSelectionParameters = builder.build()
    }

    LaunchedEffect(exoPlayer, skipTimes) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            delay(500)
            currentPositionMs = player.currentPosition
            bufferedPositionMs = player.bufferedPosition
            if (player.duration > 0L) {
                durationMs = player.duration
            }
            
            // Auto skipping is disabled per user request so videos always start at 0:00 and manual skip buttons can be clicked instead.
            
            val now = System.currentTimeMillis()
            if (now - lastSavedProgressTime >= 30000L && player.isPlaying) {
                lastSavedProgressTime = now
                val ep = currentEpisode ?: episodes.find { it.id == activeEpisodeId }
                viewModel.saveProgress(
                    animeId = animeId,
                    animeTitle = animeTitle,
                    episodeId = ep?.id ?: activeEpisodeId,
                    episodeNumber = ep?.number ?: episodeNumber,
                    episodeTitle = ep?.title ?: "Episode ${ep?.number ?: episodeNumber}",
                    progress = player.currentPosition,
                    duration = player.duration
                )
            }
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying, controlsInteractionKey) {
        if (isControlsVisible && isPlaying) {
            delay(3000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(showGestureIndicator, gestureIndicatorType, gestureIndicatorValue) {
        if (showGestureIndicator) {
            delay(1000)
            showGestureIndicator = false
        }
    }

    BackHandler(enabled = isTv || isLandscape || showSettings) {
        if (showSettings) {
            showSettings = false
        } else if (isTv) {
            onBackClick()
        } else if (isLandscape) {
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val window = activity?.window
            if (window != null) {
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
            isLandscape = false
        }
    }

    val localFile = remember(currentEpisode?.id ?: activeEpisodeId) { 
        DownloadManager.getDownloadedFile(context, currentEpisode?.id ?: activeEpisodeId) 
    }
    val effectiveUiState = remember(uiState, localFile) {
        if (localFile != null && localFile.exists()) {
            PlayerUiState.Success(
                com.aniplex.app.domain.model.EpisodeStream(
                    videoUrl = "file://${localFile.absolutePath}",
                    isHls = localFile.name.endsWith(".m3u8")
                )
            )
        } else {
            uiState
        }
    }

    val nextEp = episodes.find { it.number == currentEpNum + 1 }
    val onNextEpisodeClick = nextEp?.let { ep ->
        {
            localResumePlayback = false
            val defaultServer = availableServers.firstOrNull() ?: "s-1"
            selectedServer = defaultServer
            activeEpisodeId = ep.id
        }
    }

    val state = PlayerScreenState(
        isLandscape = isLandscape,
        skipTimes = skipTimes,
        showSettings = showSettings,
        episodes = episodes,
        uiState = effectiveUiState,
        animeDetail = animeDetail,
        currentEpisode = currentEpisode,
        likeCount = likeCount,
        isLiked = isLiked,
        dislikeCount = dislikeCount,
        isDisliked = isDisliked,
        activeCategory = activeCategory,
        playbackSpeed = playbackSpeed,
        subtitlesEnabled = subtitlesEnabled,
        qualityOption = qualityOption,
        subtitleSizeSp = subtitleSizeSp,
        capturedStreamUrl = capturedStreamUrl,
        capturedSubtitles = capturedSubtitles,
        currentSubtitleSelection = currentSubtitleSelection,
        durationMs = durationMs,
        currentPositionMs = currentPositionMs,
        bufferedPositionMs = bufferedPositionMs,
        isBuffering = isBuffering,
        isPlaying = isPlaying,
        extractionState = extractionState,
        exoPlayerRef = exoPlayer,
        webViewRef = webView,
        localResumePlayback = localResumePlayback,
        retryCount = retryCount,
        timeoutKey = timeoutKey,
        extractedUserAgent = extractedUserAgent,
        extractedCookies = extractedCookies,
        playbackPosition = playbackPosition,
        downloadTaskStatus = taskStatus,
        taskProgress = taskProgress,
        initialProgress = initialProgress,
        autoplayNextEpisode = viewModel.autoplayNextEpisode,
        playbackError = playbackError,
        retryPlaybackKey = retryPlaybackKey,
        isControlsVisible = isControlsVisible,
        controlsInteractionKey = controlsInteractionKey,
        gestureIndicatorType = gestureIndicatorType,
        gestureIndicatorValue = gestureIndicatorValue,
        showGestureIndicator = showGestureIndicator,
        showAudioDialog = showAudioDialog,
        showSubtitlesDialog = showSubtitlesDialog,
        showQualityDialog = showQualityDialog,
        showSpeedDialog = showSpeedDialog,
        showSubtitleSizeDialog = showSubtitleSizeDialog,
        showVersionDialog = showVersionDialog,
        preferredAnimeVersion = preferredAnimeVersion,
        metadataScrollState = metadataScrollState,
        settingsScrollState = settingsScrollState,
        subtitleStyle = subtitleStyle,
        showSubtitleStyleDialog = showSubtitleStyleDialog,
        showEpisodesSelector = showEpisodesSelector,
        selectedServer = selectedServer,
        preferredProvider = preferredProvider,
        showServerDialog = showServerDialog,
        isDiagnosticsEnabled = isDiagnosticsEnabled,
        subtitleColor = subtitleColor,
        subtitleBgOpacity = subtitleBgOpacity,
        subtitleSizeScale = subtitleSizeScale,
        showSubtitleColorDialog = showSubtitleColorDialog,
        showSubtitleBgOpacityDialog = showSubtitleBgOpacityDialog,
        subtitlePosition = subtitlePosition,
        screenFitMode = screenFitMode,
        isUpNextDismissed = isUpNextDismissed,
        fallbackStatusMessage = fallbackStatusMessage
    )

    val callbacks = PlayerCallbacks(
        onBackClick = {
            DebugLogManager.log("USER_ACTION", "Clicked Back Button")
            onBackClick()
        },
        onAnimeClick = {
            DebugLogManager.log("USER_ACTION", "Clicked Anime Meta Details Link")
            onAnimeClick(animeId)
        },
        toggleLike = {
            DebugLogManager.log("USER_ACTION", "Toggled Like state")
            viewModel.toggleLike()
        },
        toggleDislike = {
            DebugLogManager.log("USER_ACTION", "Toggled Dislike state")
            viewModel.toggleDislike()
        },
        onDownloadClick = {
            DebugLogManager.log("USER_ACTION", "Clicked Download Button. Current download state: $taskStatus")
            if (taskStatus == null || taskStatus == DownloadStatus.FAILED) {
                showDownloadServerDialog = true
            } else {
                val epId = currentEpisode?.id ?: activeEpisodeId
                when (taskStatus) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                        DebugLogManager.log("USER_ACTION", "Pausing Active Download for episode ID: $epId")
                        DownloadManager.pauseDownload(context, epId)
                    }
                    DownloadStatus.PAUSED -> {
                        val streamUrl = DownloadManager.getStreamUrl(epId)
                        DebugLogManager.log("USER_ACTION", "Resuming Paused Download for episode ID: $epId using stream: $streamUrl")
                        DownloadManager.resumeDownload(context, epId, streamUrl)
                    }
                    DownloadStatus.COMPLETED -> {
                        Toast.makeText(context, "Episode $currentEpNum is already downloaded!", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        },
        onNextEpisodeClick = {
            DebugLogManager.log("USER_ACTION", "Clicked Next Episode Button")
            onNextEpisodeClick?.invoke()
        },
        onSettingsClick = {
            DebugLogManager.log("USER_ACTION", "Opened Player Settings Menu Panel")
            showSettings = true
        },
        onSettingsBackClick = {
            DebugLogManager.log("USER_ACTION", "Closed Player Settings Menu Panel")
            showSettings = false
        },
        onAutoplayChange = {
            DebugLogManager.log("USER_ACTION", "Toggled Autoplay Option: $it")
            viewModel.autoplayNextEpisode = it
        },
        onAudioChange = {
            DebugLogManager.log("USER_ACTION", "Selected Audio/Category: $it")
            viewModel.defaultAudioCategory = it
            setActiveCategory(it)
        },
        onSubtitleSelectionChange = {
            DebugLogManager.log("USER_ACTION", "Selected Subtitle Language Track: $it")
            currentSubtitleSelection = it
        },
        onQualityChange = {
            DebugLogManager.log("USER_ACTION", "Selected Quality Variant Option: $it")
            viewModel.preferredQuality = it
            qualityOption = it
        },
        onSpeedChange = {
            DebugLogManager.log("USER_ACTION", "Selected Playback Speed Scale: $it")
            viewModel.playbackSpeed = it
            playbackSpeed = it
        },
        onSubtitleSizeChange = {
            DebugLogManager.log("USER_ACTION", "Selected Subtitle FontSize multiplier: $it sp")
            subtitleSizeSp = it
        },
        onRetryClick = {
            val retryUrl = getEmbedUrl(selectedServer, currentEpisode?.id ?: activeEpisodeId, activeCategory)
            DebugLogManager.log("USER_ACTION", "Clicked Retry playback overlay button. Reloading extractor with: $retryUrl")
            resetPlayerState(retryUrl, isNewEpisode = false)
        },
        onStreamUrlCaptured = {
            DebugLogManager.log("INTERNALS", "Captured stream URL link: $it")
            capturedStreamUrl = it
        },
        onUserAgentExtracted = {
            DebugLogManager.log("INTERNALS", "Captured browser User-Agent: $it")
            extractedUserAgent = it
        },
        onCookiesExtracted = {
            DebugLogManager.log("INTERNALS", "Captured cookie data string")
            extractedCookies = it
        },
        onExtractionStateChanged = {
            DebugLogManager.log("INTERNALS", "Stream extraction process state transitioned: $it")
            extractionState = it
        },
        onDurationChanged = { durationMs = it },
        onPositionChanged = {
            if (Math.abs(currentPositionMs - it) > 2000) {
                DebugLogManager.log("USER_ACTION", "ExoPlayer seek triggered from: $currentPositionMs ms to: $it ms")
            }
            currentPositionMs = it
        },
        onBufferingChanged = { isBuffering = it },
        onPlayingChanged = { isPlaying = it },
        onWebViewRefChanged = { webViewRef = it },
        onExoPlayerRefChanged = { exoPlayerRef = it },
        onRetryCountChanged = { retryCount = it },
        onTimeoutKeyChanged = { timeoutKey = it },
        onPlaybackPositionChanged = { playbackPosition = it },
        onSaveProgress = { pos, dur ->
            val ep = currentEpisode ?: episodes.find { it.id == activeEpisodeId }
            viewModel.saveProgress(
                animeId = animeId,
                animeTitle = animeTitle,
                episodeId = ep?.id ?: activeEpisodeId,
                episodeNumber = ep?.number ?: episodeNumber,
                episodeTitle = ep?.title ?: "Episode ${ep?.number ?: episodeNumber}",
                progress = pos,
                duration = dur
            )
        },
        onPlaybackErrorChanged = { playbackError = it },
        onRetryPlaybackKeyChanged = { retryPlaybackKey = it },
        onControlsVisibilityToggle = {
            isControlsVisible = !isControlsVisible
            controlsInteractionKey++
        },
        onControlsInteraction = { controlsInteractionKey++ },
        onGestureChanged = { type, value, show ->
            gestureIndicatorType = type
            gestureIndicatorValue = value
            showGestureIndicator = show
        },
        onShowAudioDialogChange = {
            DebugLogManager.log("USER_ACTION", "Toggled Audio selection modal visibility: $it")
            showAudioDialog = it
        },
        onShowSubtitlesDialogChange = {
            DebugLogManager.log("USER_ACTION", "Toggled Subtitle selection modal visibility: $it")
            showSubtitlesDialog = it
        },
        onShowQualityDialogChange = {
            DebugLogManager.log("USER_ACTION", "Toggled Quality selection modal visibility: $it")
            showQualityDialog = it
        },
        onShowSpeedDialogChange = {
            DebugLogManager.log("USER_ACTION", "Toggled Playback speed selection modal visibility: $it")
            showSpeedDialog = it
        },
        onShowSubtitleSizeDialogChange = {
            DebugLogManager.log("USER_ACTION", "Toggled Subtitle sizing modal visibility: $it")
            showSubtitleSizeDialog = it
        },
        onVersionChange = { targetVersion ->
            DebugLogManager.log("USER_ACTION", "Selected preferred version track: $targetVersion")
            viewModel.setPreferredAnimeVersion(targetVersion) { msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                preferredAnimeVersion = viewModel.preferredAnimeVersion
            }
        },
        onShowVersionDialogChange = {
            DebugLogManager.log("USER_ACTION", "Toggled Release version selection modal visibility: $it")
            showVersionDialog = it
        },
        onSubtitleStyleChange = {
            DebugLogManager.log("USER_ACTION", "Changed Subtitle rendering styling preset: $it")
            subtitleStyle = it
        },
        onShowSubtitleStyleDialogChange = {
            DebugLogManager.log("USER_ACTION", "Toggled Subtitle styling customization modal visibility: $it")
            showSubtitleStyleDialog = it
        },
        onSubtitleColorChange = {
            subtitleColor = it
            viewModel.subtitleColor = it
        },
        onShowSubtitleColorDialogChange = {
            showSubtitleColorDialog = it
        },
        onSubtitleBgOpacityChange = {
            subtitleBgOpacity = it
            viewModel.subtitleBgOpacity = it
        },
        onShowSubtitleBgOpacityDialogChange = {
            showSubtitleBgOpacityDialog = it
        },
        onSubtitleSizeScaleChange = {
            subtitleSizeScale = it
            viewModel.subtitleSizeScale = it
        },
        onShowEpisodesSelectorChange = {
            DebugLogManager.log("USER_ACTION", "Toggled Episode selection drawer overlay visibility: $it")
            showEpisodesSelector = it
        },
        onEpisodeSelect = { clickedEp ->
            DebugLogManager.log("USER_ACTION", "Selected item from Episode selection list: Episode ${clickedEp.number} (ID: ${clickedEp.id}).")
            localResumePlayback = false
            val defaultServer = availableServers.firstOrNull() ?: "s-1"
            selectedServer = defaultServer
            activeEpisodeId = clickedEp.id
        },
        onServerSelected = { server ->
            DebugLogManager.log("USER_ACTION", "Switched current provider API/Server source to: $server")
            selectedServer = server
        },
        onShowServerDialogChange = { show ->
            DebugLogManager.log("USER_ACTION", "Toggled Active provider/server selection modal visibility: $show")
            showServerDialog = show
        },
        onDebugClick = {
            DebugLogManager.log("USER_ACTION", "Tapped diagnostics debugger trigger button")
            showDebugExportDialog = true
        },
        onDiagnosticsChange = { enabled ->
            isDiagnosticsEnabled = enabled
            DebugLogManager.isLoggingEnabled = enabled
            if (enabled) {
                DebugLogManager.log("USER_ACTION", "Diagnostics & Logs ENABLED")
            } else {
                DebugLogManager.clear()
            }
        },
        onScreenFitChange = { screenFitMode = it },
        onDismissUpNext = { isUpNextDismissed = true },
        onIframeServerSwitch = { viewModel.switchIframeServer(it) }
    )

    if (showDownloadServerDialog) {
        SettingsDialog(
            title = "Download Episode $currentEpNum",
            onDismiss = { showDownloadServerDialog = false }
        ) {
            val options = listOf(
                "s-1" to "Server 1 (Primary - CF)",
                "s-2" to "Server 2 (Backup - Animo)",
                "s-3" to "Server 3 (Alternative - Kryz)"
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Choose a source server to download this episode. If the stream URL is not captured yet, the app will briefly switch the player server to capture the direct stream link.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                items(options, key = { it.first }) { (key, label) ->
                    val isCurrentActive = selectedServer == key
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrentActive) CrunchyrollOrange.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (isCurrentActive) CrunchyrollOrange else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                showDownloadServerDialog = false
                                val epId = currentEpisode?.id ?: episodeId
                                val epTitle = currentEpisode?.title ?: "Episode $currentEpNum"
                                val poster = animeDetail?.poster ?: ""
                                
                                val cachedUrl = DownloadManager.getStreamUrl(epId)
                                if (key == selectedServer && cachedUrl != null) {
                                    DownloadManager.startDownload(
                                        context = context,
                                        episodeId = epId,
                                        animeId = animeId,
                                        animeTitle = animeTitle,
                                        episodeNumber = currentEpNum,
                                        episodeTitle = epTitle,
                                        posterUrl = poster,
                                        videoUrl = cachedUrl
                                    )
                                    Toast.makeText(context, "Download started using Server $key", Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedServer = key
                                    autoStartDownloadAfterExtraction = true
                                    Toast.makeText(context, "Preparing server $key URL capture...", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isCurrentActive) {
                                Text(
                                    text = "Active",
                                    color = CrunchyrollOrange,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDebugExportDialog) {
        val reportText = remember(showDebugExportDialog, state) {
            DebugLogManager.generateDebugReport(
                context = context,
                animeId = animeId,
                episodeId = currentEpisode?.id ?: episodeId,
                selectedServer = selectedServer,
                activeCategory = activeCategory,
                capturedStreamUrl = capturedStreamUrl,
                currentWebViewUrl = webViewRef?.url ?: "N/A"
            )
        }
        SettingsDialog(
            title = "Debug Logs & Cookies",
            onDismiss = { showDebugExportDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Use this tool to copy or share the extracted video player logs and active domain cookies. Sharing this report helps us debug playback issues directly.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFF151515), shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF333333), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = reportText,
                                color = Color(0xFF00FFCC),
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            // 1. Copy to clipboard
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Aniplex Debug Report", reportText)
                            clipboardManager.setPrimaryClip(clip)
                            
                            // 2. Save/Download to TXT
                            val uri = DebugLogManager.saveLogsToDownloads(context, reportText)
                            if (uri != null) {
                                Toast.makeText(context, "Copied & Saved to Downloads folder as .txt!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Copied! (TXT download failed or needs permission)", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Copy & Download (.txt)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Aniplex Debug Report", reportText)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Copy Logs", color = Color.White, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TITLE, "Aniplex Debug Report")
                                    putExtra(android.content.Intent.EXTRA_TEXT, reportText)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share Debug Report"))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Share Logs", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    PlayerScreenContent(
        state = state,
        callbacks = callbacks,
        isTv = isTv,
        modifier = modifier
    )
}

@Composable
private fun PlayerScreenContent(
    state: PlayerScreenState,
    callbacks: PlayerCallbacks,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var selectedChunkIndex by remember(state.episodes) { mutableStateOf(0) }
    
    LaunchedEffect(state.currentEpisode, state.episodes) {
        val curEp = state.currentEpisode
        if (curEp != null) {
            val index = state.episodes.indexOfFirst { it.id == curEp.id }
            if (index != -1) {
                selectedChunkIndex = index / 25
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlayerContainer(
                state = state,
                callbacks = callbacks,
                isTv = isTv,
                modifier = if (state.isLandscape) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }
            )
            
            if (!state.isLandscape) {
                // Metadata scrollable section
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(16.dp)
                        .verticalScroll(state.metadataScrollState)
                ) {
                    val animeTitle = state.animeDetail?.name ?: ""
                    val currentEpNum = state.currentEpisode?.number ?: 1
                    
                    Text(
                        text = animeTitle,
                        color = CrunchyrollOrange,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { callbacks.onAnimeClick() }
                            .padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "E$currentEpNum - ${state.currentEpisode?.title ?: "Episode $currentEpNum"}",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Tags
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(Color.DarkGray, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "16+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "•", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (state.activeCategory.equals("dub", true)) "Dubbed" else "Subtitled", color = Color.LightGray, fontSize = 14.sp)
                    }

                    if (state.preferredProvider == "zoro") {
                        // ACTIVE STREAMING SERVER MULTI-SOURCE SELECTOR (QUICK SELECTOR CHIPS)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "CHOOSE STREAMING SERVER",
                            color = Color.LightGray.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("s-1", "s-2", "s-3").forEach { srv ->
                                val isSelected = state.selectedServer == srv
                                val srvLabel = when (srv) {
                                    "s-1" -> "Server 1 (Primary - CF)"
                                    "s-2" -> "Server 2 (Backup - Animo)"
                                    "s-3" -> "Server 3 (Alternative - Kryz)"
                                    else -> srv
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) CrunchyrollOrange else Color.DarkGray.copy(alpha = 0.35f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) CrunchyrollOrange else Color.DarkGray.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (!isSelected) {
                                                callbacks.onServerSelected(srv)
                                                Toast.makeText(context, "Switching to $srvLabel...", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Cloud,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = when (srv) {
                                                "s-1" -> "Server 1"
                                                "s-2" -> "Server 2"
                                                "s-3" -> "Server 3"
                                                else -> srv
                                            },
                                            color = if (isSelected) Color.White else Color.LightGray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Actions row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            // Like
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { callbacks.toggleLike() }) {
                                Icon(
                                    imageVector = if (state.isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                    contentDescription = "Like",
                                    tint = if (state.isLiked) CrunchyrollOrange else Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatCount(state.likeCount),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // Dislike
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { callbacks.toggleDislike() }) {
                                Icon(
                                    imageVector = if (state.isDisliked) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt,
                                    contentDescription = "Dislike",
                                    tint = if (state.isDisliked) CrunchyrollOrange else Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatCount(state.dislikeCount),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = callbacks.onDownloadClick) {
                                when (state.downloadTaskStatus) {
                                    null -> {
                                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                                    }
                                    DownloadStatus.QUEUED -> {
                                        CircularProgressIndicator(
                                            color = CrunchyrollOrange,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    DownloadStatus.DOWNLOADING -> {
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                progress = { state.taskProgress },
                                                color = CrunchyrollOrange,
                                                trackColor = Color.DarkGray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Pause,
                                                contentDescription = "Pause",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    DownloadStatus.PAUSED -> {
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                progress = { state.taskProgress },
                                                color = Color.Gray,
                                                trackColor = Color.DarkGray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Resume",
                                                tint = CrunchyrollOrange,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    DownloadStatus.COMPLETED -> {
                                        Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded", tint = Color.Green)
                                    }
                                    DownloadStatus.FAILED -> {
                                        Icon(Icons.Default.Error, contentDescription = "Failed", tint = Color.Red)
                                    }
                                }
                            }
                            IconButton(onClick = {
                                Toast.makeText(context, "More options", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // In-Player Episodes Grid Section
                    if (state.episodes.isNotEmpty()) {
                        val totalEpisodes = state.episodes.size
                        val chunks = state.episodes.chunked(25)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Episodes List",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (chunks.size > 1) {
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .background(Color(0xFF1D1D1D), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
                                            .clickable { dropdownExpanded = true }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val startEp = selectedChunkIndex * 25 + 1
                                        val endEp = minOf((selectedChunkIndex + 1) * 25, totalEpisodes)
                                        Text(
                                            text = "Episodes $startEp-$endEp",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Episode range",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false },
                                        modifier = Modifier.background(Color(0xFF1D1D1D))
                                    ) {
                                        chunks.forEachIndexed { idx, _ ->
                                            val start = idx * 25 + 1
                                            val end = minOf((idx + 1) * 25, totalEpisodes)
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        text = "Episodes $start-$end", 
                                                        color = Color.White,
                                                        fontSize = 13.sp
                                                    ) 
                                                },
                                                onClick = {
                                                    selectedChunkIndex = idx
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFff4d4d))
                                )
                                Text(
                                    text = "Filler",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        // FlowRow episodes grid
                        val activeChunk = if (selectedChunkIndex in chunks.indices) chunks[selectedChunkIndex] else emptyList()
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeChunk.forEach { ep ->
                                val isCurrent = state.currentEpisode?.id == ep.id
                                val isFiller = ep.isFiller
                                
                                Box(
                                    modifier = Modifier
                                        .width(62.dp)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isCurrent) CrunchyrollOrange else Color(0xFF1D1D1D)
                                        )
                                        .border(
                                            width = if (isCurrent) 0.dp else if (isFiller) 1.5.dp else 1.dp,
                                            color = if (isCurrent) Color.Transparent 
                                                    else if (isFiller) Color(0xFFff4d4d) 
                                                    else Color(0xFF2C2C2C),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            if (!isCurrent) {
                                                callbacks.onEpisodeSelect(ep)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Ep ${ep.number}",
                                        color = if (isCurrent) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Description
                    val description = state.animeDetail?.description ?: "No description available."
                    var isDescriptionExpanded by remember { mutableStateOf(false) }
                    
                    Text(
                        text = description,
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (description.length > 100) {
                        Text(
                            text = if (isDescriptionExpanded) "Less Details" else "More Details",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrunchyrollOrange,
                            modifier = Modifier
                                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                .padding(vertical = 8.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Content Advisory
                    Text(text = "Content Advisory", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "16+", color = Color.Gray, fontSize = 14.sp)
                    Text(text = "Profanity, Suggestive Dialogue", color = Color.Gray, fontSize = 14.sp)

                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = state.showSettings,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            PlaybackSettingsOverlay(
                state = state,
                callbacks = callbacks
            )
        }

        AnimatedVisibility(
            visible = state.showEpisodesSelector,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            InPlayerEpisodeSelectorOverlay(
                state = state,
                callbacks = callbacks
            )
        }

        // EMERGENCY FLOATING DIAGNOSTIC BUTTON (Visible on top of everything, including loading and error states)
        if (state.isDiagnosticsEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .border(1.2.dp, CrunchyrollOrange.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                    .clickable { callbacks.onDebugClick() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Debug Diagnostics",
                        tint = CrunchyrollOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "DIAGNOSTICS / LOGS",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
        count >= 1000 -> String.format("%.1fK", count / 1000.0)
        else -> count.toString()
    }
}

data class SniffedSubtitle(val url: String, val mime: String, val langCode: String)

enum class ExtractionState {
    EXTRACTING,
    READY,
    ERROR
}

@Composable
fun PlayerContainer(
    state: PlayerScreenState,
    callbacks: PlayerCallbacks,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black)) {
        // Hidden WebView (always present, never disposed)
        AndroidView(
            factory = { state.webViewRef!! },
            modifier = Modifier.size(1.dp)
        )

        Crossfade(targetState = state.uiState, label = "PlayerStateTransition") { uiState ->
            when (uiState) {
                is PlayerUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CrunchyrollOrange)
                            if (state.fallbackStatusMessage != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = state.fallbackStatusMessage,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                is PlayerUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Failed to load stream: ${uiState.message}", color = Color.Red)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = callbacks.onBackClick, colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)) {
                                Text("Go Back", color = Color.White)
                            }
                        }
                    }
                }
                is PlayerUiState.Success, is PlayerUiState.WebViewFallback -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (state.extractionState) {
                            ExtractionState.EXTRACTING -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = CrunchyrollOrange)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        val loadingMessage = when (state.preferredProvider) {
                                            "gogoanime" -> {
                                                if (state.selectedServer == "s-1") "Connecting to Gogoanime primary server..."
                                                else "Connecting to Gogoanime backup server..."
                                            }
                                            else -> {
                                                when (state.selectedServer) {
                                                    "s-1" -> "Connecting to Zoro primary server..."
                                                    "s-2" -> "Connecting to Zoro backup server..."
                                                    "s-3" -> "Connecting to Megastream backup server..."
                                                    else -> "Connecting to primary server..."
                                                }
                                            }
                                        }
                                        Text(
                                            text = loadingMessage,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Extracting secure links and subtitles",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            ExtractionState.ERROR -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Extraction failed. HLS stream not found.",
                                            color = Color.Red,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = callbacks.onRetryClick,
                                            colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
                                        ) {
                                            Text("Retry", color = Color.White)
                                        }
                                    }
                                }
                            }
                            ExtractionState.READY -> {
                                if (state.capturedStreamUrl != null) {
                                    if (isTv) {
                                        TvExoVideoPlayer(
                                            state = state,
                                            callbacks = callbacks,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        ExoVideoPlayer(
                                            state = state,
                                            callbacks = callbacks,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is PlayerUiState.IframeFallback -> {
                    // Fullscreen interactive WebView for iframe fallback (MegaPlay, VidSrc, Embed.su)
                    Box(modifier = Modifier.fillMaxSize()) {
                        var iframeWebView by remember { mutableStateOf<android.webkit.WebView?>(null) }
                        var loadError by remember(uiState.iframeUrl) { mutableStateOf<String?>(null) }
                        
                        DisposableEffect(iframeWebView) {
                            onDispose {
                                iframeWebView?.let { webView ->
                                    try {
                                        webView.stopLoading()
                                        webView.loadUrl("about:blank")
                                        Handler(Looper.getMainLooper()).post {
                                            try {
                                                val parent = webView.parent as? ViewGroup
                                                parent?.removeView(webView)
                                                webView.destroy()
                                                DebugLogManager.log("ANIPLEX_PLAYER", "Successfully detached and destroyed iframe WebView")
                                            } catch (e: Exception) {
                                                DebugLogManager.log("ANIPLEX_PLAYER", "Error cleaning iframe WebView on main loop: ${e.message}")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        DebugLogManager.log("ANIPLEX_PLAYER", "Error cleaning iframe WebView on dispose: ${e.message}")
                                    }
                                }
                            }
                        }
                        
                        if (loadError != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Stream Offline",
                                    tint = CrunchyrollOrange,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Backup Stream Offline",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = loadError ?: "Failed to connect to the backup stream. Please switch to another Server or Category.",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            AndroidView(
                                factory = { ctx ->
                                    android.webkit.WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.mediaPlaybackRequiresUserGesture = false
                                        settings.allowContentAccess = true
                                        settings.loadWithOverviewMode = true
                                        settings.useWideViewPort = true
                                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                                        
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        }
                                        
                                        val cookieManager = android.webkit.CookieManager.getInstance()
                                        cookieManager.setAcceptCookie(true)
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                            cookieManager.setAcceptThirdPartyCookies(this, true)
                                        }

                                        setBackgroundColor(android.graphics.Color.BLACK)
                                        
                                        webViewClient = object : android.webkit.WebViewClient() {
                                            override fun onReceivedError(
                                                view: android.webkit.WebView?,
                                                request: android.webkit.WebResourceRequest?,
                                                error: android.webkit.WebResourceError?
                                            ) {
                                                super.onReceivedError(view, request, error)
                                                if (request?.isForMainFrame == true) {
                                                    val desc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                        error?.description?.toString()
                                                    } else null
                                                    loadError = desc ?: "Failed to load the streaming page."
                                                }
                                            }

                                            override fun onReceivedHttpError(
                                                view: android.webkit.WebView?,
                                                request: android.webkit.WebResourceRequest?,
                                                errorResponse: android.webkit.WebResourceResponse?
                                            ) {
                                                super.onReceivedHttpError(view, request, errorResponse)
                                                if (request?.isForMainFrame == true) {
                                                    val status = errorResponse?.statusCode ?: -1
                                                    if (status == 404 || status >= 500) {
                                                        loadError = "Server returned error code: $status"
                                                    }
                                                }
                                            }

                                            override fun shouldOverrideUrlLoading(
                                                view: android.webkit.WebView?,
                                                request: android.webkit.WebResourceRequest?
                                            ): Boolean {
                                                val url = request?.url?.toString() ?: return false
                                                val host = request.url.host ?: ""
                                                val allowedDomains = listOf(
                                                    "megaplay.buzz", "vidsrc.to", "vidsrc.me", "embed.su",
                                                    "kryzox.xyz", "animeplay.cfd", "4animo.xyz"
                                                )
                                                val isAllowed = allowedDomains.any { host.contains(it) }
                                                if (!isAllowed) {
                                                    DebugLogManager.log("IFRAME_WEBVIEW", "Blocked redirect/ad navigation to: $url")
                                                    return true // Block ad redirects
                                                }
                                                return false // Allow load
                                            }
                                        }

                                        webChromeClient = android.webkit.WebChromeClient()
                                        iframeWebView = this
                                        loadUrl(uiState.iframeUrl)
                                    }
                                },
                                update = { webView ->
                                    if (webView.url != uiState.iframeUrl) {
                                        webView.loadUrl(uiState.iframeUrl)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Floating back button overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.5f),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .clickable { callbacks.onBackClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "←",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Fallback provider label
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            val providerLabel = when (uiState.provider) {
                                "megaplay" -> "MegaPlay"
                                "vidsrc-to" -> "VidSrc.to"
                                "vidsrc-me" -> "VidSrc.me"
                                "embed-su" -> "Embed.su"
                                else -> "Backup Player"
                            }
                            Text(
                                text = "▶ $providerLabel (Iframe)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .background(
                                        Color.Black.copy(alpha = 0.5f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Backup Server Selector overlay at the bottom center
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(
                                        Color.Black.copy(alpha = 0.75f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.15f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val servers = listOf(
                                    "megaplay" to "MegaPlay",
                                    "vidsrc-to" to "VidSrc.to",
                                    "vidsrc-me" to "VidSrc.me",
                                    "embed-su" to "Embed.su"
                                )
                                servers.forEach { (srvId, srvName) ->
                                    val isActive = uiState.provider == srvId
                                    var isFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .focusable()
                                            .background(
                                                color = when {
                                                    isActive -> CrunchyrollOrange
                                                    isFocused -> Color.White.copy(alpha = 0.2f)
                                                    else -> Color.Transparent
                                                },
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                width = if (isFocused && !isActive) 1.5.dp else 0.dp,
                                                color = if (isFocused) Color.White else Color.Transparent,
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                callbacks.onIframeServerSwitch(srvId)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = srvName,
                                            color = if (isActive) Color.Black else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExoVideoPlayer(
    state: PlayerScreenState,
    callbacks: PlayerCallbacks,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    var scrubbingPositionMs by remember { mutableStateOf<Long?>(null) }
    var showChaptersSelector by remember { mutableStateOf(false) }
    val exoPlayer = state.exoPlayerRef

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (state.playbackError != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Playback Error: ${state.playbackError}", color = Color.Red)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            callbacks.onPlaybackErrorChanged(null)
                            callbacks.onRetryPlaybackKeyChanged(state.retryPlaybackKey + 1)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
                    ) {
                        Text("Retry", color = Color.White)
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        visibility = android.view.View.VISIBLE
                        useController = false
                        resizeMode = when (state.screenFitMode) {
                            "Stretch" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            "Zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        player?.addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(pState: Int) {
                                if (pState == Player.STATE_READY) {
                                    this@apply.invalidate()
                                    this@apply.requestLayout()
                                }
                            }
                        })

                        subtitleView?.visibility = android.view.View.VISIBLE
                        subtitleView?.apply {
                             setViewType(androidx.media3.ui.SubtitleView.VIEW_TYPE_WEB)
                             val parsedForegroundColor = when (state.subtitleColor.lowercase()) {
                                 "yellow" -> AndroidColor.YELLOW
                                 "green" -> AndroidColor.GREEN
                                 "cyan" -> AndroidColor.CYAN
                                 "blue" -> AndroidColor.BLUE
                                 "pink" -> AndroidColor.parseColor("#F472B6")
                                 "red" -> AndroidColor.RED
                                 "black" -> AndroidColor.parseColor("#1F2937")
                                 else -> AndroidColor.WHITE
                             }
                             val parsedBackgroundColor = AndroidColor.argb(
                                 (state.subtitleBgOpacity * 255).toInt().coerceIn(0, 255),
                                 0, 0, 0
                             )
                             val parsedEdgeType = when (state.subtitleStyle) {
                                 "classic_outline" -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                                 "default" -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                                 else -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                             }
                             val parsedEdgeColor = if (parsedEdgeType != androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE) {
                                 AndroidColor.BLACK
                             } else {
                                 AndroidColor.TRANSPARENT
                             }
                             val parsedTypeface = when (state.subtitleStyle) {
                                 "serif" -> android.graphics.Typeface.SERIF
                                 "monospace" -> android.graphics.Typeface.MONOSPACE
                                 "bold" -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                 else -> null
                             }
                             val styleCompat = androidx.media3.ui.CaptionStyleCompat(
                                 parsedForegroundColor,
                                 parsedBackgroundColor,
                                 AndroidColor.TRANSPARENT,
                                 parsedEdgeType,
                                 parsedEdgeColor,
                                 parsedTypeface
                             )
                             setStyle(styleCompat)
                             setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, state.subtitleSizeSp * state.subtitleSizeScale)
                            post {
                                val lp = layoutParams as? android.widget.FrameLayout.LayoutParams
                                if (lp != null) {
                                    lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                                    lp.bottomMargin = (this@apply.height * state.subtitlePosition).toInt().coerceAtLeast(0)
                                    layoutParams = lp
                                }
                            }
                        }
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                    view.resizeMode = when (state.screenFitMode) {
                        "Stretch" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        "Zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    val parsedForegroundColor = when (state.subtitleColor.lowercase()) {
                        "yellow" -> AndroidColor.YELLOW
                        "green" -> AndroidColor.GREEN
                        "cyan" -> AndroidColor.CYAN
                        "blue" -> AndroidColor.BLUE
                        "pink" -> AndroidColor.parseColor("#F472B6")
                        "red" -> AndroidColor.RED
                        "black" -> AndroidColor.parseColor("#1F2937")
                        else -> AndroidColor.WHITE
                    }
                    val parsedBackgroundColor = AndroidColor.argb(
                        (state.subtitleBgOpacity * 255).toInt().coerceIn(0, 255),
                        0, 0, 0
                    )
                    val parsedEdgeType = when (state.subtitleStyle) {
                        "classic_outline" -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                        "default" -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                        else -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                    }
                    val parsedEdgeColor = if (parsedEdgeType != androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE) {
                        AndroidColor.BLACK
                    } else {
                        AndroidColor.TRANSPARENT
                    }
                    val parsedTypeface = when (state.subtitleStyle) {
                        "serif" -> android.graphics.Typeface.SERIF
                        "monospace" -> android.graphics.Typeface.MONOSPACE
                        "bold" -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        else -> null
                    }
                    val styleCompat = androidx.media3.ui.CaptionStyleCompat(
                        parsedForegroundColor,
                        parsedBackgroundColor,
                        AndroidColor.TRANSPARENT,
                        parsedEdgeType,
                        parsedEdgeColor,
                        parsedTypeface
                    )
                    view.subtitleView?.setViewType(androidx.media3.ui.SubtitleView.VIEW_TYPE_WEB)
                    view.subtitleView?.setStyle(styleCompat)
                    view.subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, state.subtitleSizeSp * state.subtitleSizeScale)
                    view.subtitleView?.post {
                        val lp = view.subtitleView?.layoutParams as? android.widget.FrameLayout.LayoutParams
                        if (lp != null) {
                            lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                            lp.bottomMargin = (view.height * state.subtitlePosition).toInt().coerceAtLeast(0)
                            view.subtitleView?.layoutParams = lp
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                callbacks.onControlsVisibilityToggle()
                            },
                            onDoubleTap = { offset ->
                                if (exoPlayer != null) {
                                    val halfWidth = size.width / 2
                                    if (offset.x < halfWidth) {
                                        val targetPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                        exoPlayer.seekTo(targetPos)
                                        callbacks.onPositionChanged(targetPos)
                                    } else {
                                        val targetPos = (exoPlayer.currentPosition + 10000).coerceAtMost(state.durationMs)
                                        exoPlayer.seekTo(targetPos)
                                        callbacks.onPositionChanged(targetPos)
                                    }
                                    callbacks.onControlsInteraction()
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        val edgeThresholdPx = 50.dp.toPx()
                        var dragStartValue = 0f
                        var isLeftHalf = false
                        var isDragActiveByAgent = false
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15

                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                val screenWidth = size.width
                                val screenHeight = size.height
                                val startX = offset.x
                                val startY = offset.y

                                // Completely ignore touches starting near the screen edges
                                if (startX < edgeThresholdPx || startX > screenWidth - edgeThresholdPx ||
                                    startY < edgeThresholdPx || startY > screenHeight - edgeThresholdPx) {
                                    isDragActiveByAgent = false
                                    return@detectVerticalDragGestures
                                }

                                isDragActiveByAgent = true
                                val halfWidth = screenWidth / 2f
                                isLeftHalf = startX < halfWidth

                                if (isLeftHalf) {
                                    val lp = activity?.window?.attributes
                                    val currentBrightness = lp?.screenBrightness ?: -1f
                                    dragStartValue = if (currentBrightness < 0) 0.5f else currentBrightness
                                } else {
                                    val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                    dragStartValue = currentVol.toFloat() / maxVolume.toFloat()
                                }

                                callbacks.onGestureChanged(
                                    if (isLeftHalf) "brightness" else "volume",
                                    dragStartValue,
                                    true
                                )
                            },
                            onDragEnd = {
                                isDragActiveByAgent = false
                                callbacks.onGestureChanged(null, 0f, false)
                            },
                            onDragCancel = {
                                isDragActiveByAgent = false
                                callbacks.onGestureChanged(null, 0f, false)
                            },
                            onVerticalDrag = { change, dragAmountY ->
                                if (!isDragActiveByAgent) return@detectVerticalDragGestures

                                val screenHeight = size.height
                                val delta = -dragAmountY / screenHeight.toFloat()

                                change.consume()

                                if (isLeftHalf) {
                                    val newBrightness = (dragStartValue + delta).coerceIn(0.01f, 1f)
                                    dragStartValue = newBrightness
                                    activity?.runOnUiThread {
                                        val lp = activity.window.attributes
                                        lp.screenBrightness = newBrightness
                                        activity.window.attributes = lp
                                    }
                                    callbacks.onGestureChanged("brightness", newBrightness, true)
                                } else {
                                    val newVolumeFraction = (dragStartValue + delta).coerceIn(0f, 1f)
                                    dragStartValue = newVolumeFraction
                                    val targetVolume = (newVolumeFraction * maxVolume).toInt()
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                                    callbacks.onGestureChanged("volume", newVolumeFraction, true)
                                }
                            }
                        )
                    }
            )

            AnimatedVisibility(
                visible = state.isControlsVisible,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isLandscape) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp)
                            ) {
                                val animeTitle = state.animeDetail?.name ?: ""
                                val currentEpNum = state.currentEpisode?.number ?: 1
                                Text(
                                    text = animeTitle,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clickable { callbacks.onAnimeClick() }
                                        .padding(vertical = 4.dp)
                                )
                                Text(
                                    text = state.currentEpisode?.title ?: "Episode $currentEpNum",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            IconButton(
                                onClick = callbacks.onBackClick,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Sub/Dub Quick Toggle Button
                            Button(
                                onClick = {
                                    callbacks.onControlsInteraction()
                                    val nextCat = if (state.activeCategory.equals("dub", ignoreCase = true)) "sub" else "dub"
                                    callbacks.onAudioChange(nextCat)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black.copy(alpha = 0.5f),
                                    contentColor = CrunchyrollOrange
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CrunchyrollOrange),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .align(Alignment.CenterVertically)
                            ) {
                                Text(
                                    text = if (state.activeCategory.equals("dub", ignoreCase = true)) "DUB" else "SUB",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // In-Player Episode Selector Trigger
                            IconButton(
                                onClick = {
                                    callbacks.onControlsInteraction()
                                    callbacks.onShowEpisodesSelectorChange(true)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = "Select Episode",
                                    tint = CrunchyrollOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            if (callbacks.onNextEpisodeClick != null) {
                                IconButton(
                                    onClick = {
                                        callbacks.onControlsInteraction()
                                        callbacks.onNextEpisodeClick.invoke()
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next Episode",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    callbacks.onControlsInteraction()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cast,
                                    contentDescription = "Cast",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = callbacks.onSettingsClick,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    callbacks.onControlsInteraction()
                                    if (state.isLandscape) {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    } else {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen Toggle",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    if (!state.isBuffering && exoPlayer != null) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(48.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    callbacks.onControlsInteraction()
                                    val targetPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                    exoPlayer.seekTo(targetPos)
                                    callbacks.onPositionChanged(targetPos)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    callbacks.onControlsInteraction()
                                    exoPlayer.playWhenReady = !exoPlayer.isPlaying
                                },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(56.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    callbacks.onControlsInteraction()
                                    val targetPos = (exoPlayer.currentPosition + 10000).coerceAtMost(state.durationMs)
                                    exoPlayer.seekTo(targetPos)
                                    callbacks.onPositionChanged(targetPos)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp)
                    ) {
                        AnimatedVisibility(
                            visible = scrubbingPositionMs != null,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(animationSpec = androidx.compose.animation.core.tween(150)) { it / 2 },
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(animationSpec = androidx.compose.animation.core.tween(150)) { it / 2 },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            scrubbingPositionMs?.let { pos ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    ScrubbingThumbnail(
                                        videoUrl = state.capturedStreamUrl,
                                        positionMs = pos,
                                        durationMs = state.durationMs,
                                        fallbackImageUrl = state.animeDetail?.poster,
                                        modifier = Modifier.border(1.5.dp, Color(0xFFF5A623), RoundedCornerShape(8.dp))
                                    )
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black.copy(alpha = 0.85f))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = Color(0xFFF5A623),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = formatTime(pos),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayPositionMs = scrubbingPositionMs ?: state.currentPositionMs

                            Text(
                                text = formatTime(displayPositionMs),
                                color = Color.White,
                                fontSize = 12.sp
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val bufferedPercent = if (state.durationMs > 0) (state.bufferedPositionMs.toFloat() / state.durationMs.toFloat()) else 0f
                                val playPercent = if (state.durationMs > 0) (displayPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f) else 0f

                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .padding(horizontal = 7.dp)
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val r = h / 2

                                    // 1. Draw Gray Inactive Track
                                    drawRoundRect(
                                        color = Color(0xFF4D4D4D),
                                        size = size,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                                    )

                                    // 2. Draw Semi-translucent White Buffered Track
                                    val bWidth = w * bufferedPercent.coerceIn(0f, 1f)
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.35f),
                                        size = androidx.compose.ui.geometry.Size(bWidth, h),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                                    )

                                    // 3. Draw Sleek Thin Orange Active Playback Track
                                    val playWidth = w * playPercent
                                    drawRoundRect(
                                        color = Color(0xFFF5A623),
                                        size = androidx.compose.ui.geometry.Size(playWidth, h),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                                    )
                                }

                                Slider(
                                    value = if (state.durationMs > 0) (displayPositionMs.toFloat() / state.durationMs.toFloat()) else 0f,
                                    onValueChange = { fraction ->
                                        if (state.durationMs > 0) {
                                            callbacks.onControlsInteraction()
                                            val targetPos = (fraction * state.durationMs).toLong()
                                            scrubbingPositionMs = targetPos
                                        }
                                    },
                                    onValueChangeFinished = {
                                        val finalPos = scrubbingPositionMs
                                        if (finalPos != null && exoPlayer != null) {
                                            exoPlayer.seekTo(finalPos)
                                            callbacks.onPositionChanged(finalPos)
                                        }
                                        scrubbingPositionMs = null
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFF5A623),
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent
                                    ),
                                    thumb = {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .background(Color(0xFFF5A623), CircleShape)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Segment markers ticks overlay
                                if (state.durationMs > 0) {
                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                                        val width = maxWidth
                                        if (state.skipTimes.introStart > 0) {
                                            val introFraction = state.skipTimes.introStart.toFloat() / state.durationMs.toFloat()
                                            if (introFraction in 0f..1f) {
                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = width * introFraction - 3.dp)
                                                        .size(6.dp)
                                                        .background(Color.White, CircleShape)
                                                        .border(1.dp, CrunchyrollOrange, CircleShape)
                                                        .align(Alignment.CenterStart)
                                                )
                                            }
                                        }
                                        if (state.skipTimes.introEnd > 0) {
                                            val introEndFraction = state.skipTimes.introEnd.toFloat() / state.durationMs.toFloat()
                                            if (introEndFraction in 0f..1f) {
                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = width * introEndFraction - 3.dp)
                                                        .size(6.dp)
                                                        .background(Color.White, CircleShape)
                                                        .border(1.dp, CrunchyrollOrange, CircleShape)
                                                        .align(Alignment.CenterStart)
                                                )
                                            }
                                        }
                                        if (state.skipTimes.outroStart > 0) {
                                            val outroFraction = state.skipTimes.outroStart.toFloat() / state.durationMs.toFloat()
                                            if (outroFraction in 0f..1f) {
                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = width * outroFraction - 3.dp)
                                                        .size(6.dp)
                                                        .background(Color.White, CircleShape)
                                                        .border(1.dp, CrunchyrollOrange, CircleShape)
                                                        .align(Alignment.CenterStart)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = formatTime(state.durationMs),
                                color = Color.White,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            // Chapters trigger button
                            IconButton(
                                onClick = {
                                    callbacks.onControlsInteraction()
                                    showChaptersSelector = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmarks,
                                    contentDescription = "Chapters",
                                    tint = CrunchyrollOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (state.isBuffering) {
                CircularProgressIndicator(
                    color = CrunchyrollOrange,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            }

            if (state.showGestureIndicator) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (state.gestureIndicatorType == "brightness") Icons.Default.WbSunny else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        
                        LinearProgressIndicator(
                            progress = { state.gestureIndicatorValue },
                            color = CrunchyrollOrange,
                            trackColor = Color.DarkGray,
                            modifier = Modifier
                                .width(80.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            
            // Skip Intro / Skip Outro Floating Button Overlay
            val hasIntroNow = state.skipTimes.isDuringIntro(state.currentPositionMs)
            val hasOutroNow = state.skipTimes.isDuringOutro(state.currentPositionMs)
            
            if (hasIntroNow || hasOutroNow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 96.dp, end = 24.dp)
                ) {
                    Button(
                        onClick = {
                            if (exoPlayer != null) {
                                val targetPos = if (hasIntroNow) state.skipTimes.introEnd else state.skipTimes.outroEnd
                                exoPlayer.seekTo(targetPos)
                                callbacks.onPositionChanged(targetPos)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrunchyrollOrange,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Skip Scene",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasIntroNow) "Skip Intro" else "Skip Outro",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val currentEpNum = state.currentEpisode?.number ?: 1
            val nextEp = state.episodes.find { it.number == currentEpNum + 1 }
            val timeRemainingMs = state.durationMs - state.currentPositionMs
            val showUpNext = nextEp != null && 
                             !state.isUpNextDismissed && 
                             state.durationMs > 0 && 
                             timeRemainingMs in 1..15000L
                             
            if (showUpNext && nextEp != null) {
                UpNextOverlay(
                    nextEpNumber = nextEp.number,
                    nextEpTitle = nextEp.title,
                    posterUrl = state.animeDetail?.poster,
                    timeRemainingSeconds = (timeRemainingMs / 1000).toInt().coerceAtLeast(0),
                    onDismiss = {
                        callbacks.onDismissUpNext()
                    },
                    onPlayClick = {
                        callbacks.onNextEpisodeClick?.invoke()
                    },
                    isTv = false,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (hasIntroNow || hasOutroNow) 152.dp else 96.dp, end = 24.dp)
                )
            }

            if (showChaptersSelector) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable { showChaptersSelector = false },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color(0xFF141414),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(if (state.isLandscape) 0.5f else 0.85f)
                            .fillMaxHeight(0.7f)
                            .clickable(enabled = false) {}
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Episode Chapters",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { showChaptersSelector = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(vertical = 8.dp))
                            
                            val chapters = remember(state.skipTimes, state.durationMs) {
                                val list = mutableListOf<SegmentChapter>()
                                list.add(SegmentChapter("Hook / Prologue", 0L))
                                
                                if (state.skipTimes.introStart > 0 && state.skipTimes.introEnd > state.skipTimes.introStart) {
                                    list.add(SegmentChapter("Opening Song (Intro)", state.skipTimes.introStart))
                                    list.add(SegmentChapter("Canon Episode Content", state.skipTimes.introEnd))
                                } else {
                                    list.add(SegmentChapter("Episode Intro", 90000L))
                                    list.add(SegmentChapter("Main Story Content", 180000L))
                                }
                                
                                if (state.durationMs > 720000L) {
                                    list.add(SegmentChapter("Midpoint Eyecatch", 720000L))
                                }
                                
                                if (state.skipTimes.outroStart > 0 && state.skipTimes.outroEnd > state.skipTimes.outroStart) {
                                    list.add(SegmentChapter("Ending Credits", state.skipTimes.outroStart))
                                    list.add(SegmentChapter("Next Episode Preview", state.skipTimes.outroEnd))
                                } else if (state.durationMs > 1300000L) {
                                    list.add(SegmentChapter("Ending Theme Credits", 1290000L))
                                    list.add(SegmentChapter("Next Episode Teaser", 1380000L))
                                }
                                
                                list.filter { it.startMs < state.durationMs }
                            }
                            
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(chapters, key = { "${it.title}_${it.startMs}" }) { chap ->
                                    val isActive = exoPlayer?.currentPosition?.let { currentPos ->
                                        val index = chapters.indexOf(chap)
                                        val nextStart = if (index < chapters.size - 1) chapters[index + 1].startMs else state.durationMs
                                        currentPos >= chap.startMs && currentPos < nextStart
                                    } ?: false
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isActive) Color(0xFF2E1A0C) else Color(0xFF1D1D1D),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isActive) CrunchyrollOrange else Color(0xFF2C2C2C),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                exoPlayer?.seekTo(chap.startMs)
                                                callbacks.onPositionChanged(chap.startMs)
                                                showChaptersSelector = false
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isActive) Icons.Default.PlayCircle else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isActive) CrunchyrollOrange else Color.LightGray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = chap.title,
                                                color = if (isActive) CrunchyrollOrange else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = formatTime(chap.startMs),
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
fun ScrubbingThumbnail(
    videoUrl: String?,
    positionMs: Long,
    durationMs: Long,
    fallbackImageUrl: String?,
    modifier: Modifier = Modifier
) {
    // Keep a cache of already loaded bitmaps to make dragging extremely smooth and efficient
    val bitmapCache = remember { LruCache<Long, Bitmap>(100) }

    // Round position to nearest 1000ms (1 second) to maximize cache hits
    val roundedPositionS = positionMs / 1000

    var thumbnailBitmap by remember(videoUrl, positionMs) { 
        mutableStateOf<Bitmap?>(bitmapCache.get(roundedPositionS)) 
    }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var retriever by remember(videoUrl) { mutableStateOf<MediaMetadataRetriever?>(null) }

    LaunchedEffect(videoUrl) {
        if (!videoUrl.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val r = MediaMetadataRetriever().apply {
                        setDataSource(videoUrl, HashMap<String, String>())
                    }
                    withContext(Dispatchers.Main) {
                        retriever = r
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } else {
            retriever = null
        }
    }

    DisposableEffect(retriever) {
        onDispose {
            val r = retriever
            if (r != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        r.release()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
    }

    LaunchedEffect(videoUrl, roundedPositionS, retriever) {
        val currentRetriever = retriever ?: return@LaunchedEffect

        val cached = bitmapCache.get(roundedPositionS)
        if (cached != null) {
            thumbnailBitmap = cached
            return@LaunchedEffect
        }

        isLoading = true
        // Debounce dragging requests by 60ms so fast dragging doesn't choke the thread/network
        delay(60)

        withContext(Dispatchers.IO) {
            try {
                val timeUs = roundedPositionS * 1000 * 1000
                val frame = currentRetriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, 240, 135, true)
                    bitmapCache.put(roundedPositionS, scaled)
                    withContext(Dispatchers.Main) {
                        thumbnailBitmap = scaled
                        isLoading = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .width(160.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = "Preview Frame",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            if (!fallbackImageUrl.isNullOrEmpty()) {
                val progressFraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                AsyncImage(
                    model = fallbackImageUrl,
                    contentDescription = "Preview Fallback",
                    contentScale = ContentScale.Crop,
                    alignment = androidx.compose.ui.BiasAlignment(
                        horizontalBias = (progressFraction * 2f - 1f).coerceIn(-1f, 1f),
                        verticalBias = 0f
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1F1F1F))
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFF5A623),
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsOverlay(
    state: PlayerScreenState,
    callbacks: PlayerCallbacks,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val speedFocusRequester = remember { FocusRequester() }
    val fitFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val qualityFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            speedFocusRequester.requestFocus()
        } catch (e: Exception) {}
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .onPreviewKeyEvent { event ->
                // Intercept Back key BEFORE it reaches any child (prevents gray-state trap)
                if (event.key == Key.Back && event.type == KeyEventType.KeyDown) {
                    callbacks.onSettingsBackClick()
                    true
                } else false
            }
            .clickable { callbacks.onSettingsBackClick() }
    ) {
        // Sidebar Content
        Row(
            modifier = Modifier
                .width(361.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
        ) {
            // Left divider line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF222222))
            )
            Surface(
                color = Color(0xFF0F0F0F),
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .clickable(enabled = false) {} // Prevent click propagation to background
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = callbacks.onSettingsBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = "Playback Settings",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = Color(0xFF222222))

                // Playback Speed
                SettingsSectionHeader("PLAYBACK SPEED")
                val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
                val currentSpeedIndex = speeds.indexOf(state.playbackSpeed).coerceAtLeast(1)
                SettingsButtonGroup(
                    items = speeds.map { if (it == 1.0f) "Normal" else "${it}x" },
                    selectedIndex = currentSpeedIndex,
                    onItemSelected = { index ->
                        callbacks.onSpeedChange(speeds[index])
                    },
                    focusRequester = speedFocusRequester,
                    onUpPressed = { subtitleFocusRequester.requestFocus() },
                    onDownPressed = { fitFocusRequester.requestFocus() }
                )

                // Screen Fit
                SettingsSectionHeader("SCREEN FIT")
                val fitModes = listOf("Fit", "Stretch", "Zoom")
                val currentFitIndex = fitModes.indexOf(state.screenFitMode).coerceAtLeast(0)
                SettingsButtonGroup(
                    items = fitModes,
                    selectedIndex = currentFitIndex,
                    onItemSelected = { index ->
                        callbacks.onScreenFitChange(fitModes[index])
                    },
                    focusRequester = fitFocusRequester,
                    onUpPressed = { speedFocusRequester.requestFocus() },
                    onDownPressed = { audioFocusRequester.requestFocus() }
                )

                // Audio
                SettingsSectionHeader("AUDIO")
                val audioModes = listOf("sub", "dub")
                val audioLabels = listOf("Subbed", "Dubbed")
                val currentAudioIndex = audioModes.indexOf(state.activeCategory).coerceAtLeast(0)
                SettingsButtonGroup(
                    items = audioLabels,
                    selectedIndex = currentAudioIndex,
                    onItemSelected = { index ->
                        callbacks.onAudioChange(audioModes[index])
                    },
                    focusRequester = audioFocusRequester,
                    onUpPressed = { fitFocusRequester.requestFocus() },
                    onDownPressed = { qualityFocusRequester.requestFocus() }
                )

                // Quality
                SettingsSectionHeader("QUALITY")
                val qualities = listOf("Auto", "1080p", "720p", "480p")
                val currentQualityIndex = qualities.indexOf(state.qualityOption).coerceAtLeast(0)
                SettingsButtonGroup(
                    items = qualities,
                    selectedIndex = currentQualityIndex,
                    onItemSelected = { index ->
                        callbacks.onQualityChange(qualities[index])
                    },
                    focusRequester = qualityFocusRequester,
                    onUpPressed = { audioFocusRequester.requestFocus() },
                    onDownPressed = { subtitleFocusRequester.requestFocus() }
                )

                // Subtitles selection
                SettingsSectionHeader("SUBTITLES/CC")
                val subtitleTracks = state.capturedSubtitles.map { it.langCode } + "off"
                val subtitleLabels = state.capturedSubtitles.map { 
                    when (it.langCode) {
                        "en" -> "English"
                        "ar" -> "Arabic"
                        "es" -> "Spanish"
                        "fr" -> "French"
                        else -> "Track: ${it.langCode}"
                    }
                } + "Off"
                val currentSubIndex = subtitleTracks.indexOf(state.currentSubtitleSelection).coerceAtLeast(0)
                SettingsButtonGroup(
                    items = subtitleLabels,
                    selectedIndex = currentSubIndex,
                    onItemSelected = { index ->
                        callbacks.onSubtitleSelectionChange(subtitleTracks[index])
                    },
                    focusRequester = subtitleFocusRequester,
                    onUpPressed = { qualityFocusRequester.requestFocus() },
                    onDownPressed = { speedFocusRequester.requestFocus() }
                )

            }
        }
    }
}
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        color = Color.Gray,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
fun SettingsButtonGroup(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    onDownPressed: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            var isFocused by remember { mutableStateOf(false) }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) CrunchyrollOrange else Color(0xFF222222))
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) Color.White else if (isSelected) CrunchyrollOrange else Color(0xFF333333),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .then(if (isSelected && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                // Fire selection on KeyUp to avoid long-press requirement
                                if (event.type == KeyEventType.KeyUp) {
                                    onItemSelected(index)
                                }
                                true // Consume both KeyDown and KeyUp
                            }
                            Key.DirectionUp -> {
                                if (event.type == KeyEventType.KeyDown && onUpPressed != null) {
                                    onUpPressed()
                                    true
                                } else event.type == KeyEventType.KeyDown && onUpPressed != null
                            }
                            Key.DirectionDown -> {
                                if (event.type == KeyEventType.KeyDown && onDownPressed != null) {
                                    onDownPressed()
                                    true
                                } else event.type == KeyEventType.KeyDown && onDownPressed != null
                            }
                            else -> false
                        }
                    }
                    .clickable { onItemSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.White, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFF5A623),
                checkedTrackColor = Color(0xFFF5A623).copy(alpha = 0.5f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@Composable
private fun SettingsClickableRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.White, fontSize = 16.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (value.isNotEmpty()) {
                Text(text = value, color = Color(0xFFB3B3B3), fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFB3B3B3),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                content()
            }
        },
        containerColor = Color(0xFF1F1F1F),
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFF5A623))
            }
        },
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun SettingsDialogRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFFF5A623),
                unselectedColor = Color.Gray
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun InPlayerEpisodeSelectorOverlay(
    state: PlayerScreenState,
    callbacks: PlayerCallbacks,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = true, onClick = { callbacks.onShowEpisodesSelectorChange(false) })
    ) {
        Surface(
            color = Color(0xFF141414),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C2C)),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier
                .fillMaxWidth(if (state.isLandscape) 0.5f else 1f)
                .fillMaxHeight(if (state.isLandscape) 1f else 0.65f)
                .align(if (state.isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .clickable(enabled = false) {}
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Episodes (${state.episodes.size})",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { callbacks.onShowEpisodesSelectorChange(false) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
                
                HorizontalDivider(color = Color(0xFF222222))
                
                if (state.episodes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No episodes available", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.episodes, key = { it.id }) { ep ->
                            val isCurrent = state.currentEpisode?.id == ep.id
                            Surface(
                                color = if (isCurrent) Color(0xFF2E1A0C) else Color(0xFF1D1D1D),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCurrent) CrunchyrollOrange else Color(0xFF2C2C2C)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        callbacks.onShowEpisodesSelectorChange(false)
                                        callbacks.onEpisodeSelect(ep)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (isCurrent) CrunchyrollOrange else Color(0xFF2C2C2C),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ep.number.toString(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ep.title.ifEmpty { "Episode ${ep.number}" },
                                            color = if (isCurrent) CrunchyrollOrange else Color.White,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (ep.isFiller) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF8B0000), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "FILLER",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Playing",
                                            tint = CrunchyrollOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class SegmentChapter(val title: String, val startMs: Long)

@Composable
fun UpNextOverlay(
    nextEpNumber: Int,
    nextEpTitle: String,
    posterUrl: String?,
    timeRemainingSeconds: Int,
    onDismiss: () -> Unit,
    onPlayClick: () -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .padding(16.dp)
            .width(if (isTv) 320.dp else 280.dp)
            .background(Color(0xFF141414), RoundedCornerShape(12.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) CrunchyrollOrange else Color(0xFF2C2C2C),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(isTv)
            .clickable(enabled = isTv) { onPlayClick() }
            .padding(12.dp)
    ) {
        // Close Button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "UP NEXT",
                color = CrunchyrollOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Poster image with Play button overlay
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 90.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF222222))
                ) {
                    if (!posterUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = nextEpTitle,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { onPlayClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Info Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = nextEpTitle,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Episode $nextEpNumber",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = if (timeRemainingSeconds <= 10) {
                            "Autoplay in ${timeRemainingSeconds}s"
                        } else {
                            "Up next soon"
                        },
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            // Play Now Button
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrunchyrollOrange,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Text(
                    text = "Play Now",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

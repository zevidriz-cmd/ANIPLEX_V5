package com.aniplex.app.presentation.screens.player.components

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aniplex.app.domain.model.EpisodeStream
import com.aniplex.app.theme.CrunchyrollOrange
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

import androidx.compose.animation.core.tween

@Composable
fun CustomExoPlayer(
    stream: EpisodeStream,
    playbackSpeed: Float,
    subtitlesEnabled: Boolean,
    animeTitle: String,
    episodeTitle: String,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNextEpisodeClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var bufferedPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }

    val exoPlayer = remember {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true, 65536))
            .setBufferDurationsMs(
                120_000, // minBufferMs: 120 seconds ahead to start pausing active downloads if safe
                600_000, // maxBufferMs: keep background downloading up to 10 minutes (600 seconds)
                2_500,   // bufferForPlaybackMs: starts right away (2.5s) to feel super snappy
                5_000    // bufferForPlaybackAfterRebufferMs: robust 5 seconds cushion if a stall happens
            )
            .setTargetBufferBytes(300 * 1024 * 1024) // 300MB buffer memory limit
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
            val mediaItemBuilder = MediaItem.Builder().setUri(stream.videoUrl)
            if (stream.isHls) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            
            // Add subtitles
            if (stream.subtitles.isNotEmpty()) {
                val subtitleConfigs = stream.subtitles.map { sub ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(if (sub.url.endsWith(".ass")) MimeTypes.TEXT_SSA else MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setLabel(sub.label)
                        .setSelectionFlags(if (sub.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                }
                mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
            }
            
            setMediaItem(mediaItemBuilder.build())
            prepare()
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    isPlaying = isPlayingNow
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isLoading = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        totalDuration = duration
                    }
                }
            })
        }
    }

    // Auto-hide controls
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(3000)
            isControlsVisible = false
        }
    }

    // Progress update loop
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            bufferedPosition = exoPlayer.bufferedPosition
            if (exoPlayer.duration > 0L) {
                totalDuration = exoPlayer.duration
            }
            delay(500)
        }
    }

    // Apply Playback Speed
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // Apply Subtitles Toggle
    LaunchedEffect(subtitlesEnabled) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
    ) {
        // Player View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isControlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            isControlsVisible = true
                        }
                    }
            )
        }

        // Custom Overlay
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControlsOverlay(
                isPlaying = isPlaying,
                isLoading = isLoading,
                currentPositionMs = currentPosition,
                durationMs = totalDuration,
                bufferedPositionMs = bufferedPosition,
                isFullscreen = isLandscape,
                animeTitle = animeTitle,
                episodeTitle = episodeTitle,
                subtitleText = null, // ExoPlayer renders subtitles internally via PlayerView
                onPlayPauseClick = {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onSeekRelative = { delta ->
                    val newTime = (exoPlayer.currentPosition + delta).coerceIn(0, totalDuration)
                    exoPlayer.seekTo(newTime)
                    currentPosition = newTime
                },
                onSeekPosition = { pos ->
                    exoPlayer.seekTo(pos)
                    currentPosition = pos
                },
                onFullscreenToggle = {
                    val activity = context as? Activity
                    if (isLandscape) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                },
                onBackClick = onBackClick,
                onSettingsClick = onSettingsClick,
                onNextEpisodeClick = onNextEpisodeClick,
                onBackgroundClick = { isControlsVisible = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

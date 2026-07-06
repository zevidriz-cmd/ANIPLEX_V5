package com.aniplex.app.presentation.screens.player

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.SurfaceDark
import kotlinx.coroutines.delay

@Composable
fun TvExoVideoPlayer(
    state: PlayerScreenState,
    callbacks: PlayerCallbacks,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = state.exoPlayerRef

    val hasIntroNow = state.skipTimes.isDuringIntro(state.currentPositionMs)
    val hasOutroNow = state.skipTimes.isDuringOutro(state.currentPositionMs)

    var isControlsVisible by remember { mutableStateOf(true) }
    var controlsTimeoutKey by remember { mutableStateOf(0) }
    val playFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    val skipFocusRequester = remember { FocusRequester() }
    val timelineFocusRequester = remember { FocusRequester() }

    // Auto-hide controls after 5 seconds of inactivity
    LaunchedEffect(isControlsVisible, controlsTimeoutKey) {
        if (isControlsVisible) {
            delay(5000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(isControlsVisible, state.showSettings) {
        if (state.showSettings) {
            // Let the settings menu handle its focus
            return@LaunchedEffect
        }
        if (isControlsVisible) {
            try {
                if (hasIntroNow || hasOutroNow) {
                    skipFocusRequester.requestFocus()
                } else {
                    playFocusRequester.requestFocus()
                }
            } catch (_: Exception) {}
        } else {
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    var isRootFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .onFocusChanged { isRootFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                // === CHANGE 1: Context-Aware Back/Exit Key Hierarchy ===
                if (event.key == Key.Back && event.type == KeyEventType.KeyDown) {
                    when {
                        state.showSettings -> {
                            // Tier A: Settings open → close settings only
                            callbacks.onSettingsBackClick()
                        }
                        isControlsVisible -> {
                            // Tier B: Controls visible → hide controls only
                            isControlsVisible = false
                        }
                        else -> {
                            // Tier C: Nothing visible → exit player
                            callbacks.onBackClick()
                        }
                    }
                    return@onPreviewKeyEvent true // Always consume Back
                }

                // === Media hardware keys: always seek regardless of UI state ===
                if (event.type == KeyEventType.KeyDown && (
                    event.key == Key(android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) ||
                    event.key == Key(android.view.KeyEvent.KEYCODE_MEDIA_REWIND)
                )) {
                    exoPlayer?.let {
                        val newPos = if (event.key == Key(android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)) {
                            val dur = it.duration
                            if (dur > 0L) (it.currentPosition + 10000).coerceAtMost(dur) else it.currentPosition
                        } else {
                            (it.currentPosition - 10000).coerceAtLeast(0)
                        }
                        it.seekTo(newPos)
                        callbacks.onPositionChanged(newPos)
                        controlsTimeoutKey++
                        if (!isControlsVisible) isControlsVisible = true
                    }
                    return@onPreviewKeyEvent true
                }

                // Let settings panel handle its own key events
                if (state.showSettings) return@onPreviewKeyEvent false

                if (event.type == KeyEventType.KeyDown) {
                    controlsTimeoutKey++
                    val wasHidden = !isControlsVisible
                    if (wasHidden) {
                        if (isRootFocused) {
                            isControlsVisible = true
                            // Seek or click immediately on waking up
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    exoPlayer?.let {
                                        val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                        it.seekTo(newPos)
                                        callbacks.onPositionChanged(newPos)
                                    }
                                }
                                Key.DirectionRight -> {
                                    exoPlayer?.let {
                                        val duration = it.duration
                                        if (duration > 0L) {
                                            val newPos = (it.currentPosition + 10000).coerceAtMost(duration)
                                            it.seekTo(newPos)
                                            callbacks.onPositionChanged(newPos)
                                        }
                                    }
                                }
                                Key.DirectionCenter, Key.Spacebar, Key.Enter -> {
                                    if (hasIntroNow || hasOutroNow) {
                                        val targetPos = if (hasIntroNow) state.skipTimes.introEnd else state.skipTimes.outroEnd
                                        exoPlayer?.let {
                                            it.seekTo(targetPos)
                                            callbacks.onPositionChanged(targetPos)
                                        }
                                    } else {
                                        if (state.isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                                    }
                                }
                            }
                            true // Consume wakeup click
                        } else {
                            if (event.key == Key.DirectionCenter || event.key == Key.Spacebar || event.key == Key.Enter) {
                                false
                            } else {
                                isControlsVisible = true
                                false
                            }
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
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
                            val styleCompat = androidx.media3.ui.CaptionStyleCompat(
                                AndroidColor.WHITE,
                                AndroidColor.TRANSPARENT,
                                AndroidColor.TRANSPARENT,
                                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                AndroidColor.BLACK,
                                null
                            )
                            setStyle(styleCompat)
                            setFractionalTextSize(0.06f)
                        }
                    }
                },
                update = { view ->
                    view.resizeMode = when (state.screenFitMode) {
                        "Stretch" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        "Zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Centered Buffering/Loading Indicator overlay to bridge latency during timestamp skips
            if (state.isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = CrunchyrollOrange,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(48.dp)
                    )
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
                    isTv = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 96.dp, end = 24.dp)
                )
            }

            // Skip Intro / Skip Outro Floating Button Overlay for TV
            if (hasIntroNow || hasOutroNow) {
                var isSkipFocused by remember { mutableStateOf(false) }
                
                LaunchedEffect(hasIntroNow, hasOutroNow) {
                    try {
                        skipFocusRequester.requestFocus()
                    } catch (e: Exception) {}
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (showUpNext) 170.dp else 96.dp, end = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .wrapContentWidth()
                            .focusRequester(skipFocusRequester)
                            .onFocusChanged { isSkipFocused = it.isFocused }
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar) {
                                    if (event.type == KeyEventType.KeyUp) {
                                        exoPlayer?.let {
                                            val targetPos = if (hasIntroNow) state.skipTimes.introEnd else state.skipTimes.outroEnd
                                            it.seekTo(targetPos)
                                            callbacks.onPositionChanged(targetPos)
                                        }
                                    }
                                    true
                                } else false
                            }
                            .background(
                                color = if (isSkipFocused) CrunchyrollOrange else SurfaceDark,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 2.dp,
                                color = if (isSkipFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Dynamic Custom Controls Overlay
            AnimatedVisibility(
                visible = isControlsVisible && !state.showSettings,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    // Top Bar: Back & Title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .align(Alignment.TopStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // === CHANGE 2: Back Button — Box replaces IconButton ===
                        var isBackFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .onFocusChanged { isBackFocused = it.isFocused }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                                        if (event.type == KeyEventType.KeyUp) {
                                            callbacks.onBackClick()
                                        }
                                        true // Consume both KeyDown and KeyUp
                                    } else false
                                }
                                .background(if (isBackFocused) CrunchyrollOrange else Color.Transparent, CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = if (isBackFocused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${state.animeDetail?.name ?: ""} - Episode ${state.currentEpisode?.number ?: 1}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Center Row: Seek Back / Play-Pause / Seek Forward
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // === Rewind Button — Box replaces IconButton ===
                        var isRewindFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .onFocusChanged { isRewindFocused = it.isFocused }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    when (event.key) {
                                        Key.DirectionCenter, Key.Enter -> {
                                            if (event.type == KeyEventType.KeyUp) {
                                                exoPlayer?.let {
                                                    val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                                    it.seekTo(newPos)
                                                    callbacks.onPositionChanged(newPos)
                                                }
                                            }
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (event.type == KeyEventType.KeyDown) {
                                                try { timelineFocusRequester.requestFocus() } catch (_: Exception) {}
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .background(if (isRewindFocused) CrunchyrollOrange else SurfaceDark, CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = if (isRewindFocused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s", tint = Color.White)
                        }

                        // === Play/Pause Button — Box replaces IconButton ===
                        var isPlayFocused by remember { mutableStateOf(false) }
                        val playScale by animateFloatAsState(if (isPlayFocused) 1.1f else 1.0f)
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(playScale)
                                .onFocusChanged { isPlayFocused = it.isFocused }
                                .focusRequester(playFocusRequester)
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    when (event.key) {
                                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                            if (event.type == KeyEventType.KeyUp) {
                                                if (state.isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                                            }
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (event.type == KeyEventType.KeyDown) {
                                                try { timelineFocusRequester.requestFocus() } catch (_: Exception) {}
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .background(if (isPlayFocused) CrunchyrollOrange else SurfaceDark, CircleShape)
                                .border(
                                    width = 3.dp,
                                    color = if (isPlayFocused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // === Fast Forward Button — Box replaces IconButton ===
                        var isForwardFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .onFocusChanged { isForwardFocused = it.isFocused }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    when (event.key) {
                                        Key.DirectionCenter, Key.Enter -> {
                                            if (event.type == KeyEventType.KeyUp) {
                                                exoPlayer?.let {
                                                    val dur = it.duration
                                                    if (dur > 0L) {
                                                        val newPos = (it.currentPosition + 10000).coerceAtMost(dur)
                                                        it.seekTo(newPos)
                                                        callbacks.onPositionChanged(newPos)
                                                    }
                                                }
                                            }
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (event.type == KeyEventType.KeyDown) {
                                                try { timelineFocusRequester.requestFocus() } catch (_: Exception) {}
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .background(if (isForwardFocused) CrunchyrollOrange else SurfaceDark, CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = if (isForwardFocused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = "Forward 10s", tint = Color.White)
                        }
                    }

                    // Bottom Bar: Progress Seek Bar & Settings Button
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // === CHANGE 3: Focusable Timeline Scrubber ===
                            val progress = if (state.durationMs > 0) state.currentPositionMs.toFloat() / state.durationMs else 0f
                            var isTimelineFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(if (isTimelineFocused) 20.dp else 14.dp)
                                    .focusRequester(timelineFocusRequester)
                                    .onFocusChanged { isTimelineFocused = it.isFocused }
                                    .focusable()
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.DirectionLeft -> {
                                                    exoPlayer?.let {
                                                        val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                                        it.seekTo(newPos)
                                                        callbacks.onPositionChanged(newPos)
                                                        controlsTimeoutKey++
                                                    }
                                                    true
                                                }
                                                Key.DirectionRight -> {
                                                    exoPlayer?.let {
                                                        val dur = it.duration
                                                        if (dur > 0L) {
                                                            val newPos = (it.currentPosition + 10000).coerceAtMost(dur)
                                                            it.seekTo(newPos)
                                                            callbacks.onPositionChanged(newPos)
                                                            controlsTimeoutKey++
                                                        }
                                                    }
                                                    true
                                                }
                                                Key.DirectionUp -> {
                                                    try { playFocusRequester.requestFocus() } catch (_: Exception) {}
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .border(
                                        width = if (isTimelineFocused) 2.dp else 0.dp,
                                        color = if (isTimelineFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    ),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isTimelineFocused) 10.dp else 8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = CrunchyrollOrange,
                                    trackColor = Color.Gray
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
                            Spacer(modifier = Modifier.width(16.dp))

                            // === Settings Button — Box replaces IconButton ===
                            var isSettingsFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .onFocusChanged { isSettingsFocused = it.isFocused }
                                    .focusable()
                                    .onPreviewKeyEvent { event ->
                                        if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                                            if (event.type == KeyEventType.KeyUp) {
                                                callbacks.onSettingsClick()
                                            }
                                            true
                                        } else false
                                    }
                                    .background(if (isSettingsFocused) CrunchyrollOrange else Color.Transparent, CircleShape)
                                    .border(
                                        width = 2.5.dp,
                                        color = if (isSettingsFocused) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress Timers
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = formatTime(state.currentPositionMs),
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                            Text(
                                text = formatTime(state.durationMs),
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSecs = timeMs / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

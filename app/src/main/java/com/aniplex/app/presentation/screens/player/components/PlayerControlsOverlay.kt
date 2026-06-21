package com.aniplex.app.presentation.screens.player.components

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControlsOverlay(
    isPlaying: Boolean,
    isLoading: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long = 0L,
    isFullscreen: Boolean,
    animeTitle: String,
    episodeTitle: String,
    subtitleText: String?,
    onPlayPauseClick: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onSeekPosition: (Long) -> Unit,
    onFullscreenToggle: () -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNextEpisodeClick: (() -> Unit)?,
    onBackgroundClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures {
                    onBackgroundClick()
                }
            }
    ) {
        // Top Gradient (50% black to transparent over 80dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )

        // Bottom Gradient (50% black to transparent over 80dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                    )
                )
        )

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Back button (portrait) or Title Info (landscape)
            if (!isFullscreen) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 8.dp)
                        .weight(1f, fill = false)
                ) {
                    Text(
                        text = animeTitle,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = episodeTitle,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Side: Action Icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onNextEpisodeClick != null) {
                    IconButton(
                        onClick = onNextEpisodeClick,
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
                
                val context = LocalContext.current
                IconButton(
                    onClick = { 
                        Toast.makeText(context, "Casting coming soon", Toast.LENGTH_SHORT).show()
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
                    onClick = onSettingsClick,
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
                    onClick = onFullscreenToggle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Toggle Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Center Controls or Buffering Spinner
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFFF5A623),
                strokeWidth = 3.dp,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onSeekRelative(-10000L) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Rewind 10s",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayPauseClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                IconButton(
                    onClick = { onSeekRelative(10000L) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Bottom Bar (pins to bottom edge)
        if (!isFullscreen) {
            // Portrait Bottom Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    val bufferedPercent = if (durationMs > 0) (bufferedPositionMs.toFloat() / durationMs.toFloat()) else 0f
                    val playPercent = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .padding(horizontal = 6.dp)
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
                            value = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()) else 0f,
                            onValueChange = { percent ->
                                val newTime = (percent * durationMs).toLong()
                                onSeekPosition(newTime)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFF5A623),
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(0xFFF5A623), CircleShape)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = formatTime(durationMs),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                
                // Centered Subtitle Line
                if (!subtitleText.isNullOrEmpty()) {
                    Text(
                        text = subtitleText,
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        } else {
            // Landscape Bottom Bar (Single row inline subtitles)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(currentPositionMs),
                    color = Color.White,
                    fontSize = 12.sp
                )
                
                // Subtitles centered in remaining space inline
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!subtitleText.isNullOrEmpty()) {
                        Text(
                            text = subtitleText,
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val bufferedPercent = if (durationMs > 0) (bufferedPositionMs.toFloat() / durationMs.toFloat()) else 0f
                val playPercent = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .padding(horizontal = 6.dp)
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
                        value = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()) else 0f,
                        onValueChange = { percent ->
                            val newTime = (percent * durationMs).toLong()
                            onSeekPosition(newTime)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFF5A623),
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFFF5A623), CircleShape)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Text(
                    text = formatTime(durationMs),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%d:%02d", minutes, seconds)
}

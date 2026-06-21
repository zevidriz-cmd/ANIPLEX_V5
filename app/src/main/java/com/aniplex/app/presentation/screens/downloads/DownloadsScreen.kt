package com.aniplex.app.presentation.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aniplex.app.data.download.DownloadManager
import com.aniplex.app.data.download.DownloadStatus
import com.aniplex.app.data.download.DownloadTask
import com.aniplex.app.theme.BackgroundVoid
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.ErrorColor
import com.aniplex.app.theme.SuccessColor
import com.aniplex.app.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onPlayClick: (episodeId: String, animeId: String, animeTitle: String, episodeNumber: Int, category: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by DownloadManager.downloads.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Downloads",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundVoid)
            )
        },
        containerColor = BackgroundVoid
    ) { innerPadding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(BackgroundVoid),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DownloadForOffline,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Downloads Found",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Click the download icon on any episode to start downloading offline.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(BackgroundVoid),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(downloads, key = { it.episodeId }) { task ->
                    DownloadItemRow(task = task, onPlayClick = onPlayClick)
                }
            }
        }
    }
}

@Composable
fun DownloadItemRow(
    task: DownloadTask,
    onPlayClick: (episodeId: String, animeId: String, animeTitle: String, episodeNumber: Int, category: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status by task.status.collectAsStateWithLifecycle()
    val progress by task.progress.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .clickable(enabled = status == DownloadStatus.COMPLETED) {
                onPlayClick(task.episodeId, task.animeId, task.animeTitle, task.episodeNumber, "sub")
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(110.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(4.dp))
        ) {
            AsyncImage(
                model = task.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (status == DownloadStatus.COMPLETED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Offline",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.animeTitle,
                color = CrunchyrollOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "E${task.episodeNumber} - ${task.episodeTitle}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            when (status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = CrunchyrollOrange,
                        trackColor = Color.DarkGray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
                DownloadStatus.PAUSED -> {
                    Text(
                        text = "Paused (${(progress * 100).toInt()}%)",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                DownloadStatus.COMPLETED -> {
                    Text(
                        text = "Completed • Offline Playback",
                        color = SuccessColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                DownloadStatus.FAILED -> {
                    Text(
                        text = "Download Failed",
                        color = ErrorColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Action Buttons
        Row {
            when (status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                    IconButton(onClick = { DownloadManager.pauseDownload(context, task.episodeId) }) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                DownloadStatus.PAUSED -> {
                    IconButton(onClick = { DownloadManager.resumeDownload(context, task.episodeId) }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = CrunchyrollOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                else -> {}
            }

            IconButton(onClick = { 
                DownloadManager.cancelDownload(context, task.episodeId) 
            }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

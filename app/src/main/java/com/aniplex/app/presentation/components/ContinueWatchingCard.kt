package com.aniplex.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aniplex.app.domain.model.HistoryItem
import com.valentinilk.shimmer.shimmer
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.SurfaceDark
import com.aniplex.app.theme.TextSecondary

@Composable
fun ContinueWatchingCard(
    item: HistoryItem,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRemoveFromHistory: ((String) -> Unit)? = null,
    onMarkAsFinished: ((String, String, String) -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var isImageLoading by remember { mutableStateOf(true) }
    var isImageError by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .width(240.dp)
            .clickable { onClick(item.animeId) }
            .border(
                width = 1.dp,
                color = Color(0xFF2C2945),
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Video-style physical frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color(0xFF141221))
            ) {
                // Main poster/banner Image
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.animeTitle,
                    contentScale = ContentScale.Crop,
                    onLoading = { isImageLoading = true; isImageError = false },
                    onSuccess = { isImageLoading = false; isImageError = false },
                    onError = { isImageLoading = false; isImageError = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isImageLoading) Modifier.shimmer() else Modifier)
                )
                if (isImageError) {
                    ErrorPlaceholder(modifier = Modifier.fillMaxSize())
                }

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 150f
                            )
                        )
                )

                // Tiny active Play Button overlay in the corner
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume Movie",
                        tint = CrunchyrollOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Ep Indicator Badge in the bottom-start of the image
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, Color(0xFFC5BAFF).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "EP. ${item.episodeNumber}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC5BAFF)
                    )
                }

                // PROGRESS BAR overlaying the exact very bottom edge of image
                val progressFraction = if (item.totalDuration > 0) {
                    item.progressPosition.toFloat() / item.totalDuration.toFloat()
                } else {
                    0f
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .background(CrunchyrollOrange)
                    )
                }
            }

            // Title and last episode details in bottom container
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.animeTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))

                    val subDescription = if (item.episodeTitle.isNotBlank()) {
                        item.episodeTitle
                    } else {
                        "Tap to resume watching"
                    }
                    Text(
                        text = subDescription,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onRemoveFromHistory != null || onMarkAsFinished != null) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Actions",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(SurfaceDark)
                        ) {
                            if (onMarkAsFinished != null) {
                                DropdownMenuItem(
                                    text = { Text("Mark as Finished", color = Color.White, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Mark finished",
                                            tint = CrunchyrollOrange,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onMarkAsFinished(item.animeId, item.animeTitle, item.poster)
                                    }
                                )
                            }
                            if (onRemoveFromHistory != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove from History", color = Color.Red, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete from history",
                                            tint = Color.Red,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onRemoveFromHistory(item.animeId)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

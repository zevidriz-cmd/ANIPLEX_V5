package com.aniplex.app.presentation.screens.detail

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import com.aniplex.app.data.download.DownloadManager
import com.aniplex.app.data.download.DownloadStatus
import com.aniplex.app.data.local.preferences.PreferenceManager
import coil.compose.AsyncImage
import com.aniplex.app.domain.model.AnimeDetail
import com.aniplex.app.domain.model.Character
import com.aniplex.app.domain.model.Episode
import com.aniplex.app.domain.model.HistoryItem
import com.aniplex.app.domain.model.Season
import com.aniplex.app.presentation.components.AnimeCard
import com.aniplex.app.theme.BackgroundVoid
import com.aniplex.app.theme.BrandGradient
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.GoldStar
import com.aniplex.app.theme.NetflixRed
import com.aniplex.app.theme.SurfaceDark
import com.aniplex.app.theme.SurfaceDarkVariant
import com.aniplex.app.theme.TextSecondary

@Composable
fun DetailScreen(
    animeId: String,
    onBackClick: () -> Unit,
    onPlayClick: (String, String, String, Int, String) -> Unit,
    onRecommendationClick: (String) -> Unit,
    onSeasonSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val episodesState by viewModel.episodesState.collectAsStateWithLifecycle()
    val charactersState by viewModel.charactersState.collectAsStateWithLifecycle()
    val isWatchlisted by viewModel.isWatchlisted.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val userRating by viewModel.userRating.collectAsStateWithLifecycle()
    val seasonsState by viewModel.seasonsState.collectAsStateWithLifecycle()
    val resolvedAnikotoId by viewModel.resolvedAnikotoId.collectAsStateWithLifecycle()
    val isResolvingSeason by viewModel.isResolvingSeason.collectAsStateWithLifecycle()
    val selectedVersion by viewModel.selectedVersion.collectAsStateWithLifecycle()
    val hasMultipleVersions by viewModel.hasMultipleVersions.collectAsStateWithLifecycle()

    // Load the selected season smoothly by popping the current screen and pushing a new one
    LaunchedEffect(resolvedAnikotoId) {
        resolvedAnikotoId?.let { id ->
            onSeasonSelect(id)
            viewModel.clearResolvedId()
        }
    }

    val resolutionError by viewModel.resolutionError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(resolutionError) {
        resolutionError?.let { errorMsg ->
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            viewModel.clearResolutionError()
        }
    }

    LaunchedEffect(animeId) {
        viewModel.loadAnimeData(animeId)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundVoid)
    ) {
        when (val state = detailState) {
            is DetailState.Loading -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        color = CrunchyrollOrange,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(top = 48.dp, start = 16.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }
            is DetailState.Success -> {
                DetailContent(
                    animeDetail = state.data,
                    episodesState = episodesState,
                    charactersState = charactersState,
                    isWatchlisted = isWatchlisted,
                    watchHistory = watchHistory,
                    userRating = userRating,
                    seasonsState = seasonsState,
                    onBackClick = onBackClick,
                    onPlayClick = onPlayClick,
                    onRecommendationClick = onRecommendationClick,
                    onWatchlistToggle = { viewModel.toggleWatchlist(state.data) },
                    onRatingSelected = { rating -> viewModel.setRating(animeId, rating) },
                    onSeasonSelected = { malId -> viewModel.resolveMALAndNavigate(malId) },
                    onSeasonRetry = { viewModel.retryLoadSeasons() },
                    onMarkAsWatched = { viewModel.markAsWatched(state.data.id, state.data.name, state.data.poster) },
                    onRemoveFromHistory = { viewModel.removeFromHistory(state.data.id) },
                    selectedVersion = selectedVersion,
                    onVersionChange = { viewModel.setSelectedVersion(it) },
                    hasMultipleVersions = hasMultipleVersions,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is DetailState.Error -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Error Loading Details", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = state.message, color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadAnimeData(animeId, forceRefresh = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(top = 48.dp, start = 16.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        if (isResolvingSeason) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkVariant),
                    modifier = Modifier
                        .padding(32.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = CrunchyrollOrange,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "SWITCHING SEASON...",
                            color = CrunchyrollOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Retrieving and preparing requested season details dynamically.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailContent(
    animeDetail: AnimeDetail,
    episodesState: DetailState<List<Episode>>,
    charactersState: DetailState<List<Character>>,
    isWatchlisted: Boolean,
    watchHistory: HistoryItem?,
    userRating: Int,
    seasonsState: DetailState<List<Season>>,
    onBackClick: () -> Unit,
    onPlayClick: (String, String, String, Int, String) -> Unit,
    onRecommendationClick: (String) -> Unit,
    onWatchlistToggle: () -> Unit,
    onRatingSelected: (Int) -> Unit,
    onSeasonSelected: (String) -> Unit,
    onSeasonRetry: () -> Unit,
    onMarkAsWatched: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    selectedVersion: String,
    onVersionChange: (String) -> Unit,
    hasMultipleVersions: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Episodes", "Characters", "Related")
    var selectedAudioType by remember { mutableStateOf("SUB") }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val downloads by DownloadManager.downloads.collectAsStateWithLifecycle()
    
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCellularWarningDialog by remember { mutableStateOf(false) }
    var showCellularDisabledDialog by remember { mutableStateOf(false) }
    var pendingDownloadEpisode by remember { mutableStateOf<Episode?>(null) }
    
    val triggerDownload: (Episode) -> Unit = { episode: Episode ->
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        
        val prefManager = PreferenceManager(context)
        val downloadCellular = prefManager.downloadOverCellular
        
        if (isCellular && !isWifi) {
            if (downloadCellular) {
                pendingDownloadEpisode = episode
                showCellularWarningDialog = true
            } else {
                showCellularDisabledDialog = true
            }
        } else {
            DownloadManager.startDownload(
                context = context,
                episodeId = episode.id,
                animeId = animeDetail.id,
                animeTitle = animeDetail.name,
                episodeNumber = episode.number,
                episodeTitle = episode.title.ifBlank { "Episode ${episode.number}" },
                posterUrl = animeDetail.poster
            )
            Toast.makeText(context, "Download started for Episode ${episode.number}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 80.dp)
            ) {
                // 1. Hero Poster
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                        .graphicsLayer {
                            translationY = scrollState.value * 0.4f
                            alpha = 1f - (scrollState.value.toFloat() / 1200f).coerceIn(0f, 1f)
                        }
                ) {
                    AsyncImage(
                        model = animeDetail.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient fade to background at the bottom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.3f),
                                        Color.Transparent,
                                        Color.Transparent,
                                        BackgroundVoid
                                    )
                                )
                            )
                    )

                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(top = 48.dp, start = 16.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 16.dp)
                    ) {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(SurfaceDark)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mark as Finished", color = Color.White) },
                                onClick = {
                                    showMoreMenu = false
                                    onMarkAsWatched()
                                    Toast.makeText(context, "Marked as Watched", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = CrunchyrollOrange)
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = Color.White,
                                    leadingIconColor = CrunchyrollOrange
                                )
                            )

                            if (watchHistory != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove Watch Progress", color = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        onRemoveFromHistory()
                                        Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.LightGray)
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = Color.White,
                                        leadingIconColor = Color.LightGray
                                    )
                                )
                            }

                            DropdownMenuItem(
                                text = { Text(if (isWatchlisted) "Remove from Watchlist" else "Add to Watchlist", color = Color.White) },
                                onClick = {
                                    showMoreMenu = false
                                    onWatchlistToggle()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isWatchlisted) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = null,
                                        tint = CrunchyrollOrange
                                    )
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = Color.White,
                                    leadingIconColor = CrunchyrollOrange
                                )
                            )

                            DropdownMenuItem(
                                text = { Text("Share Anime", color = Color.White) },
                                onClick = {
                                    showMoreMenu = false
                                    try {
                                        val sendIntent = android.content.Intent().apply {
                                            action = android.content.Intent.ACTION_SEND
                                            putExtra(
                                                android.content.Intent.EXTRA_TEXT,
                                                "Check out ${animeDetail.name} on Aniplex! Here's the details: ${animeDetail.description.take(120)}..."
                                            )
                                            type = "text/plain"
                                        }
                                        val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                                        context.startActivity(shareIntent)
                                    } catch (e: Exception) {
                                        // Squelch
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = Color.White,
                                    leadingIconColor = Color.White
                                )
                            )

                            DropdownMenuItem(
                                text = { Text("Copy Title", color = Color.White) },
                                onClick = {
                                    showMoreMenu = false
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Anime Title", animeDetail.name)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied title to clipboard", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        // Squelch
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White)
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = Color.White,
                                    leadingIconColor = Color.White
                                )
                            )
                        }
                    }

                    // Cast button removed as per Stitch specs (hide Cast feature)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-40).dp)
                ) {
                    // Title Logo / Text
                    Text(
                        text = animeDetail.name,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Metadata
                    val metaText = "16+ • Dub | Sub • ${if(animeDetail.genres.isNotEmpty()) animeDetail.genres.take(2).joinToString(", ") else "Shonen"}"
                    Text(
                        text = metaText,
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Rating
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Icon(Icons.Default.StarHalf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (animeDetail.rating.isNotBlank()) "Average: ${animeDetail.rating}" else "No Rating",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons (Play, Bookmark, Download)
                    val episodes = (episodesState as? DetailState.Success)?.data ?: emptyList()
                    val hasHistory = watchHistory != null
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (hasHistory && watchHistory != null) {
                                    onPlayClick(watchHistory.episodeId, animeDetail.id, animeDetail.name, watchHistory.episodeNumber, selectedAudioType.lowercase())
                                } else if (episodes.isNotEmpty()) {
                                    val firstEpisode = episodes.first()
                                    onPlayClick(firstEpisode.id, animeDetail.id, animeDetail.name, firstEpisode.number, selectedAudioType.lowercase())
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange),
                            enabled = episodes.isNotEmpty()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = buildString {
                                        if (hasHistory) {
                                            append("RESUME E${watchHistory?.episodeNumber}")
                                        } else {
                                            append("START WATCHING E1")
                                        }
                                        if (hasMultipleVersions) {
                                            if (selectedVersion == "uncensored") {
                                                append(" (UNCUT)")
                                            } else {
                                                append(" (TV)")
                                            }
                                        }
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onWatchlistToggle,
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(SurfaceDarkVariant)
                        ) {
                            Icon(
                                imageVector = if (isWatchlisted) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Watchlist",
                                tint = if (isWatchlisted) CrunchyrollOrange else Color.White
                            )
                        }

                        val targetEpisode = if (hasHistory && watchHistory != null) {
                            episodes.find { it.id == watchHistory.episodeId } ?: episodes.firstOrNull()
                        } else {
                            episodes.firstOrNull()
                        }

                        val mainDownloadTask = downloads.find { it.episodeId == targetEpisode?.id }
                        val mainStatus = mainDownloadTask?.status?.collectAsStateWithLifecycle()?.value

                        IconButton(
                            onClick = {
                                targetEpisode?.let { triggerDownload(it) }
                            },
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(SurfaceDarkVariant),
                            enabled = targetEpisode != null
                        ) {
                            when (mainStatus) {
                                DownloadStatus.QUEUED -> {
                                    CircularProgressIndicator(color = CrunchyrollOrange, modifier = Modifier.size(24.dp))
                                }
                                DownloadStatus.DOWNLOADING -> {
                                    val progress = mainDownloadTask?.progress?.collectAsStateWithLifecycle()?.value ?: 0f
                                    CircularProgressIndicator(
                                        progress = { progress },
                                        color = CrunchyrollOrange,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                DownloadStatus.COMPLETED -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Downloaded",
                                        tint = CrunchyrollOrange
                                    )
                                }
                                DownloadStatus.FAILED -> {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download Failed (Click to retry)",
                                        tint = Color.Red
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Description
                    var isExpanded by remember { mutableStateOf(false) }
                    Text(
                        text = animeDetail.description,
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = if (isExpanded) "Less Details" else "More Details",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrunchyrollOrange,
                        modifier = Modifier
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 8.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Continue Watching Progress Banner
        if (watchHistory != null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPlayClick(
                                watchHistory.episodeId,
                                animeDetail.id,
                                animeDetail.name,
                                watchHistory.episodeNumber,
                                selectedAudioType.lowercase()
                            )
                        }
                        .border(1.dp, CrunchyrollOrange.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CrunchyrollOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Continue from Episode ${watchHistory.episodeNumber}",
                                fontSize = 11.sp,
                                color = CrunchyrollOrange,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (watchHistory.episodeTitle.isNotBlank()) watchHistory.episodeTitle else "Resume playback",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val progress = if (watchHistory.totalDuration > 0) {
                                watchHistory.progressPosition.toFloat() / watchHistory.totalDuration.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(CircleShape),
                                color = CrunchyrollOrange,
                                trackColor = Color.DarkGray
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        MetadataGrid(animeDetail = animeDetail)

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Segmented Tabbed Section (Episodes, Characters, Related)
        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceDark,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CrunchyrollOrange
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Add Season Selector Dropdown here if we are on Episodes tab
            if (selectedTab == 0) {
                when (seasonsState) {
                    is DetailState.Success -> {
                        val seasons = seasonsState.data
                        if (seasons.isNotEmpty()) {
                            var expanded by remember { mutableStateOf(false) }
                            // Current selected season is the one that matches this anime's title or the first one if we can't figure it out
                            val currentSeason = seasons.find { it.malId == animeDetail.malId } ?: seasons.firstOrNull()
                            
                            val groupedSeasons = remember(seasons) {
                                val result = mutableListOf<SeasonGroup>()
                                val processed = mutableSetOf<String>()
                                
                                for (season in seasons) {
                                    if (season.malId in processed) continue
                                    
                                    val alts = seasons.filter { other ->
                                        other.malId != season.malId && 
                                        other.malId !in processed && 
                                        run {
                                            val t1 = season.title.lowercase().replace(":", "").replace("-", " ")
                                            val t2 = other.title.lowercase().replace(":", "").replace("-", " ")
                                            
                                            val keywords = listOf(
                                                "mugen train", "mugen ressha",
                                                "entertainment district", "yuukaku",
                                                "swordsmith village", "katanakaji",
                                                "hashira training", "hashira geiko",
                                                "solo leveling", "my hero academia",
                                                "shingeki no kyojin", "attack on titan",
                                                "recap", "special"
                                            )
                                            
                                            var match = false
                                            for (kw in keywords) {
                                                if (t1.contains(kw) && t2.contains(kw)) {
                                                    match = true
                                                    break
                                                }
                                            }
                                            
                                            if (!match) {
                                                if (season.seasonNumber > 0 && other.seasonNumber > 0 && season.seasonNumber != other.seasonNumber) {
                                                    false
                                                } else {
                                                    val words1 = t1.split(" ").filter { it.length >= 5 && it != "season" && it != "movie" && it != "series" && it != "special" && it != "edition" && it != "version" }
                                                    val words2 = t2.split(" ").filter { it.length >= 5 && it != "season" && it != "movie" && it != "series" && it != "special" && it != "edition" && it != "version" }
                                                    val common = words1.intersect(words2)
                                                    common.isNotEmpty()
                                                }
                                            } else {
                                                if (season.seasonNumber > 0 && other.seasonNumber > 0 && season.seasonNumber != other.seasonNumber) {
                                                    false
                                                } else {
                                                    true
                                                }
                                            }
                                        }
                                    }
                                    
                                    result.add(SeasonGroup(primary = season, alternatives = alts))
                                    processed.add(season.malId)
                                    alts.forEach { processed.add(it.malId) }
                                }
                                result
                            }

                            
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .animateContentSize(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = SurfaceDarkVariant
                                )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expanded = !expanded }
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(CrunchyrollOrange)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "SELECT SEASON",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = currentSeason?.title?.takeIf { it.isNotBlank() } ?: animeDetail.name,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { onSeasonRetry() },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Replay,
                                                contentDescription = "Sync Seasons",
                                                tint = CrunchyrollOrange,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Toggle Seasons List",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .size(28.dp)
                                                .graphicsLayer {
                                                    rotationZ = if (expanded) 180f else 0f
                                                }
                                        )
                                    }
                                    
                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                            groupedSeasons.forEachIndexed { groupIdx, group ->
                                                val primary = group.primary
                                                val isPrimaryCurrent = primary.malId == animeDetail.malId
                                                val isAnyInGroupCurrent = isPrimaryCurrent || group.alternatives.any { it.malId == animeDetail.malId }
                                                val watchNum = (groupIdx + 1).toString().let { if (it.length == 1) "0$it" else it }
                                                
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                if (isPrimaryCurrent) Color.White.copy(alpha = 0.04f) else Color.Transparent
                                                            )
                                                            .clickable {
                                                                expanded = false
                                                                if (!isPrimaryCurrent) {
                                                                    onSeasonSelected(primary.malId)
                                                                }
                                                            }
                                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                                    ) {
                                                        Text(
                                                            text = "#$watchNum",
                                                            color = if (isPrimaryCurrent) CrunchyrollOrange else if (isAnyInGroupCurrent) CrunchyrollOrange.copy(alpha = 0.7f) else TextSecondary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .width(36.dp)
                                                                .background(
                                                                    if (isPrimaryCurrent) CrunchyrollOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                                                    RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(vertical = 4.dp, horizontal = 4.dp),
                                                            textAlign = TextAlign.Center
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = primary.title.takeIf { it.isNotBlank() } ?: (animeDetail.name + if (primary.seasonNumber > 1) " Season ${primary.seasonNumber}" else ""),
                                                                color = if (isPrimaryCurrent) CrunchyrollOrange else Color.White,
                                                                fontSize = 14.sp,
                                                                fontWeight = if (isPrimaryCurrent) FontWeight.Bold else FontWeight.Normal,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            
                                                            val primaryTitleLower = primary.title.lowercase()
                                                            val isPrimaryMovie = primary.episodes == 1 || primaryTitleLower.contains("movie") || primaryTitleLower.contains("film") || primaryTitleLower.contains("theatrical")
                                                            val isPrimaryOva = primaryTitleLower.contains("ova") || primaryTitleLower.contains("o.v.a")
                                                            val isPrimarySpecial = primaryTitleLower.contains("special") || primaryTitleLower.contains("recap") || primaryTitleLower.contains("summary")
                                                            val isPrimaryOna = primaryTitleLower.contains("ona") || primaryTitleLower.contains("spin-off")

                                                            val primaryBadgeText = when {
                                                                isPrimaryMovie -> "MOVIE"
                                                                isPrimaryOva -> "OVA"
                                                                isPrimarySpecial -> "SPECIAL"
                                                                isPrimaryOna -> "ONA"
                                                                else -> "SERIES"
                                                            }
                                                            val primaryBadgeColor = if (isPrimaryMovie) CrunchyrollOrange else if (isPrimarySpecial) Color(0xFFFFD54F) else TextSecondary
                                                            val primaryBadgeBg = if (isPrimaryMovie) CrunchyrollOrange.copy(alpha = 0.12f) else if (isPrimarySpecial) Color(0xFFFFD54F).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f)
                                                            val primaryBadgeBorder = if (isPrimaryMovie) CrunchyrollOrange.copy(alpha = 0.25f) else if (isPrimarySpecial) Color(0xFFFFD54F).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f)
                                                            val primaryBadgeIcon = if (isPrimaryMovie || isPrimaryOva) Icons.Default.Movie else if (isPrimarySpecial) Icons.Default.Star else Icons.Default.Tv

                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(top = 4.dp)
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier
                                                                        .background(primaryBadgeBg, RoundedCornerShape(4.dp))
                                                                        .border(1.dp, primaryBadgeBorder, RoundedCornerShape(4.dp))
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = primaryBadgeIcon,
                                                                        contentDescription = null,
                                                                        tint = primaryBadgeColor,
                                                                        modifier = Modifier.size(10.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(
                                                                        text = primaryBadgeText,
                                                                        color = primaryBadgeColor,
                                                                        fontSize = 8.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }

                                                                if (!isPrimaryMovie && primary.episodes > 0) {
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Text(
                                                                        text = "${primary.episodes} EPISODES",
                                                                        color = TextSecondary,
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.Medium
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        if (isPrimaryCurrent) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(CrunchyrollOrange.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                                    .border(1.dp, CrunchyrollOrange.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "ACTIVE",
                                                                    color = CrunchyrollOrange,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                    
                                                    if (group.alternatives.isNotEmpty()) {
                                                        group.alternatives.forEach { alt ->
                                                            val isAltCurrent = alt.malId == animeDetail.malId
                                                            
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(
                                                                        if (isAltCurrent) Color.White.copy(alpha = 0.04f) else Color.Transparent
                                                                    )
                                                                    .clickable {
                                                                        expanded = false
                                                                        if (!isAltCurrent) {
                                                                            onSeasonSelected(alt.malId)
                                                                        }
                                                                    }
                                                                    .padding(start = 36.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                                                            ) {
                                                                Text(
                                                                    text = "ALT",
                                                                    color = if (isAltCurrent) CrunchyrollOrange else TextSecondary,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier
                                                                        .width(36.dp)
                                                                        .background(
                                                                            if (isAltCurrent) CrunchyrollOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f),
                                                                            RoundedCornerShape(4.dp)
                                                                        )
                                                                        .padding(vertical = 3.dp),
                                                                    textAlign = TextAlign.Center
                                                                )
                                                                Spacer(modifier = Modifier.width(12.dp))
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        text = alt.title,
                                                                        color = if (isAltCurrent) CrunchyrollOrange else Color.White.copy(alpha = 0.85f),
                                                                        fontSize = 13.sp,
                                                                        fontWeight = if (isAltCurrent) FontWeight.Bold else FontWeight.Normal,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                    
                                                                    val altTitleLower = alt.title.lowercase()
                                                                    val isAltMovie = alt.episodes == 1 || altTitleLower.contains("movie") || altTitleLower.contains("film") || altTitleLower.contains("theatrical")
                                                                    val isAltOva = altTitleLower.contains("ova") || altTitleLower.contains("o.v.a")
                                                                    val isAltSpecial = altTitleLower.contains("special") || altTitleLower.contains("recap") || altTitleLower.contains("summary")
                                                                    val isAltOna = altTitleLower.contains("ona") || altTitleLower.contains("spin-off")

                                                                    val altBadgeText = when {
                                                                        isAltMovie -> "MOVIE CUT"
                                                                        isAltOva -> "OVA"
                                                                        isAltSpecial -> "RECAP / SPECIAL"
                                                                        isAltOna -> "ONA"
                                                                        else -> "TV VERSION"
                                                                    }
                                                                    val altBadgeColor = if (isAltMovie) CrunchyrollOrange else if (isAltSpecial) Color(0xFFFFD54F) else TextSecondary.copy(alpha = 0.8f)
                                                                    val altBadgeBg = if (isAltMovie) CrunchyrollOrange.copy(alpha = 0.1f) else if (isAltSpecial) Color(0xFFFFD54F).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f)
                                                                    val altBadgeBorder = if (isAltMovie) CrunchyrollOrange.copy(alpha = 0.2f) else if (isAltSpecial) Color(0xFFFFD54F).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.1f)
                                                                    val altBadgeIcon = if (isAltMovie || isAltOva) Icons.Default.Movie else if (isAltSpecial) Icons.Default.Star else Icons.Default.Tv

                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        modifier = Modifier.padding(top = 2.dp)
                                                                    ) {
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            modifier = Modifier
                                                                                .background(altBadgeBg, RoundedCornerShape(4.dp))
                                                                                .border(1.dp, altBadgeBorder, RoundedCornerShape(4.dp))
                                                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                                                        ) {
                                                                            Icon(
                                                                                imageVector = altBadgeIcon,
                                                                                contentDescription = null,
                                                                                tint = altBadgeColor,
                                                                                modifier = Modifier.size(9.dp)
                                                                            )
                                                                            Spacer(modifier = Modifier.width(3.dp))
                                                                            Text(
                                                                                text = altBadgeText,
                                                                                color = altBadgeColor,
                                                                                fontSize = 7.5.sp,
                                                                                fontWeight = FontWeight.Bold
                                                                            )
                                                                        }

                                                                        if (!isAltMovie && alt.episodes > 0) {
                                                                            Spacer(modifier = Modifier.width(6.dp))
                                                                            Text(
                                                                                text = "${alt.episodes} EP",
                                                                                color = TextSecondary.copy(alpha = 0.8f),
                                                                                fontSize = 8.5.sp,
                                                                                fontWeight = FontWeight.Medium
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                if (isAltCurrent) {
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .background(CrunchyrollOrange.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                                            .border(1.dp, CrunchyrollOrange.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = "ACTIVE",
                                                                            color = CrunchyrollOrange,
                                                                            fontSize = 9.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                                if (groupIdx < groupedSeasons.size - 1) {
                                                    HorizontalDivider(color = Color.White.copy(alpha = 0.04f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Empty Success State: gracefully show a subtle informative card
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth()
                                    .background(SurfaceDarkVariant, RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(CrunchyrollOrange.copy(alpha = 0.5f))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Single Season Release",
                                        color = TextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onSeasonRetry() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay,
                                            contentDescription = "Sync Seasons",
                                            tint = CrunchyrollOrange,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }
                    }
                    is DetailState.Loading -> {
                        // Premium beautiful shimmering/indicator card
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDarkVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = CrunchyrollOrange,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "SYNCING SEASONS",
                                        color = CrunchyrollOrange,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Connecting to database...",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                    is DetailState.Error -> {
                        // Polished Modern Error State with large retry CTA card
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .border(1.dp, Color.Red.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDarkVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay,
                                        contentDescription = "Syncing Error",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Seasons Offline",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Failed to sync related seasons. Server connection might be slow or rate-limited.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { onSeasonRetry() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CrunchyrollOrange,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay,
                                            contentDescription = "Retry",
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Retry Syncing",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            when (selectedTab) {
                0 -> EpisodesTabContent(
                    animeTitle = animeDetail.name,
                    poster = animeDetail.poster,
                    duration = animeDetail.duration,
                    episodesState = episodesState,
                    selectedAudioType = selectedAudioType,
                    onAudioTypeChange = { selectedAudioType = it },
                    onPlayClick = { epId, title, epNum, cat ->
                        onPlayClick(epId, animeDetail.id, title, epNum, cat)
                    },
                    downloads = downloads,
                    triggerDownload = triggerDownload,
                    watchHistory = watchHistory,
                    selectedVersion = selectedVersion,
                    onVersionChange = onVersionChange,
                    hasMultipleVersions = hasMultipleVersions
                )
                1 -> CharactersTabContent(charactersState = charactersState)
                2 -> RecommendationsTabContent(
                    recommendations = animeDetail.recommendations,
                    onAnimeClick = onRecommendationClick
                )
            }
            }
        }
    } // closes Box(weight=1f)
        
    // Sticky "Start Watching E1" Button
    val episodes = (episodesState as? DetailState.Success)?.data ?: emptyList()
        val hasHistory = watchHistory != null
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    if (hasHistory && watchHistory != null) {
                        onPlayClick(watchHistory.episodeId, animeDetail.id, animeDetail.name, watchHistory.episodeNumber, selectedAudioType.lowercase())
                    } else if (episodes.isNotEmpty()) {
                        val firstEpisode = episodes.first()
                        onPlayClick(firstEpisode.id, animeDetail.id, animeDetail.name, firstEpisode.number, selectedAudioType.lowercase())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange),
                enabled = episodes.isNotEmpty()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasHistory) "Resume E${watchHistory?.episodeNumber}" else "Start Watching E1",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 15.sp
                    )
                }
            }
        }

        if (showCellularWarningDialog && pendingDownloadEpisode != null) {
            val episode = pendingDownloadEpisode!!
            AlertDialog(
                onDismissRequest = { showCellularWarningDialog = false },
                title = { Text("Cellular Data Warning", color = Color.White) },
                text = { Text("You are connected to cellular data. Downloading over cellular may consume your data plan. Do you want to proceed?", color = Color.LightGray) },
                confirmButton = {
                    Button(
                        onClick = {
                            showCellularWarningDialog = false
                            DownloadManager.startDownload(
                                context = context,
                                episodeId = episode.id,
                                animeId = animeDetail.id,
                                animeTitle = animeDetail.name,
                                episodeNumber = episode.number,
                                episodeTitle = episode.title.ifBlank { "Episode ${episode.number}" },
                                posterUrl = animeDetail.poster
                            )
                            Toast.makeText(context, "Download started for Episode ${episode.number}", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
                    ) {
                        Text("Download", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCellularWarningDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = SurfaceDark
            )
        }

        if (showCellularDisabledDialog) {
            AlertDialog(
                onDismissRequest = { showCellularDisabledDialog = false },
                title = { Text("Cellular Download Disabled", color = Color.White) },
                text = { Text("You are on cellular data, but downloading over cellular is disabled in settings. Connect to Wi-Fi or enable it in Settings under Profile.", color = Color.LightGray) },
                confirmButton = {
                    Button(
                        onClick = { showCellularDisabledDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
                    ) {
                        Text("OK", color = Color.Black)
                    }
                },
                containerColor = SurfaceDark
            )
        }
    }
}

@Composable
fun MetadataGrid(animeDetail: AnimeDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(SurfaceDark.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Status", color = Color.Gray, fontSize = 11.sp)
                Text(animeDetail.status, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Aired", color = Color.Gray, fontSize = 11.sp)
                Text(animeDetail.aired, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Premiere", color = Color.Gray, fontSize = 11.sp)
                Text(animeDetail.premiere, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Duration", color = Color.Gray, fontSize = 11.sp)
                Text(animeDetail.duration, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun EpisodesTabContent(
    animeTitle: String,
    poster: String,
    duration: String,
    episodesState: DetailState<List<Episode>>,
    selectedAudioType: String,
    onAudioTypeChange: (String) -> Unit,
    onPlayClick: (String, String, Int, String) -> Unit,
    downloads: List<com.aniplex.app.data.download.DownloadTask>,
    triggerDownload: (Episode) -> Unit,
    watchHistory: HistoryItem? = null,
    selectedVersion: String,
    onVersionChange: (String) -> Unit,
    hasMultipleVersions: Boolean = false
) {
    var showSeasonDialog by remember { mutableStateOf(false) }
    var selectedChunkIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Season Selector and Audio/Version Toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val totalEpisodes = (episodesState as? DetailState.Success)?.data?.size ?: 0
            val chunks = (episodesState as? DetailState.Success)?.data?.chunked(25) ?: emptyList()

            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { if (chunks.size > 1) showSeasonDialog = true }
                ) {
                    Text(
                        text = if (chunks.size > 1) "Episodes ${selectedChunkIndex * 25 + 1}-${minOf((selectedChunkIndex + 1) * 25, totalEpisodes)}" else "All Episodes",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (chunks.size > 1) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Episodes",
                            tint = Color.White
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = showSeasonDialog,
                    onDismissRequest = { showSeasonDialog = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    chunks.forEachIndexed { index, _ ->
                        DropdownMenuItem(
                            text = { Text("Episodes ${index * 25 + 1}-${minOf((index + 1) * 25, totalEpisodes)}", color = Color.White) },
                            onClick = {
                                selectedChunkIndex = index
                                showSeasonDialog = false
                            }
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Audio Language Toggle Selector
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(8.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { onAudioTypeChange("SUB") }
                            .background(if (selectedAudioType == "SUB") CrunchyrollOrange else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("SUB", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clickable { onAudioTypeChange("DUB") }
                            .background(if (selectedAudioType == "DUB") NetflixRed else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("DUB", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Anime Version Toggle Selector [TV vs UNCUT]
                if (hasMultipleVersions) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .clickable { onVersionChange("censored") }
                                .background(if (selectedVersion == "censored") CrunchyrollOrange else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("TV", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clickable { onVersionChange("uncensored") }
                                .background(if (selectedVersion == "uncensored") CrunchyrollOrange else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("UNCUT", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        when (val state = episodesState) {
            is DetailState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CrunchyrollOrange)
                }
            }
            is DetailState.Success -> {
                val episodeList = state.data
                if (episodeList.isEmpty()) {
                    Text(
                        text = "No episodes available.",
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val chunks = episodeList.chunked(25)
                        val displayEpisodes = if (chunks.isNotEmpty() && selectedChunkIndex < chunks.size) chunks[selectedChunkIndex] else emptyList()
                        
                        displayEpisodes.forEach { episode ->
                            val isFiller = episode.isFiller
                            val isEpisodeFinished = if (watchHistory != null) {
                                if (watchHistory.episodeTitle == "Finished Watching") {
                                    true
                                } else if (watchHistory.episodeId == episode.id) {
                                    val progress = if (watchHistory.totalDuration > 0) {
                                        watchHistory.progressPosition.toFloat() / watchHistory.totalDuration.toFloat()
                                    } else {
                                        0f
                                    }
                                    progress >= 0.95f
                                } else {
                                    watchHistory.episodeNumber > episode.number
                                }
                            } else {
                                false
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayClick(episode.id, animeTitle, episode.number, selectedAudioType.lowercase()) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail Placeholder
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(72.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.DarkGray)
                                ) {
                                    coil.compose.AsyncImage(
                                        model = poster,
                                        contentDescription = "Episode Thumbnail",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().alpha(0.6f)
                                    )
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))
                                    )
                                    Icon(
                                        imageVector = if (isEpisodeFinished) Icons.Default.Replay else Icons.Default.PlayArrow,
                                        contentDescription = if (isEpisodeFinished) "Replay" else "Play",
                                        tint = if (isEpisodeFinished) CrunchyrollOrange else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${episode.number}. ${episode.title.ifBlank { "Episode ${episode.number}" }}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Uncut vs TV badge
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (selectedVersion == "uncensored") 
                                                        CrunchyrollOrange.copy(alpha = 0.15f) 
                                                    else 
                                                        Color.Gray.copy(alpha = 0.2f)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (selectedVersion == "uncensored") "UNCUT" else "TV-BROADCAST",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedVersion == "uncensored") CrunchyrollOrange else Color.LightGray
                                            )
                                        }
                                        if (isFiller) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.Red.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "FILLER",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Red
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                val epDownloadTask = downloads.find { it.episodeId == episode.id }
                                val epStatus = epDownloadTask?.status?.collectAsStateWithLifecycle()?.value

                                IconButton(onClick = { triggerDownload(episode) }) {
                                    when (epStatus) {
                                        DownloadStatus.QUEUED -> {
                                            CircularProgressIndicator(color = CrunchyrollOrange, modifier = Modifier.size(20.dp))
                                        }
                                        DownloadStatus.DOWNLOADING -> {
                                            val progress = epDownloadTask?.progress?.collectAsStateWithLifecycle()?.value ?: 0f
                                            CircularProgressIndicator(
                                                progress = { progress },
                                                color = CrunchyrollOrange,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        DownloadStatus.COMPLETED -> {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Downloaded",
                                                tint = CrunchyrollOrange,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        DownloadStatus.FAILED -> {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Download Failed (Click to retry)",
                                                tint = Color.Red,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Download",
                                                tint = Color.LightGray,
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
            is DetailState.Error -> {
                Text(
                    text = state.message,
                    color = NetflixRed,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
fun CharactersTabContent(charactersState: DetailState<List<Character>>) {
    when (val state = charactersState) {
        is DetailState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CrunchyrollOrange)
            }
        }
        is DetailState.Success -> {
            val characters = state.data
            if (characters.isEmpty()) {
                Text(
                    text = "No character profiles found.",
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 16.dp, bottom = 24.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(characters) { character ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(80.dp)
                        ) {
                            AsyncImage(
                                model = character.poster,
                                contentDescription = character.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(Color.DarkGray)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = character.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Badge(
                                containerColor = if (character.role == "Main") CrunchyrollOrange.copy(alpha = 0.2f) else Color.DarkGray,
                                contentColor = if (character.role == "Main") CrunchyrollOrange else Color.LightGray,
                                modifier = Modifier.height(16.dp)
                            ) {
                                Text(
                                    text = character.role,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        is DetailState.Error -> {
            Text(
                text = state.message,
                color = NetflixRed,
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
fun RecommendationsTabContent(
    recommendations: List<com.aniplex.app.domain.model.Anime>,
    onAnimeClick: (String) -> Unit
) {
    if (recommendations.isEmpty()) {
        Text(
            text = "No related recommendations found.",
            color = Color.Gray,
            modifier = Modifier.padding(start = 16.dp, bottom = 24.dp)
        )
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(recommendations) { anime ->
                AnimeCard(
                    anime = anime,
                    onClick = onAnimeClick
                )
            }
        }
    }
}

data class SeasonGroup(
    val primary: com.aniplex.app.domain.model.Season,
    val alternatives: List<com.aniplex.app.domain.model.Season>
)


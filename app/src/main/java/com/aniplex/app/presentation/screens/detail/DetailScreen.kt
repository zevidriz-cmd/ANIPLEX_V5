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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import com.aniplex.app.data.download.DownloadManager
import com.aniplex.app.data.download.DownloadStatus
import com.aniplex.app.data.local.preferences.PreferenceManager
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.valentinilk.shimmer.shimmer
import com.aniplex.app.presentation.components.ShimmerBox
import com.aniplex.app.presentation.components.ErrorPlaceholder
import com.aniplex.app.domain.model.AnimeDetail
import com.aniplex.app.domain.model.Character
import com.aniplex.app.domain.model.Episode
import com.aniplex.app.domain.model.HistoryItem
import com.aniplex.app.domain.model.Season
import com.aniplex.app.domain.model.StoryArc
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
    onPlayClick: (String, String, String, Int, String, Long) -> Unit,
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
    val storyArcsState by viewModel.storyArcsState.collectAsStateWithLifecycle()

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

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, animeId) {
        lifecycleOwner.lifecycle.currentStateFlow.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                viewModel.refreshWatchHistory(animeId)
            }
        }
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
                    storyArcsState = storyArcsState,
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
    onPlayClick: (String, String, String, Int, String, Long) -> Unit,
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
    storyArcsState: DetailState<List<StoryArc>> = DetailState.Loading,
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
                    SubcomposeAsyncImage(
                        model = animeDetail.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        loading = { ShimmerBox(modifier = Modifier.fillMaxSize()) },
                        error = { ErrorPlaceholder(modifier = Modifier.fillMaxSize()) },
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
                    val seasons = (seasonsState as? DetailState.Success)?.data ?: emptyList()
                    val mainSeasons = seasons.filter { it.relationType == "MAIN" && it.seasonNumber > 0 && it.format != "MOVIE" }
                    val currentSeasonIdx = mainSeasons.indexOfFirst { it.resolvedId == animeDetail.id || it.malId == animeDetail.malId }
                    val nextSeason = if (currentSeasonIdx != -1 && currentSeasonIdx < mainSeasons.size - 1) mainSeasons[currentSeasonIdx + 1] else null

                    val hasHistory = watchHistory != null
                    val isSeasonFinished = watchHistory != null && episodes.isNotEmpty() && watchHistory.episodeNumber >= episodes.size

                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (isSeasonFinished && nextSeason != null) {
                                    onSeasonSelected(nextSeason.malId)
                                } else if (isSeasonFinished) {
                                    if (episodes.isNotEmpty()) {
                                        val firstEp = episodes.first()
                                        onPlayClick(firstEp.id, animeDetail.id, animeDetail.name, firstEp.number, selectedAudioType.lowercase(), 0L)
                                    }
                                } else if (hasHistory && watchHistory != null) {
                                    onPlayClick(watchHistory.episodeId, animeDetail.id, animeDetail.name, watchHistory.episodeNumber, selectedAudioType.lowercase(), watchHistory.progressPosition)
                                } else if (episodes.isNotEmpty()) {
                                    val firstEpisode = episodes.first()
                                    onPlayClick(firstEpisode.id, animeDetail.id, animeDetail.name, firstEpisode.number, selectedAudioType.lowercase(), 0L)
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange),
                            enabled = episodes.isNotEmpty() || (isSeasonFinished && nextSeason != null)
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
                                        if (isSeasonFinished && nextSeason != null) {
                                            val sNum = if (nextSeason.seasonNumber > 0) "SEASON ${nextSeason.seasonNumber}" else "NEXT SEASON"
                                            append("WATCH $sNum")
                                        } else if (isSeasonFinished) {
                                            append("REWATCH E1")
                                        } else if (hasHistory) {
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
                                selectedAudioType.lowercase(),
                                watchHistory.progressPosition
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
                        if (seasons.size > 1) {
                            var expanded by remember { mutableStateOf(false) }
                            // Current selected season is the one that matches this anime's title or the first one if we can't figure it out
                            val currentSeason = seasons.find { it.malId == animeDetail.malId } ?: seasons.firstOrNull()
                                                      val groupedSeasons = remember(seasons) {
                                val map = linkedMapOf<String, List<Season>>()
                                val mainSeasons = seasons.filter { it.relationType == "MAIN" }
                                val sideSeasons = seasons.filter { it.relationType == "SIDE_STORY" }
                                val otherSeasons = seasons.filter { it.relationType != "MAIN" && it.relationType != "SIDE_STORY" }
                                
                                if (mainSeasons.isNotEmpty()) map["MAIN TIMELINE"] = mainSeasons
                                if (sideSeasons.isNotEmpty()) map["MOVIES & SPECIALS"] = sideSeasons
                                if (otherSeasons.isNotEmpty()) map["OTHER"] = otherSeasons
                                
                                map
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
                                            
                                            val groupEntries = groupedSeasons.toList()
                                            groupEntries.forEachIndexed { groupIdx, (groupName, groupSeasons) ->
                                                Text(
                                                    text = groupName,
                                                    color = TextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                                    letterSpacing = 1.sp
                                                )
                                                
                                                groupSeasons.forEach { season ->
                                                    val isCurrent = season.malId == animeDetail.malId
                                                    val watchNum = if (season.relationType == "MAIN" && season.seasonNumber > 0) {
                                                        season.seasonNumber.toString().let { if (it.length == 1) "0$it" else it }
                                                    } else {
                                                        "-"
                                                    }
                                                    
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(
                                                                if (isCurrent) CrunchyrollOrange.copy(alpha = 0.08f) else Color.Transparent
                                                            )
                                                            .then(
                                                                if (isCurrent) Modifier.border(1.dp, CrunchyrollOrange.copy(alpha = 0.2f), RoundedCornerShape(8.dp)) else Modifier
                                                            )
                                                            .clickable {
                                                                expanded = false
                                                                if (!isCurrent) {
                                                                    onSeasonSelected(season.malId)
                                                                }
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                                    ) {
                                                        Text(
                                                            text = if (watchNum == "-") watchNum else "#$watchNum",
                                                            color = if (isCurrent) CrunchyrollOrange else TextSecondary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .width(36.dp)
                                                                .background(
                                                                    if (isCurrent) CrunchyrollOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                                                    RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(vertical = 4.dp, horizontal = 4.dp),
                                                            textAlign = TextAlign.Center
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = season.title.takeIf { it.isNotBlank() } ?: (animeDetail.name + if (season.seasonNumber > 1) " Season ${season.seasonNumber}" else ""),
                                                                color = if (isCurrent) CrunchyrollOrange else Color.White,
                                                                fontSize = 14.sp,
                                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            
                                                            val isMovie = season.format == "MOVIE" || season.episodes == 1
                                                            val isSpecial = season.format == "SPECIAL" || season.title.lowercase().contains("special")
                                                            val badgeText = when {
                                                                isMovie -> "MOVIE"
                                                                isSpecial -> "SPECIAL"
                                                                else -> "SERIES"
                                                            }
                                                            val badgeColor = if (isMovie) CrunchyrollOrange else if (isSpecial) Color(0xFFFFD54F) else TextSecondary
                                                            val badgeBg = if (isMovie) CrunchyrollOrange.copy(alpha = 0.12f) else if (isSpecial) Color(0xFFFFD54F).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f)
                                                            val badgeBorder = if (isMovie) CrunchyrollOrange.copy(alpha = 0.25f) else if (isSpecial) Color(0xFFFFD54F).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f)
                                                            val badgeIcon = if (isMovie) Icons.Default.Movie else if (isSpecial) Icons.Default.Star else Icons.Default.Tv

                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(top = 4.dp)
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier
                                                                        .background(badgeBg, RoundedCornerShape(4.dp))
                                                                        .border(1.dp, badgeBorder, RoundedCornerShape(4.dp))
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = badgeIcon,
                                                                        contentDescription = null,
                                                                        tint = badgeColor,
                                                                        modifier = Modifier.size(10.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(
                                                                        text = badgeText,
                                                                        color = badgeColor,
                                                                        fontSize = 8.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }

                                                                if (!isMovie && season.episodes > 0) {
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Text(
                                                                        text = "${season.episodes} EPISODES",
                                                                        color = TextSecondary,
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.Medium
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        if (isCurrent) {
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
                                                
                                                if (groupIdx < groupEntries.size - 1) {
                                                    Spacer(modifier = Modifier.height(4.dp))
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
                    onPlayClick = { epId, title, epNum, cat, progress ->
                        onPlayClick(epId, animeDetail.id, title, epNum, cat, progress)
                    },
                    downloads = downloads,
                    triggerDownload = triggerDownload,
                    watchHistory = watchHistory,
                    selectedVersion = selectedVersion,
                    onVersionChange = onVersionChange,
                    hasMultipleVersions = hasMultipleVersions,
                    storyArcsState = storyArcsState
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
                        onPlayClick(watchHistory.episodeId, animeDetail.id, animeDetail.name, watchHistory.episodeNumber, selectedAudioType.lowercase(), watchHistory.progressPosition)
                    } else if (episodes.isNotEmpty()) {
                        val firstEpisode = episodes.first()
                        onPlayClick(firstEpisode.id, animeDetail.id, animeDetail.name, firstEpisode.number, selectedAudioType.lowercase(), 0L)
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
    onPlayClick: (String, String, Int, String, Long) -> Unit,
    downloads: List<com.aniplex.app.data.download.DownloadTask>,
    triggerDownload: (Episode) -> Unit,
    watchHistory: HistoryItem? = null,
    selectedVersion: String,
    onVersionChange: (String) -> Unit,
    hasMultipleVersions: Boolean = false,
    storyArcsState: DetailState<List<StoryArc>> = DetailState.Loading
) {
    val arcs = if (storyArcsState is DetailState.Success) storyArcsState.data else emptyList()
    val hasArcs = arcs.isNotEmpty()

    val activeArcIndex = remember(arcs, watchHistory) {
        val index = arcs.indexOfFirst { arc ->
            watchHistory != null && watchHistory.episodeNumber in arc.start..arc.end
        }
        if (index != -1) index else 0
    }
    var selectedArcIndex by remember(activeArcIndex) { mutableStateOf(activeArcIndex) }

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

            val showDropdown = (hasArcs && arcs.size > 1) || (!hasArcs && chunks.size > 1)
            if (showDropdown) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showSeasonDialog = true }
                    ) {
                        Text(
                            text = if (hasArcs) {
                                if (selectedArcIndex in arcs.indices) arcs[selectedArcIndex].label else "All Episodes"
                            } else {
                                "Episodes ${selectedChunkIndex * 25 + 1}-${minOf((selectedChunkIndex + 1) * 25, totalEpisodes)}"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Episodes",
                            tint = Color.White
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showSeasonDialog,
                        onDismissRequest = { showSeasonDialog = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        if (hasArcs) {
                            arcs.forEachIndexed { index, arc ->
                                DropdownMenuItem(
                                    text = { Text(arc.label, color = Color.White) },
                                    onClick = {
                                        selectedArcIndex = index
                                        showSeasonDialog = false
                                    }
                                )
                            }
                        } else {
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
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
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
                        val displayEpisodes = if (hasArcs) {
                            if (selectedArcIndex in arcs.indices) arcs[selectedArcIndex].episodes else emptyList()
                        } else {
                            val chunks = episodeList.chunked(25)
                            if (chunks.isNotEmpty() && selectedChunkIndex < chunks.size) chunks[selectedChunkIndex] else emptyList()
                        }
                        
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

                            val epProg = if (watchHistory != null && (watchHistory.episodeId == episode.id || watchHistory.episodeNumber == episode.number)) watchHistory.progressPosition else 0L

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayClick(episode.id, animeTitle, episode.number, selectedAudioType.lowercase(), epProg) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var isThumbLoading by remember(episode.id) { mutableStateOf(true) }
                                var isThumbError by remember(episode.id) { mutableStateOf(false) }
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
                                        onLoading = { isThumbLoading = true; isThumbError = false },
                                        onSuccess = { isThumbLoading = false; isThumbError = false },
                                        onError = { isThumbLoading = false; isThumbError = true },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .alpha(0.6f)
                                            .then(if (isThumbLoading) Modifier.shimmer() else Modifier)
                                    )
                                    if (isThumbError) {
                                        ErrorPlaceholder(modifier = Modifier.fillMaxSize())
                                    }
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
                    items(characters, key = { it.id }) { character ->
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
            items(recommendations, key = { it.id }) { anime ->
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


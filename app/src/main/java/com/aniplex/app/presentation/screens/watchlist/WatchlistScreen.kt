package com.aniplex.app.presentation.screens.watchlist

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import com.aniplex.app.presentation.components.ErrorPlaceholder
import com.aniplex.app.data.download.DownloadManager
import com.aniplex.app.data.download.DownloadStatus
import com.aniplex.app.data.download.DownloadTask
import com.aniplex.app.domain.model.HistoryItem
import com.aniplex.app.presentation.components.ScheduleItemShimmer
import com.aniplex.app.presentation.screens.history.HistoryUiState
import com.aniplex.app.presentation.screens.history.HistoryViewModel
import com.aniplex.app.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onAnimeClick: (String) -> Unit,
    onEpisodeClick: (String, String, String, Int, String, Long) -> Unit,
    onSearchClick: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    watchlistViewModel: WatchlistViewModel = hiltViewModel(),
    historyViewModel: HistoryViewModel = hiltViewModel()
) {
    val tabs = listOf("Watchlist", "History", "Downloads")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    val watchlistState by watchlistViewModel.watchlistState.collectAsStateWithLifecycle()
    val historyState by historyViewModel.historyState.collectAsStateWithLifecycle()
    val downloads by DownloadManager.downloads.collectAsStateWithLifecycle()
    val sortOrder by watchlistViewModel.sortOrder.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val defaultAudio = remember { historyViewModel.defaultAudioCategory }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Lists",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundVoid,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = BackgroundVoid
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Segmented Tabs
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = BackgroundVoid,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = CrunchyrollOrange
                    )
                },
                divider = {
                    HorizontalDivider(color = SurfaceDarkVariant, thickness = 1.dp)
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { 
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        // Watchlist Tab
                        val statusFilter by watchlistViewModel.statusFilter.collectAsStateWithLifecycle()
                        WatchlistTabContent(
                            state = watchlistState,
                            onAnimeClick = onAnimeClick,
                            onRemove = { watchlistViewModel.removeFromWatchlist(it) },
                            sortOrder = sortOrder,
                            onSortOrderChange = { watchlistViewModel.setSortOrder(it) },
                            onToggleFavorite = { id, currentVal -> watchlistViewModel.toggleFavorite(id, currentVal) },
                            statusFilter = statusFilter,
                            onStatusFilterChange = { watchlistViewModel.setStatusFilter(it) }
                        )
                    }
                    1 -> {
                        // History Tab
                        // History Tab
                        HistoryTabContent(
                            state = historyState,
                            defaultAudio = defaultAudio,
                            onAnimeClick = onAnimeClick,
                            onEpisodeClick = onEpisodeClick,
                            onRemove = { historyViewModel.removeFromHistory(it) }
                        )
                    }
                    2 -> {
                        // Downloads Tab
                        DownloadsTabContent(
                            downloads = downloads,
                            onPlayClick = { epId, animId, title, epNum, cat ->
                                onEpisodeClick(epId, animId, title, epNum, cat, 0L)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistTabContent(
    state: WatchlistUiState,
    onAnimeClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    statusFilter: WatchlistStatusFilter,
    onStatusFilterChange: (WatchlistStatusFilter) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Status Filter Capsule Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WatchlistStatusFilter.values().forEach { filter ->
                val isSelected = filter == statusFilter
                val label = when (filter) {
                    WatchlistStatusFilter.ALL -> "All Shows"
                    WatchlistStatusFilter.WATCHING -> "Watching"
                    WatchlistStatusFilter.PLANNING -> "Plan to Watch"
                    WatchlistStatusFilter.COMPLETED -> "Completed"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) CrunchyrollOrange else SurfaceDark)
                        .border(1.dp, if (isSelected) CrunchyrollOrange else SurfaceDarkVariant, RoundedCornerShape(16.dp))
                        .clickable { onStatusFilterChange(filter) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        when (state) {
            is WatchlistUiState.Loading -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(8) {
                        ScheduleItemShimmer()
                    }
                }
            }
            is WatchlistUiState.Empty -> {
                EmptyWatchlistState()
            }
            is WatchlistUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = ErrorColor,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is WatchlistUiState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                var showSortMenu by remember { mutableStateOf(false) }

                // Sorting Header (Stitch Design: Selected Sort Order + sort + tune buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (sortOrder) {
                            SortOrder.RECENT_ACTIVITY -> "Recent Activity"
                            SortOrder.DATE_ADDED -> "Date Added"
                            SortOrder.ALPHABETICAL -> "Alphabetical"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { showSortMenu = true }
                            )
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(SurfaceDark)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Recent Activity", color = Color.White) },
                                    onClick = {
                                        onSortOrderChange(SortOrder.RECENT_ACTIVITY)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date Added", color = Color.White) },
                                    onClick = {
                                        onSortOrderChange(SortOrder.DATE_ADDED)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Alphabetical", color = Color.White) },
                                    onClick = {
                                        onSortOrderChange(SortOrder.ALPHABETICAL)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                        
                        val context = LocalContext.current
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filter",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { 
                                    android.widget.Toast.makeText(context, "Filter options coming soon", android.widget.Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.list, key = { it.id }) { item ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    onRemove(item.id)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NetflixRed)
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = Color.White
                                    )
                                }
                            }
                        ) {
                            WatchlistRowCard(
                                item = item,
                                onClick = { onAnimeClick(item.id) },
                                onToggleFavorite = { onToggleFavorite(item.id, item.isFavorite) },
                                onDetailsClick = { onAnimeClick(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun WatchlistRowCard(
    item: WatchlistItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDetailsClick: () -> Unit
) {
    var isImageLoading by remember { mutableStateOf(true) }
    var isImageError by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, SurfaceDarkVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
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
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dub | Sub",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Stitch Buttons: Favorite Heart
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (item.isFavorite) CrunchyrollOrange else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

                var showMenu by remember { mutableStateOf(false) }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = SurfaceDarkVariant
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remove from Watchlist", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onToggleFavorite()
                            }
                        )
                    }
                }
        }
    }
}

@Composable
fun HistoryTabContent(
    state: HistoryUiState,
    defaultAudio: String,
    onAnimeClick: (String) -> Unit,
    onEpisodeClick: (String, String, String, Int, String, Long) -> Unit,
    onRemove: (String) -> Unit
) {
    when (state) {
        is HistoryUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CrunchyrollOrange)
            }
        }
        is HistoryUiState.Empty -> {
            EmptyHistoryState()
        }
        is HistoryUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    color = ErrorColor,
                    fontSize = 14.sp
                )
            }
        }
        is HistoryUiState.Success -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.list, key = { it.animeId }) { item ->
                    HistoryGridCard(
                        item = item,
                        defaultAudio = defaultAudio,
                        onPlayClick = {
                            onEpisodeClick(
                                item.episodeId,
                                item.animeId,
                                item.animeTitle,
                                item.episodeNumber,
                                defaultAudio,
                                item.progressPosition
                            )
                        },
                        onDetailsClick = { onAnimeClick(item.animeId) },
                        onRemove = { onRemove(item.animeId) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryGridCard(
    item: HistoryItem,
    defaultAudio: String,
    onPlayClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRemove: () -> Unit
) {
    var isImageLoading by remember { mutableStateOf(true) }
    var isImageError by remember { mutableStateOf(false) }
    val progress = if (item.totalDuration > 0) {
        item.progressPosition.toFloat() / item.totalDuration.toFloat()
    } else {
        0f
    }
    val remainingMinutes = ((item.totalDuration - item.progressPosition) / 60000L).coerceAtLeast(0L)
    val isWatched = progress >= 0.95f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, SurfaceDarkVariant)
    ) {
        Column {
            // Thumbnail container with play button and progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = null,
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

                // Translucent dark overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )

                // Play / Replay icon in center
                Icon(
                    imageVector = if (isWatched) Icons.Default.Replay else Icons.Default.PlayCircle,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                )

                // Remaining time or Watched badge at bottom-right
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isWatched) "Watched" else "${remainingMinutes}m left",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Progress Bar at bottom edge of thumbnail
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = CrunchyrollOrange,
                    trackColor = Color.Transparent
                )
            }

            // Text Info & option menu below thumbnail
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.animeTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.episodeTitle.ifBlank { "Episode ${item.episodeNumber}" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "E${item.episodeNumber} • ${defaultAudio.uppercase()}",
                        fontSize = 10.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                var showMenu by remember { mutableStateOf(false) }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = SurfaceDarkVariant
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remove from History", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onRemove()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Go to Details", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onDetailsClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsTabContent(
    downloads: List<DownloadTask>,
    onPlayClick: (episodeId: String, animeId: String, animeTitle: String, episodeNumber: Int, category: String) -> Unit
) {
    val context = LocalContext.current

    if (downloads.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundVoid),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DownloadForOffline,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No downloads...Yet!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Download shows and movies to watch when you're offline.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundVoid)
        ) {
            items(downloads, key = { it.episodeId }) { task ->
                val status by task.status.collectAsStateWithLifecycle()
                val progress by task.progress.collectAsStateWithLifecycle()
                var isImageLoading by remember(task.episodeId) { mutableStateOf(true) }
                var isImageError by remember(task.episodeId) { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark, RoundedCornerShape(12.dp))
                        .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(12.dp))
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
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = task.posterUrl,
                            contentDescription = null,
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
                                    color = Color.Green,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            DownloadStatus.FAILED -> {
                                Text(
                                    text = "Download Failed",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Actions Row
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
        }
    }
}

@Composable
fun EmptyWatchlistState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BookmarkBorder,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Watchlist is Empty",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Keep track of anime you want to watch by bookmarking them from their details screen.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun EmptyHistoryState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Watch History",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Watch episodes of your favorite anime, and your history will be displayed here for quick resuming.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

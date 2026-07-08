package com.aniplex.app.presentation.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aniplex.app.domain.model.Anime
import com.aniplex.app.domain.model.HistoryItem
import com.aniplex.app.domain.model.HomeData
import com.aniplex.app.domain.model.SpotlightAnime
import com.aniplex.app.theme.BackgroundVoid
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.SurfaceDark
import com.aniplex.app.theme.TextMuted
import com.aniplex.app.theme.TextSecondary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.aniplex.app.presentation.screens.watchlist.WatchlistViewModel
import com.aniplex.app.domain.model.AnimeDetail
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvHomeScreen(
    onAnimeClick: (String) -> Unit,
    onEpisodeClick: (String, String, String, Int, String, Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val tvBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                val parentFraction = 0.3f
                val childFraction = 0f
                val leadingEdge = parentFraction * containerSize - (childFraction * size)
                return offset - leadingEdge
            }
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val continueWatchingList by viewModel.continueWatchingList.collectAsStateWithLifecycle()
    val watchlistViewModel: WatchlistViewModel = hiltViewModel()

    var activeBackdropUrl by remember { mutableStateOf<String?>(null) }
    var focusedTitle by remember { mutableStateOf("") }
    var focusedDescription by remember { mutableStateOf("") }
    var longPressedAnime by remember { mutableStateOf<Anime?>(null) }

    val context = LocalContext.current

    CompositionLocalProvider(LocalBringIntoViewSpec provides tvBringIntoViewSpec) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BackgroundVoid)
        ) {
        // 1. Dynamic background backdrop with blur/dark overlay
        activeBackdropUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.25f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, BackgroundVoid.copy(alpha = 0.9f), BackgroundVoid)
                    )
                )
        )

        when (val state = uiState) {
            is HomeUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrunchyrollOrange)
                }
            }
            is HomeUiState.Success -> {
                val homeData = state.homeData
                
                // Initialize backdrop URL with the first spotlight
                LaunchedEffect(homeData.spotlightAnimes) {
                    if (activeBackdropUrl == null && homeData.spotlightAnimes.isNotEmpty()) {
                        activeBackdropUrl = homeData.spotlightAnimes.first().poster
                        focusedTitle = homeData.spotlightAnimes.first().name
                        focusedDescription = homeData.spotlightAnimes.first().description
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 48.dp, vertical = 24.dp)
                ) {
                    // Header Title / Focus Info Pane
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = focusedTitle,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = focusedDescription,
                            fontSize = 16.sp,
                            color = Color.LightGray,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // 2. Focusable Genre Chips Row
                    val genresList = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Supernatural")
                    Text(
                        text = "Browse Genres",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        items(genresList.distinct(), key = { it }) { genre ->
                            var isChipFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isChipFocused = it.isFocused }
                                    .clickable {
                                        Toast.makeText(context, "Switch to Search tab to filter by: $genre", Toast.LENGTH_SHORT).show()
                                    }
                                    .background(
                                        color = if (isChipFocused) CrunchyrollOrange else SurfaceDark,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .border(
                                        width = if (isChipFocused) 2.dp else 1.dp,
                                        color = if (isChipFocused) Color.White else Color(0xFF2C2C35),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = genre,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Continue Watching Row
                    if (continueWatchingList.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                            Text(
                                text = "Continue Watching",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            androidx.compose.foundation.lazy.LazyRow(
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(continueWatchingList, key = { it.animeId }) { item ->
                                    TvContinueWatchingCard(
                                        item = item,
                                        onClick = {
                                            onEpisodeClick(
                                                item.episodeId,
                                                item.animeId,
                                                item.animeTitle,
                                                item.episodeNumber,
                                                "sub",
                                                item.progressPosition
                                            )
                                        },
                                        onFocus = {
                                            activeBackdropUrl = item.poster
                                            focusedTitle = item.animeTitle
                                            focusedDescription = "Resume watching: Episode ${item.episodeNumber} - ${item.episodeTitle}"
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Spotlight List Row
                    TvSectionRow(
                        title = "Featured Spotlights",
                        items = homeData.spotlightAnimes.map {
                            Anime(
                                id = it.id,
                                title = it.name,
                                poster = it.poster,
                                type = it.description
                            )
                        },
                        onAnimeFocus = {
                            activeBackdropUrl = it.poster
                            focusedTitle = it.title
                            focusedDescription = it.type
                        },
                        onAnimeClick = onAnimeClick,
                        onAnimeLongClick = { anime ->
                            longPressedAnime = anime
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Trending Row
                    if (homeData.trendingAnimes.isNotEmpty()) {
                        TvSectionRow(
                            title = "Trending Now",
                            items = homeData.trendingAnimes,
                            onAnimeFocus = {
                                activeBackdropUrl = it.poster
                                focusedTitle = it.title
                                focusedDescription = ""
                            },
                            onAnimeClick = onAnimeClick,
                            onAnimeLongClick = { anime ->
                                longPressedAnime = anime
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Latest Episodes Row
                    if (homeData.recentlyUpdatedAnimes.isNotEmpty()) {
                        TvSectionRow(
                            title = "Recently Updated",
                            items = homeData.recentlyUpdatedAnimes,
                            onAnimeFocus = {
                                activeBackdropUrl = it.poster
                                focusedTitle = it.title
                                focusedDescription = ""
                            },
                            onAnimeClick = onAnimeClick,
                            onAnimeLongClick = { anime ->
                                longPressedAnime = anime
                            }
                        )
                    }
                }

                // Focusable TV Action Overlay Dialog for Long Clicked Cards
                if (longPressedAnime != null) {
                    val anime = longPressedAnime!!
                    AlertDialog(
                        onDismissRequest = { longPressedAnime = null },
                        title = {
                            Text(text = anime.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        },
                        containerColor = Color(0xFF0F0F14),
                        text = {
                            Text("What would you like to do with this anime?", color = Color.LightGray)
                        },
                        confirmButton = {
                            var isWatchlistFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    val detail = AnimeDetail(
                                        id = anime.id,
                                        name = anime.title,
                                        poster = anime.poster,
                                        description = "",
                                        rating = "",
                                        duration = "",
                                        recommendations = emptyList()
                                    )
                                    watchlistViewModel.toggleWatchlist(detail)
                                    Toast.makeText(context, "Watchlist Toggled", Toast.LENGTH_SHORT).show()
                                    longPressedAnime = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isWatchlistFocused) CrunchyrollOrange else SurfaceDark
                                ),
                                modifier = Modifier.onFocusChanged { isWatchlistFocused = it.isFocused }
                            ) {
                                Text("Toggle Watchlist", color = Color.White)
                            }
                        },
                        dismissButton = {
                            var isCancelFocused by remember { mutableStateOf(false) }
                            TextButton(
                                onClick = { longPressedAnime = null },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (isCancelFocused) CrunchyrollOrange else Color.LightGray
                                ),
                                modifier = Modifier.onFocusChanged { isCancelFocused = it.isFocused }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
            is HomeUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = Color.Red, fontSize = 18.sp)
                }
            }
        }
    }
}
}

@Composable
fun TvSectionRow(
    title: String,
    items: List<Anime>,
    onAnimeFocus: (Anime) -> Unit,
    onAnimeClick: (String) -> Unit,
    onAnimeLongClick: (Anime) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                TvAnimeCard(
                    anime = item,
                    onFocus = { onAnimeFocus(item) },
                    onClick = { onAnimeClick(item.id) },
                    onLongClick = { onAnimeLongClick(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvAnimeCard(
    anime: Anime,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f)
    val borderStroke = if (isFocused) BorderStroke(3.dp, CrunchyrollOrange) else null

    Card(
        modifier = modifier
            .width(140.dp)
            .height(210.dp)
            .scale(scale)
            .onFocusChanged { 
                isFocused = it.isFocused 
                if (it.isFocused) {
                    onFocus()
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke() }
            ),
        border = borderStroke,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = anime.poster,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            Text(
                text = anime.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun TvContinueWatchingCard(
    item: HistoryItem,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f)
    val borderStroke = if (isFocused) BorderStroke(3.dp, CrunchyrollOrange) else null

    Card(
        modifier = modifier
            .width(220.dp)
            .height(130.dp)
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocus()
                }
            }
            .clickable(onClick = onClick),
        border = borderStroke,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.animeTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Overlay gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )

            // Text info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = item.animeTitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Episode ${item.episodeNumber}",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                
                // Progress Bar
                if (item.totalDuration > 0) {
                    val progress = item.progressPosition.toFloat() / item.totalDuration.toFloat()
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        color = CrunchyrollOrange,
                        trackColor = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

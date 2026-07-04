package com.aniplex.app.presentation.screens.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.horizontalScroll
import com.aniplex.app.domain.model.AnimeDetail
import com.aniplex.app.domain.model.Episode
import com.aniplex.app.domain.model.Season
import com.aniplex.app.domain.model.Character
import com.aniplex.app.domain.model.StoryArc
import com.aniplex.app.presentation.screens.home.TvAnimeCard
import androidx.compose.ui.text.style.TextAlign
import com.aniplex.app.presentation.screens.detail.DetailState
import android.widget.Toast
import com.aniplex.app.theme.BackgroundVoid
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.SurfaceDark
import com.aniplex.app.theme.TextMuted
import com.aniplex.app.theme.TextSecondary

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.shape.CircleShape

@Composable
fun TvDetailScreen(
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
    val isResolvingSeason by viewModel.isResolvingSeason.collectAsStateWithLifecycle()
    val selectedVersion by viewModel.selectedVersion.collectAsStateWithLifecycle()
    val hasMultipleVersions by viewModel.hasMultipleVersions.collectAsStateWithLifecycle()
    val storyArcsState by viewModel.storyArcsState.collectAsStateWithLifecycle()
    val resolvedAnikotoId by viewModel.resolvedAnikotoId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val resolutionError by viewModel.resolutionError.collectAsStateWithLifecycle()

    LaunchedEffect(resolvedAnikotoId) {
        resolvedAnikotoId?.let { id ->
            onSeasonSelect(id)
            viewModel.clearResolvedId()
        }
    }

    LaunchedEffect(resolutionError) {
        resolutionError?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrunchyrollOrange)
                }
            }
            is DetailState.Success -> {
                val detail = state.data
                val seasonsList = (seasonsState as? DetailState.Success)?.data ?: emptyList()
                val arcs = if (storyArcsState is DetailState.Success) (storyArcsState as DetailState.Success<List<StoryArc>>).data else emptyList()
                val hasArcs = arcs.isNotEmpty()

                val currentHistory = watchHistory
                val activeArcIndex = remember(arcs, currentHistory) {
                    val index = arcs.indexOfFirst { arc ->
                        currentHistory != null && currentHistory.episodeNumber in arc.start..arc.end
                    }
                    if (index != -1) index else 0
                }
                var selectedArcIndex by remember(activeArcIndex) { mutableStateOf(activeArcIndex) }
                var selectedChunkIndex by remember(detail.id) { mutableStateOf(0) }
                var selectedTab by remember { mutableStateOf(0) }
                val tabs = listOf("Episodes", "Characters", "Related")
                var selectedAudioType by remember { mutableStateOf("SUB") }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp, vertical = 24.dp)
                ) {
                    // Left Column (40%): Metadata Info & Actions
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.4f)
                            .padding(end = 32.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        AsyncImage(
                            model = detail.poster,
                            contentDescription = detail.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .aspectRatio(1f / 1.4f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = detail.name,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Rating: ${detail.rating ?: "N/A"}", color = CrunchyrollOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = detail.duration ?: "", color = Color.LightGray, fontSize = 14.sp)
                        }
                        
                        // Continue Watching Banner for TV
                        if (watchHistory != null) {
                            var isHistoryFocused by remember { mutableStateOf(false) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .padding(vertical = 12.dp)
                                    .onFocusChanged { isHistoryFocused = it.isFocused }
                                    .clickable {
                                        onPlayClick(
                                            watchHistory!!.episodeId,
                                            detail.id,
                                            detail.name,
                                            watchHistory!!.episodeNumber,
                                            selectedAudioType.lowercase()
                                        )
                                    }
                                    .border(
                                        width = if (isHistoryFocused) 2.dp else 1.dp,
                                        color = if (isHistoryFocused) Color.White else CrunchyrollOrange.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                colors = CardDefaults.cardColors(containerColor = if (isHistoryFocused) CrunchyrollOrange.copy(alpha = 0.2f) else SurfaceDark),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isHistoryFocused) Color.White else CrunchyrollOrange,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Resume E${watchHistory!!.episodeNumber}",
                                            fontSize = 12.sp,
                                            color = CrunchyrollOrange,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (watchHistory!!.episodeTitle.isNotBlank()) watchHistory!!.episodeTitle else "Resume playback",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        var isDescriptionExpanded by remember { mutableStateOf(false) }
                        Text(
                            text = detail.description ?: "No description available.",
                            fontSize = 13.sp,
                            color = Color.LightGray,
                            maxLines = if (isDescriptionExpanded) 20 else 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        var isDescToggleFocused by remember { mutableStateOf(false) }
                        Text(
                            text = if (isDescriptionExpanded) "Show Less" else "Show More",
                            color = if (isDescToggleFocused) CrunchyrollOrange else Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                .onFocusChanged { isDescToggleFocused = it.isFocused }
                                .padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Focusable Action Buttons Grid
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            var isWatchlistFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { viewModel.toggleWatchlist(detail) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isWatchlistFocused) CrunchyrollOrange else SurfaceDark
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isWatchlistFocused = it.isFocused }
                            ) {
                                Icon(
                                    imageVector = if (isWatchlisted) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isWatchlisted) "In Watchlist" else "Add to Watchlist",
                                    color = Color.White
                                )
                            }

                            var isMarkFinishedFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    viewModel.markAsWatched(detail.id, detail.name, detail.poster)
                                    Toast.makeText(context, "Marked as Watched", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMarkFinishedFocused) CrunchyrollOrange else SurfaceDark
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isMarkFinishedFocused = it.isFocused }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mark as Finished", color = Color.White)
                            }

                            if (watchHistory != null) {
                                var isClearHistoryFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = {
                                        viewModel.removeFromHistory(detail.id)
                                        Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isClearHistoryFocused) CrunchyrollOrange else SurfaceDark
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isClearHistoryFocused = it.isFocused }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Clear History", color = Color.White)
                                }
                            }
                        }
                    }

                    // Right Column (60%): Tabbed Layout
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.6f)
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = BackgroundVoid,
                            contentColor = Color.White,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = CrunchyrollOrange
                                )
                            },
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            tabs.forEachIndexed { index, title ->
                                var isTabFocused by remember { mutableStateOf(false) }
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    modifier = Modifier
                                        .onFocusChanged { isTabFocused = it.isFocused }
                                        .padding(vertical = 8.dp),
                                    text = {
                                        Text(
                                            text = title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedTab == index || isTabFocused) Color.White else Color.Gray
                                        )
                                    }
                                )
                            }
                        }

                        when (selectedTab) {
                            0 -> {
                                // Episodes Tab
                                if (isResolvingSeason) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = CrunchyrollOrange)
                                    }
                                } else {
                                    when (val epsState = episodesState) {
                                        is DetailState.Loading -> {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(color = CrunchyrollOrange)
                                            }
                                        }
                                        is DetailState.Success -> {
                                            val episodesList = epsState.data
                                            if (episodesList.isEmpty()) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text(text = "No episodes found.", color = Color.LightGray)
                                                }
                                            } else {
                                                val chunks = episodesList.chunked(25)
                                                val displayEpisodes = if (hasArcs) {
                                                    if (selectedArcIndex in arcs.indices) arcs[selectedArcIndex].episodes else emptyList()
                                                } else {
                                                    if (selectedChunkIndex < chunks.size) chunks[selectedChunkIndex] else emptyList()
                                                }

                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    // Audio and Version switcher controls row
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                                    ) {
                                                        var isAudioFocused by remember { mutableStateOf(false) }
                                                        Button(
                                                            onClick = {
                                                                selectedAudioType = if (selectedAudioType == "SUB") "DUB" else "SUB"
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = if (isAudioFocused) CrunchyrollOrange else SurfaceDark
                                                            ),
                                                            modifier = Modifier.onFocusChanged { isAudioFocused = it.isFocused }
                                                        ) {
                                                            Text("Audio: $selectedAudioType")
                                                        }

                                                        if (hasMultipleVersions) {
                                                            var isVersionFocused by remember { mutableStateOf(false) }
                                                            Button(
                                                                onClick = {
                                                                    val nextVer = if (selectedVersion == "uncensored") "censored" else "uncensored"
                                                                    viewModel.setSelectedVersion(nextVer)
                                                                },
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = if (isVersionFocused) CrunchyrollOrange else SurfaceDark
                                                                ),
                                                                modifier = Modifier.onFocusChanged { isVersionFocused = it.isFocused }
                                                            ) {
                                                                Text("Version: ${if (selectedVersion == "uncensored") "UNCUT" else "TV"}")
                                                            }
                                                        }
                                                    }

                                                    // 1. Season Selector Row (if multiple seasons)
                                                    if (seasonsList.size > 1) {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .horizontalScroll(rememberScrollState())
                                                                .padding(bottom = 12.dp)
                                                        ) {
                                                            seasonsList.forEach { season ->
                                                                val isCurrentSeason = season.malId == detail.malId
                                                                var isSeasonFocused by remember { mutableStateOf(false) }
                                                                val seasonScale by animateFloatAsState(if (isSeasonFocused) 1.04f else 1.0f)

                                                                Box(
                                                                    modifier = Modifier
                                                                        .scale(seasonScale)
                                                                        .onFocusChanged { isSeasonFocused = it.isFocused }
                                                                        .clickable {
                                                                            viewModel.resolveMALAndNavigate(season.malId)
                                                                        }
                                                                        .background(
                                                                            color = if (isCurrentSeason) CrunchyrollOrange else if (isSeasonFocused) SurfaceDark else Color(0xFF14141A),
                                                                            shape = RoundedCornerShape(20.dp)
                                                                        )
                                                                        .border(
                                                                            width = if (isSeasonFocused) 2.dp else 1.dp,
                                                                            color = if (isSeasonFocused) Color.White else if (isCurrentSeason) Color.Transparent else Color(0xFF2C2C35),
                                                                            shape = RoundedCornerShape(20.dp)
                                                                        )
                                                                        .padding(horizontal = 18.dp, vertical = 8.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                    ) {
                                                                        if (isCurrentSeason) {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .size(6.dp)
                                                                                    .clip(CircleShape)
                                                                                    .background(Color.White)
                                                                            )
                                                                        }
                                                                        Text(
                                                                            text = season.title.takeIf { it.isNotBlank() } ?: "Season ${season.seasonNumber}",
                                                                            color = if (isCurrentSeason || isSeasonFocused) Color.White else Color.Gray,
                                                                            fontWeight = FontWeight.Bold,
                                                                            fontSize = 13.sp
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 2. Arc / Episode Classifier Tabs
                                                    val tabItems = if (hasArcs) {
                                                        arcs.map { it.label }
                                                    } else {
                                                        chunks.mapIndexed { index, chunk ->
                                                            val startEp = chunk.firstOrNull()?.number ?: 1
                                                            val endEp = chunk.lastOrNull()?.number ?: 25
                                                            "Ep $startEp-$endEp"
                                                        }
                                                    }

                                                    if (tabItems.size > 1) {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .horizontalScroll(rememberScrollState())
                                                                .padding(bottom = 12.dp)
                                                        ) {
                                                            tabItems.forEachIndexed { index, label ->
                                                                val isActive = if (hasArcs) index == selectedArcIndex else index == selectedChunkIndex
                                                                var isFocused by remember { mutableStateOf(false) }
                                                                val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f)

                                                                Box(
                                                                    modifier = Modifier
                                                                        .scale(scale)
                                                                        .onFocusChanged { isFocused = it.isFocused }
                                                                        .clickable {
                                                                            if (hasArcs) {
                                                                                selectedArcIndex = index
                                                                            } else {
                                                                                selectedChunkIndex = index
                                                                            }
                                                                        }
                                                                        .background(
                                                                            color = if (isActive) CrunchyrollOrange else if (isFocused) SurfaceDark else Color(0xFF14141A),
                                                                            shape = RoundedCornerShape(20.dp)
                                                                        )
                                                                        .border(
                                                                            width = if (isFocused) 2.dp else 1.dp,
                                                                            color = if (isFocused) Color.White else if (isActive) Color.Transparent else Color(0xFF2C2C35),
                                                                            shape = RoundedCornerShape(20.dp)
                                                                        )
                                                                        .padding(horizontal = 18.dp, vertical = 8.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = label,
                                                                        color = if (isActive || isFocused) Color.White else Color.Gray,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 13.sp
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 3. Grid of Episodes
                                                    LazyVerticalGrid(
                                                        columns = GridCells.Fixed(3),
                                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        items(displayEpisodes) { episode: Episode ->
                                                            TvEpisodeCard(
                                                                episode = episode,
                                                                posterUrl = detail.poster,
                                                                onClick = {
                                                                    onPlayClick(
                                                                        episode.id,
                                                                        animeId,
                                                                        detail.name,
                                                                        episode.number,
                                                                        selectedAudioType.lowercase()
                                                                    )
                                                                },
                                                                modifier = Modifier.height(96.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        is DetailState.Error -> {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(text = epsState.message, color = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // Characters Tab
                                TvCharactersTabContent(charactersState = charactersState)
                            }
                            2 -> {
                                // Related recommendations Tab
                                TvRecommendationsTabContent(
                                    recommendations = detail.recommendations,
                                    onAnimeClick = onRecommendationClick
                                )
                            }
                        }
                    }
                }
            }
            is DetailState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun TvEpisodeCard(
    episode: Episode,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f)
    val cardColor = if (isFocused) SurfaceDark else Color(0xFF14141A)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color.White else Color(0xFF2C2C35)
        ),
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // 16:9 Aspect Ratio Thumbnail
            Box(
                modifier = Modifier
                    .aspectRatio(16f / 9f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isFocused) CrunchyrollOrange else Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Episode ${episode.number}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    if (episode.isFiller) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, Color.Red, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "FILLER",
                                color = Color.Red,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = episode.title.takeIf { it.isNotBlank() } ?: "Episode ${episode.number}",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TvCharactersTabContent(
    charactersState: DetailState<List<Character>>,
    modifier: Modifier = Modifier
) {
    when (val state = charactersState) {
        is DetailState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CrunchyrollOrange)
            }
        }
        is DetailState.Success -> {
            val characters = state.data
            if (characters.isEmpty()) {
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No characters found.", color = Color.LightGray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = modifier.fillMaxSize()
                ) {
                    items(characters) { character ->
                        var isFocused by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f)
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isFocused) CrunchyrollOrange else SurfaceDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .scale(scale)
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                AsyncImage(
                                    model = character.poster,
                                    contentDescription = character.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(Color.DarkGray)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = character.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = character.role,
                                    fontSize = 10.sp,
                                    color = if (isFocused) Color.White else Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        is DetailState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = Color.Red)
            }
        }
    }
}

@Composable
fun TvRecommendationsTabContent(
    recommendations: List<com.aniplex.app.domain.model.Anime>,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (recommendations.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No related recommendations found.", color = Color.LightGray)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(recommendations) { anime ->
                TvAnimeCard(
                    anime = anime,
                    onFocus = {},
                    onClick = { onAnimeClick(anime.id) }
                )
            }
        }
    }
}

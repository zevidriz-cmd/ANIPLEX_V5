package com.aniplex.app.presentation.screens.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniplex.app.presentation.components.AnimeCard
import com.aniplex.app.presentation.screens.schedule.ScheduleScreen
import com.aniplex.app.theme.BackgroundVoid
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.NetflixRed
import com.aniplex.app.theme.SurfaceDark
import com.aniplex.app.theme.SurfaceDarkVariant
import com.aniplex.app.presentation.screens.seasonal.SeasonalScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    viewModel: BrowseViewModel = hiltViewModel()
) {
    val topTabs = listOf("All Anime", "Seasonal", "Simulcasts", "Anime Genres")
    val pagerState = rememberPagerState(initialPage = initialTab, pageCount = { topTabs.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(initialTab) {
        if (pagerState.currentPage != initialTab) {
            pagerState.scrollToPage(initialTab)
        }
    }

    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val categories = listOf(
        "most-popular" to "Popular",
        "tv" to "TV Series",
        "movie" to "Movies",
        "ova" to "OVAs",
        "ona" to "ONAs",
        "special" to "Specials"
    )

    // Stitch Genres List with appropriate icons
    val genres = listOf(
        GenreData("action", "Action", Icons.Default.Whatshot),
        GenreData("adventure", "Adventure", Icons.Default.Map),
        GenreData("comedy", "Comedy", Icons.Default.SentimentSatisfied),
        GenreData("drama", "Drama", Icons.Default.Favorite),
        GenreData("fantasy", "Fantasy", Icons.Default.AutoFixHigh),
        GenreData("romance", "Romance", Icons.Default.Favorite),
        GenreData("sci-fi", "Sci-Fi", Icons.Default.RocketLaunch),
        GenreData("slice-of-life", "Slice of Life", Icons.Default.CalendarMonth),
        GenreData("supernatural", "Supernatural", Icons.Default.Thunderstorm),
        GenreData("thriller", "Thriller", Icons.Default.Warning)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundVoid)
    ) {
        // Stitch Top Tab Row
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = BackgroundVoid,
            contentColor = Color.White,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = CrunchyrollOrange
                    )
                }
            },
            divider = {
                HorizontalDivider(color = SurfaceDarkVariant, thickness = 1.dp)
            }
        ) {
            topTabs.forEachIndexed { index, title ->
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
                            maxLines = 1,
                            softWrap = false,
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
                    // All Anime Tab
                    AllAnimeContent(
                        selectedCategory = selectedCategory,
                        selectedGenre = selectedGenre,
                        uiState = uiState,
                        categories = categories,
                        onCategoryChange = { viewModel.onCategoryChange(it) },
                        onGenreChange = { viewModel.onGenreChange(it) },
                        onAnimeClick = onAnimeClick,
                        onLoadMore = { viewModel.loadNextPage() }
                    )
                }
                1 -> {
                    // Seasonal Tab
                    SeasonalScreen(
                        onAnimeClick = onAnimeClick,
                        onSearchClick = onSearchClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                2 -> {
                    // Simulcasts Tab
                    ScheduleScreen(
                        onAnimeClick = onAnimeClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                3 -> {
                    // Anime Genres Tab
                    GenresTabContent(
                        genres = genres,
                        onGenreClick = { genreKey ->
                            viewModel.onGenreChange(genreKey)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAnimeContent(
    selectedCategory: String,
    selectedGenre: String?,
    uiState: BrowseUiState,
    categories: List<Pair<String, String>>,
    onCategoryChange: (String) -> Unit,
    onGenreChange: (String?) -> Unit,
    onAnimeClick: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()

    // Detect scrolling boundary for infinite paging
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleItem != null && lastVisibleItem.index >= totalItems - 4
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Categories horizontal list
        ScrollableTabRow(
            selectedTabIndex = categories.indexOfFirst { it.first == selectedCategory }.coerceAtLeast(0),
            containerColor = SurfaceDark,
            contentColor = Color.White,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                val activeIndex = categories.indexOfFirst { it.first == selectedCategory }.coerceAtLeast(0)
                if (activeIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeIndex]),
                        color = CrunchyrollOrange
                    )
                }
            }
        ) {
            categories.forEachIndexed { index, (key, display) ->
                Tab(
                    selected = selectedCategory == key,
                    onClick = { onCategoryChange(key) },
                    text = { Text(display, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected Genre filter chip (for quick resetting)
        if (selectedGenre != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = true,
                    onClick = { onGenreChange(null) },
                    label = { Text("Genre: ${selectedGenre.replaceFirstChar { it.uppercase() }} ✕") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrunchyrollOrange,
                        selectedLabelColor = Color.White
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            when (uiState) {
                is BrowseUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CrunchyrollOrange)
                    }
                }
                is BrowseUiState.Success -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(uiState.results, key = { it.id }) { anime ->
                            AnimeCard(anime = anime, onClick = onAnimeClick)
                        }
                        if (uiState.hasNextPage) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = CrunchyrollOrange, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
                is BrowseUiState.Empty -> {
                    BrowseEmptyState(onReset = { onGenreChange(null) })
                }
                is BrowseUiState.Error -> {
                    BrowseErrorState(message = uiState.message, onRetry = { onCategoryChange(selectedCategory) })
                }
            }
        }
    }
}

@Composable
fun GenresTabContent(
    genres: List<GenreData>,
    onGenreClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(genres.distinctBy { it.key }, key = { it.key }) { genre ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clickable { onGenreClick(genre.key) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, SurfaceDarkVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = genre.icon,
                            contentDescription = null,
                            tint = CrunchyrollOrange,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = genre.display.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

data class GenreData(
    val key: String,
    val display: String,
    val icon: ImageVector
)

@Composable
fun BrowseEmptyState(onReset: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = null,
                tint = Color.DarkGray,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Anime Found",
                fontSize = 18.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "No titles found matching this genre in this category. Try resetting the genre filter.",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
            ) {
                Text("Show All Genres")
            }
        }
    }
}

@Composable
fun BrowseErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = NetflixRed,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Browse Request Failed",
                fontSize = 18.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
            ) {
                Text("Retry")
            }
        }
    }
}


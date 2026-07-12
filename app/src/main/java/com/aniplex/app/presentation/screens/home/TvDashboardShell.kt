package com.aniplex.app.presentation.screens.home

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniplex.app.domain.model.Anime
import com.aniplex.app.presentation.screens.search.SearchUiState
import com.aniplex.app.presentation.screens.search.SearchViewModel
import com.aniplex.app.presentation.screens.watchlist.WatchlistUiState
import com.aniplex.app.presentation.screens.watchlist.WatchlistViewModel
import com.aniplex.app.theme.BackgroundVoid
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.SurfaceDark
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.animation.core.animateFloatAsState
import com.aniplex.app.presentation.screens.watchlist.WatchlistStatusFilter

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import com.aniplex.app.presentation.screens.profile.ProfileViewModel
import com.aniplex.app.presentation.screens.history.HistoryViewModel
import com.aniplex.app.presentation.screens.history.HistoryUiState
import com.aniplex.app.domain.model.HistoryItem

enum class TvTab(val title: String, val icon: ImageVector) {
    DISCOVER("Discover", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
    LIBRARY("Library", Icons.Default.Bookmark),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvDashboardShell(
    onAnimeClick: (String) -> Unit,
    onEpisodeClick: (String, String, String, Int, String, Long) -> Unit,
    onSwitchProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(TvTab.DISCOVER) }
    var isSidebarFocused by remember { mutableStateOf(false) }
    val sidebarWidth by animateDpAsState(if (isSidebarFocused) 220.dp else 72.dp)

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

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundVoid)
    ) {
        // Collapsible/Expandable Navigation Sidebar
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(sidebarWidth)
                .background(SurfaceDark.copy(alpha = 0.95f))
                .onFocusChanged { state ->
                    isSidebarFocused = state.hasFocus
                }
                .padding(vertical = 24.dp, horizontal = 8.dp),
            horizontalAlignment = if (isSidebarFocused) Alignment.Start else Alignment.CenterHorizontally
        ) {
            // App Title Logo
            if (isSidebarFocused) {
                Text(
                    text = "ANIPLEX",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = CrunchyrollOrange,
                    modifier = Modifier.padding(start = 16.dp, bottom = 32.dp)
                )
            } else {
                Text(
                    text = "A",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = CrunchyrollOrange,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Items
            TvTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                var isItemFocused by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .onFocusChanged { isItemFocused = it.isFocused }
                        .clickable {
                            selectedTab = tab
                        }
                        .background(
                            when {
                                isItemFocused -> CrunchyrollOrange
                                isSelected -> SurfaceDark
                                else -> Color.Transparent
                            }
                        )
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isSidebarFocused) Arrangement.Start else Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (isItemFocused || isSelected) Color.White else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    if (isSidebarFocused) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = tab.title,
                            color = if (isItemFocused || isSelected) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides tvBringIntoViewSpec) {
                when (selectedTab) {
                    TvTab.DISCOVER -> {
                        TvHomeScreen(
                            onAnimeClick = onAnimeClick,
                            onEpisodeClick = onEpisodeClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    TvTab.SEARCH -> {
                        TvSearchContent(
                            onAnimeClick = onAnimeClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    TvTab.LIBRARY -> {
                        TvLibraryContent(
                            onAnimeClick = onAnimeClick,
                            onEpisodeClick = onEpisodeClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    TvTab.SETTINGS -> {
                        TvSettingsContent(
                            onSwitchProfile = onSwitchProfile,
                            onSignOut = onSignOut,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvSearchContent(
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Search Anime",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var isSearchFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    viewModel.onQueryChange(it)
                    viewModel.performSearch()
                },
                placeholder = { Text("Enter anime name...", color = Color.Gray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.performSearch()
                        focusManager.clearFocus()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = CrunchyrollOrange,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isSearchFocused = it.isFocused }
            )

            // D-Pad focusable Filter Button
            var isFilterBtnFocused by remember { mutableStateOf(false) }
            var showFiltersDialog by remember { mutableStateOf(false) }
            Button(
                onClick = { showFiltersDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = if (isFilterBtnFocused) CrunchyrollOrange else SurfaceDark),
                modifier = Modifier
                    .height(56.dp)
                    .onFocusChanged { isFilterBtnFocused = it.isFocused }
            ) {
                Icon(Icons.Default.FilterList, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Filters", color = Color.White)
            }

            if (showFiltersDialog) {
                TvSearchFiltersDialog(
                    viewModel = viewModel,
                    onDismiss = { showFiltersDialog = false }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Search Results
        Box(modifier = Modifier.weight(1f)) {
            when (val state = uiState) {
                is SearchUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CrunchyrollOrange)
                    }
                }
                is SearchUiState.Success -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.results, key = { it.id }) { anime ->
                            TvAnimeCard(
                                anime = anime,
                                onFocus = {},
                                onClick = { onAnimeClick(anime.id) }
                            )
                        }
                    }
                }
                is SearchUiState.Empty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No results found.", color = Color.LightGray)
                    }
                }
                is SearchUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = Color.Red)
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Search for your favorite anime series or movies.", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvSearchFiltersDialog(
    viewModel: SearchViewModel,
    onDismiss: () -> Unit
) {
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Search Filters", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        containerColor = Color(0xFF0F0F14),
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                // 1. Type filter
                Text("Type", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("tv", "movie", "ova").forEach { type ->
                        val isSelected = selectedType == type
                        var isFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable {
                                    viewModel.selectedType.value = if (isSelected) null else type
                                }
                                .background(
                                    color = if (isSelected) CrunchyrollOrange else if (isFocused) SurfaceDark else Color(0xFF22222A),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = if (isFocused) Color.White else if (isSelected) Color.Transparent else Color(0xFF33333E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(type.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 2. Sort option
                Text("Sort By", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sorts = listOf(
                        "recently_updated" to "Latest",
                        "most_popular" to "Popularity",
                        "score" to "Score"
                    )
                    sorts.forEach { (sortKey, label) ->
                        val isSelected = selectedSort == sortKey
                        var isFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable {
                                    viewModel.selectedSort.value = if (isSelected) null else sortKey
                                }
                                .background(
                                    color = if (isSelected) CrunchyrollOrange else if (isFocused) SurfaceDark else Color(0xFF22222A),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = if (isFocused) Color.White else if (isSelected) Color.Transparent else Color(0xFF33333E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 3. Language options
                Text("Audio Language", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("subbed", "dubbed").forEach { lang ->
                        val isSelected = selectedLanguage == lang
                        var isFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable {
                                    viewModel.selectedLanguage.value = if (isSelected) null else lang
                                }
                                .background(
                                    color = if (isSelected) CrunchyrollOrange else if (isFocused) SurfaceDark else Color(0xFF22222A),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = if (isFocused) Color.White else if (isSelected) Color.Transparent else Color(0xFF33333E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(lang.replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            var isConfirmFocused by remember { mutableStateOf(false) }
            TextButton(
                onClick = {
                    viewModel.performSearch()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = if (isConfirmFocused) CrunchyrollOrange else Color.White),
                modifier = Modifier.onFocusChanged { isConfirmFocused = it.isFocused }
            ) {
                Text("Apply Filters", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            var isDismissFocused by remember { mutableStateOf(false) }
            TextButton(
                onClick = {
                    viewModel.clearFilters()
                    viewModel.performSearch()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = if (isDismissFocused) CrunchyrollOrange else Color.LightGray),
                modifier = Modifier.onFocusChanged { isDismissFocused = it.isFocused }
            ) {
                Text("Clear All")
            }
        }
    )
}

@Composable
fun TvLibraryContent(
    onAnimeClick: (String) -> Unit,
    onEpisodeClick: (String, String, String, Int, String, Long) -> Unit,
    modifier: Modifier = Modifier,
    watchlistViewModel: WatchlistViewModel = hiltViewModel(),
    historyViewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by watchlistViewModel.watchlistState.collectAsStateWithLifecycle()
    val statusFilter by watchlistViewModel.statusFilter.collectAsStateWithLifecycle()
    val historyState by historyViewModel.historyState.collectAsStateWithLifecycle()

    var selectedSubTab by remember { mutableStateOf(0) }
    val subTabs = listOf("Watchlist", "History")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Tab Row at top of Library
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = BackgroundVoid,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = CrunchyrollOrange
                )
            },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            subTabs.forEachIndexed { index, title ->
                var isTabFocused by remember { mutableStateOf(false) }
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    modifier = Modifier
                        .onFocusChanged { isTabFocused = it.isFocused }
                        .padding(vertical = 8.dp),
                    text = {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedSubTab == index || isTabFocused) Color.White else Color.Gray
                        )
                    }
                )
            }
        }

        if (selectedSubTab == 0) {
            // Watchlist Status Filter Chips Row for TV
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                WatchlistStatusFilter.values().forEach { filter ->
                    val isSelected = filter == statusFilter
                    val filterText = when (filter) {
                        WatchlistStatusFilter.ALL -> "All Shows"
                        WatchlistStatusFilter.WATCHING -> "Watching"
                        WatchlistStatusFilter.PLANNING -> "Plan to Watch"
                        WatchlistStatusFilter.COMPLETED -> "Completed"
                    }

                    var isFocused by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f)

                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { watchlistViewModel.setStatusFilter(filter) }
                            .background(
                                color = if (isSelected) CrunchyrollOrange else if (isFocused) SurfaceDark else Color(0xFF14141A),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) Color.White else if (isSelected) Color.Transparent else Color(0xFF2C2C35),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filterText,
                            color = if (isSelected || isFocused) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is WatchlistUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CrunchyrollOrange)
                        }
                    }
                    is WatchlistUiState.Success -> {
                        val list = state.list
                        if (list.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = "Your watchlist is empty under this filter.", color = Color.LightGray)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(5),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(list, key = { it.id }) { item ->
                                    val anime = Anime(
                                        id = item.id,
                                        title = item.title,
                                        poster = item.poster,
                                        type = ""
                                    )
                                    TvAnimeCard(
                                        anime = anime,
                                        onFocus = {},
                                        onClick = { onAnimeClick(anime.id) }
                                    )
                                }
                            }
                        }
                    }
                    is WatchlistUiState.Empty -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "Your watchlist is empty.", color = Color.LightGray)
                        }
                    }
                    is WatchlistUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = state.message, color = Color.Red)
                        }
                    }
                }
            }
        } else {
            // History Tab Content
            Box(modifier = Modifier.weight(1f)) {
                TvHistoryGrid(
                    historyState = historyState,
                    defaultAudioCategory = remember { historyViewModel.defaultAudioCategory },
                    onEpisodeClick = onEpisodeClick,
                    onRemove = { historyViewModel.removeFromHistory(it) }
                )
            }
        }
    }
}

@Composable
fun TvHistoryGrid(
    historyState: HistoryUiState,
    defaultAudioCategory: String,
    onEpisodeClick: (String, String, String, Int, String, Long) -> Unit,
    onRemove: (String) -> Unit
) {
    when (historyState) {
        is HistoryUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CrunchyrollOrange)
            }
        }
        is HistoryUiState.Success -> {
            val list = historyState.list
            if (list.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No watch history found.", color = Color.LightGray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list, key = { it.animeId }) { item ->
                        var isFocused by remember { mutableStateOf(false) }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isFocused) CrunchyrollOrange else SurfaceDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .onFocusChanged { isFocused = it.isFocused }
                                .scale(if (isFocused) 1.05f else 1.0f)
                                .clickable {
                                    onEpisodeClick(
                                        item.episodeId,
                                        item.animeId,
                                        item.animeTitle,
                                        item.episodeNumber,
                                        defaultAudioCategory,
                                        item.progressPosition
                                    )
                                }
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                AsyncImage(
                                    model = item.poster,
                                    contentDescription = item.animeTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.animeTitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Episode ${item.episodeNumber}",
                                    fontSize = 11.sp,
                                    color = if (isFocused) Color.White else Color.LightGray
                                )
                                // Linear Progress bar for watched position
                                val progress = if (item.totalDuration > 0) item.progressPosition.toFloat() / item.totalDuration.toFloat() else 0f
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = CrunchyrollOrange,
                                    trackColor = Color.DarkGray,
                                    modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 4.dp).clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
        is HistoryUiState.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No watch history found.", color = Color.LightGray)
            }
        }
        is HistoryUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(historyState.message, color = Color.Red)
            }
        }
    }
}

@Composable
fun TvSettingsContent(
    onSwitchProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val audioCategory by viewModel.defaultAudioCategory.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplayNextEpisode.collectAsStateWithLifecycle()
    val preferredQuality by viewModel.preferredQuality.collectAsStateWithLifecycle()
    val skipIntro by viewModel.skipIntro.collectAsStateWithLifecycle()
    val skipOutro by viewModel.skipOutro.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 1. Audio Option Settings
        TvSettingsRow(title = "Default Audio Language") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("SUB", "DUB").forEach { option ->
                    val isSelected = audioCategory == option
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { viewModel.setDefaultAudioCategory(option) }
                            .background(
                                color = if (isSelected) CrunchyrollOrange else if (isFocused) SurfaceDark else Color(0xFF14141A),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) Color.White else if (isSelected) Color.Transparent else Color(0xFF2C2C35),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Autoplay Option
        TvSettingsRow(title = "Autoplay Next Episode") {
            var isFocused by remember { mutableStateOf(false) }
            Switch(
                checked = autoplay,
                onCheckedChange = { viewModel.setAutoplayNextEpisode(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CrunchyrollOrange,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = SurfaceDark
                ),
                modifier = Modifier
                    .onFocusChanged { isFocused = it.isFocused }
                    .border(
                        width = if (isFocused) 2.dp else 0.dp,
                        color = if (isFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    )
            )
        }

        // 3. Skip Intro / Skip Outro Options
        TvSettingsRow(title = "Auto Skip Intro & Outro") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var isFocusedIntro by remember { mutableStateOf(false) }
                    Checkbox(
                        checked = skipIntro,
                        onCheckedChange = { viewModel.setSkipIntro(it) },
                        colors = CheckboxDefaults.colors(checkedColor = CrunchyrollOrange, checkmarkColor = Color.White),
                        modifier = Modifier
                            .onFocusChanged { isFocusedIntro = it.isFocused }
                            .border(width = if (isFocusedIntro) 2.dp else 0.dp, color = if (isFocusedIntro) Color.White else Color.Transparent)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Skip Intro", color = Color.LightGray, fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    var isFocusedOutro by remember { mutableStateOf(false) }
                    Checkbox(
                        checked = skipOutro,
                        onCheckedChange = { viewModel.setSkipOutro(it) },
                        colors = CheckboxDefaults.colors(checkedColor = CrunchyrollOrange, checkmarkColor = Color.White),
                        modifier = Modifier
                            .onFocusChanged { isFocusedOutro = it.isFocused }
                            .border(width = if (isFocusedOutro) 2.dp else 0.dp, color = if (isFocusedOutro) Color.White else Color.Transparent)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Skip Outro", color = Color.LightGray, fontSize = 13.sp)
                }
            }
        }

        // 4. Quality Settings
        TvSettingsRow(title = "Preferred Video Quality") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("1080p", "720p", "480p").forEach { qualityOption ->
                    val isSelected = preferredQuality == qualityOption
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { viewModel.setPreferredQuality(qualityOption) }
                            .background(
                                color = if (isSelected) CrunchyrollOrange else if (isFocused) SurfaceDark else Color(0xFF14141A),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) Color.White else if (isSelected) Color.Transparent else Color(0xFF2C2C35),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(qualityOption, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        // 4.1. Preferred Streaming Provider
        val preferredProvider by viewModel.preferredProvider.collectAsStateWithLifecycle()
        TvSettingsRow(title = "Preferred Streaming Provider") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("zoro" to "Zoro (HD-1)", "gogoanime" to "Gogoanime (RapidCloud)").forEach { (providerId, providerLabel) ->
                    val isSelected = preferredProvider == providerId
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { viewModel.setPreferredProvider(providerId) }
                            .background(
                                color = if (isSelected) CrunchyrollOrange else if (isFocused) SurfaceDark else Color(0xFF14141A),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) Color.White else if (isSelected) Color.Transparent else Color(0xFF2C2C35),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(providerLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4.5. App Updates Settings
        var isCheckingUpdates by remember { mutableStateOf(false) }
        val updateViewModel: com.aniplex.app.presentation.screens.update.UpdateViewModel = hiltViewModel()

        TvSettingsRow(title = "App Updates") {
            var isFocused by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f)
            Box(
                modifier = Modifier
                    .scale(scale)
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable {
                        if (!isCheckingUpdates) {
                            isCheckingUpdates = true
                            updateViewModel.checkForUpdates { info ->
                                isCheckingUpdates = false
                                if (info == null) {
                                    Toast.makeText(context, "Your app is up to date.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .background(
                        color = if (isFocused) SurfaceDark else Color(0xFF14141A),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) Color.White else Color(0xFF2C2C35),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isCheckingUpdates) "Checking for updates..." else "Check for Updates",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5. Account & Profile Actions (Switch Profile, Logout)
        HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            var isProfileFocused by remember { mutableStateOf(false) }
            Button(
                onClick = onSwitchProfile,
                colors = ButtonDefaults.buttonColors(containerColor = if (isProfileFocused) CrunchyrollOrange else SurfaceDark),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isProfileFocused = it.isFocused }
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Switch Profile", color = Color.White)
            }

            var isLogoutFocused by remember { mutableStateOf(false) }
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = if (isLogoutFocused) CrunchyrollOrange else SurfaceDark),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isLogoutFocused = it.isFocused }
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", color = Color.White)
            }
        }
    }
}

@Composable
fun TvSettingsRow(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        content()
    }
}


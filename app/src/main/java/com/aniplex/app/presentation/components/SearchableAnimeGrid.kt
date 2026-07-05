package com.aniplex.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aniplex.app.domain.model.Anime
import com.aniplex.app.presentation.screens.search.SearchUiState
import com.aniplex.app.presentation.screens.search.SearchViewModel
import com.aniplex.app.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableAnimeGrid(
    onAnimeClick: (String) -> Unit,
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
    showMainFilters: Boolean = true
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()

    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val selectedGenres by viewModel.selectedGenres.collectAsStateWithLifecycle()

    var showFilterSheet by remember { mutableStateOf(false) }
    var isSearchFieldFocused by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current

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
            viewModel.loadNextPage()
        }
    }

    // Determine grid columns dynamically based on screen configurations (Responsive Grid)
    val columns = remember(configuration.screenWidthDp) {
        if (configuration.screenWidthDp > 840) {
            GridCells.Adaptive(minSize = 170.dp) // 5+ columns on wide screens / TV / tablets
        } else if (configuration.screenWidthDp > 600) {
            GridCells.Adaptive(minSize = 150.dp) // 3-4 columns on medium foldables / tablets
        } else {
            GridCells.Fixed(2) // 2 columns on standard compact mobile
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundVoid)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Search input field with glow border & premium interactive indicators
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    viewModel.onQueryChange(it)
                    isSearchFieldFocused = true
                },
                placeholder = { Text("Search by title, character, studio...", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = if (isSearchFieldFocused) CrunchyrollOrange else Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    Row(
                        modifier = Modifier.padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onQueryChange("") },
                                modifier = Modifier.testTag("search_clear_button").size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear query",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (showMainFilters) {
                            IconButton(
                                onClick = { showFilterSheet = true },
                                modifier = Modifier.testTag("search_filters_button").size(36.dp)
                            ) {
                                val activeFiltersCount = (if (selectedType != null) 1 else 0) +
                                        (if (selectedStatus != null) 1 else 0) +
                                        (if (selectedSort != null) 1 else 0) +
                                        (if (selectedLanguage != null) 1 else 0) +
                                        selectedGenres.size
                                BadgedBox(
                                    badge = {
                                        if (activeFiltersCount > 0) {
                                            Badge(
                                                containerColor = CrunchyrollOrange,
                                                contentColor = Color.White
                                            ) {
                                                Text(activeFiltersCount.toString(), fontSize = 9.sp)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filters sheet",
                                        tint = if (activeFiltersCount > 0) CrunchyrollOrange else Color.LightGray,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.performSearch()
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        isSearchFieldFocused = false
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrunchyrollOrange,
                    unfocusedBorderColor = SurfaceDarkVariant,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input_field")
                    .border(
                        border = BorderStroke(
                            width = 1.dp,
                            brush = if (isSearchFieldFocused) {
                                Brush.horizontalGradient(listOf(CrunchyrollOrange, Color(0xFFFF9F1C)))
                            } else {
                                Brush.horizontalGradient(listOf(SurfaceDarkVariant, SurfaceDarkVariant))
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Recent Searches Chips Panel
            if (recentSearches.isNotEmpty() && searchQuery.isBlank()) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Searches",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Clear All",
                            color = CrunchyrollOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .testTag("clear_recent_searches_button")
                                .clickable { viewModel.clearRecentSearches() }
                                .padding(4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentSearches.size) { index ->
                            val query = recentSearches[index]
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceDark)
                                    .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.onQueryChange(query)
                                        viewModel.performSearch()
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = query,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove search reference",
                                        tint = Color.Gray,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { viewModel.removeRecentSearch(query) }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Central content block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (val state = uiState) {
                    is SearchUiState.Idle -> {
                        SearchComponentIdleState(
                            onGenreClick = { genre ->
                                viewModel.toggleGenre(genre)
                                viewModel.performSearch()
                            }
                        )
                    }
                    is SearchUiState.Loading -> {
                        SearchComponentShimmerLoader(columns)
                    }
                    is SearchUiState.Success -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (state.fallbackType == "anilist") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = CrunchyrollOrange.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, CrunchyrollOrange.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Warning",
                                            tint = CrunchyrollOrange,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Search server is experiencing downtime. Serving results via AniList backup.",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            LazyVerticalGrid(
                                columns = columns,
                                state = gridState,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("search_results_grid")
                            ) {
                                items(state.results.size) { index ->
                                    val anime = state.results[index]
                                    AnimeCard(
                                        anime = anime,
                                        onClick = { animeId ->
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            viewModel.recordSearchQuery(searchQuery)
                                            onAnimeClick(animeId)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (state.hasNextPage) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                    }
                    is SearchUiState.Empty -> {
                        SearchComponentEmptyState(
                            query = searchQuery,
                            selectedType = selectedType,
                            selectedStatus = selectedStatus,
                            selectedLanguage = selectedLanguage,
                            selectedGenres = selectedGenres,
                            onClearFilters = {
                                viewModel.clearFilters()
                                viewModel.onQueryChange("")
                                viewModel.performSearch()
                            }
                        )
                    }
                    is SearchUiState.Error -> {
                        SearchComponentErrorState(
                            message = state.message,
                            onRetry = { viewModel.performSearch() }
                        )
                    }
                }

                // Auto suggestions glassmorphic drop down selection
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSearchFieldFocused && suggestions.isNotEmpty() && searchQuery.length >= 2,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.98f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(suggestions.size) { idx ->
                                val item = suggestions[idx]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            isSearchFieldFocused = false
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            viewModel.recordSearchQuery(searchQuery)
                                            onAnimeClick(item.id)
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = item.poster,
                                        contentDescription = "Suggestion art",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${item.type} • ${item.duration}",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Go to anime details",
                                        tint = CrunchyrollOrange.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filters dialog / Bottom sheet
        if (showFilterSheet && showMainFilters) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                containerColor = SurfaceDark,
                contentColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Search Filters",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { viewModel.clearFilters() },
                            colors = ButtonDefaults.textButtonColors(contentColor = CrunchyrollOrange)
                        ) {
                            Text("Reset All", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Type
                        item {
                            FilterComponentSectionTitle("Media Type")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val types = listOf("tv", "movie", "ova", "ona", "special")
                                OptGroupRowChips(
                                    items = types,
                                    selectedItem = selectedType,
                                    onSelect = { selectedVal -> viewModel.selectedType.value = selectedVal }
                                )
                            }
                        }

                        // Status
                        item {
                            FilterComponentSectionTitle("Release Status")
                            val statuses = mapOf(
                                "currently-airing" to "Airing",
                                "finished-airing" to "Finished",
                                "not-yet-aired" to "Upcoming"
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                statuses.forEach { (key, display) ->
                                    FilterChip(
                                        selected = selectedStatus == key,
                                        onClick = { viewModel.selectedStatus.value = if (selectedStatus == key) null else key },
                                        label = { Text(display) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CrunchyrollOrange,
                                            selectedLabelColor = Color.White,
                                            containerColor = SurfaceDarkVariant,
                                            labelColor = Color.LightGray
                                        )
                                    )
                                }
                            }
                        }

                        // Language
                        item {
                            FilterComponentSectionTitle("Audio / Dubbing")
                            val langs = mapOf("sub" to "Sub", "dub" to "Dub", "sub-dub" to "Multi")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                langs.forEach { (key, display) ->
                                    FilterChip(
                                        selected = selectedLanguage == key,
                                        onClick = { viewModel.selectedLanguage.value = if (selectedLanguage == key) null else key },
                                        label = { Text(display) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CrunchyrollOrange,
                                            selectedLabelColor = Color.White,
                                            containerColor = SurfaceDarkVariant,
                                            labelColor = Color.LightGray
                                        )
                                    )
                                }
                            }
                        }

                        // Sort
                        item {
                            FilterComponentSectionTitle("Sort Ordering")
                            val sorts = mapOf(
                                "default" to "Default",
                                "recently-added" to "Newest Added",
                                "most-popular" to "Most Popular",
                                "alphabetical" to "A to Z"
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            ) {
                                sorts.forEach { (key, display) ->
                                    FilterChip(
                                        selected = selectedSort == key,
                                        onClick = { viewModel.selectedSort.value = if (selectedSort == key) null else key },
                                        label = { Text(display) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CrunchyrollOrange,
                                            selectedLabelColor = Color.White,
                                            containerColor = SurfaceDarkVariant,
                                            labelColor = Color.LightGray
                                        )
                                    )
                                }
                            }
                        }

                        // Genres
                        item {
                            FilterComponentSectionTitle("Genres")
                            val genres = listOf(
                                "action", "adventure", "comedy", "drama", "fantasy",
                                "romance", "sci-fi", "slice-of-life", "supernatural", "thriller"
                            )
                            OptFlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                genres.forEach { genre ->
                                    val isSelected = selectedGenres.contains(genre)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.toggleGenre(genre) },
                                        label = { Text(genre.replace("-", " ").replaceFirstChar { it.uppercase() }) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CrunchyrollOrange,
                                            selectedLabelColor = Color.White,
                                            containerColor = SurfaceDarkVariant,
                                            labelColor = Color.LightGray
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            showFilterSheet = false
                            viewModel.performSearch()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("APPLY SEARCH FILTERS", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun OptGroupRowChips(
    items: List<String>,
    selectedItem: String?,
    onSelect: (String?) -> Unit
) {
    items.forEach { item ->
        FilterChip(
            selected = selectedItem == item,
            onClick = { onSelect(if (selectedItem == item) null else item) },
            label = { Text(item.uppercase()) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = CrunchyrollOrange,
                selectedLabelColor = Color.White,
                containerColor = SurfaceDarkVariant,
                labelColor = Color.LightGray
            )
        )
    }
}

@Composable
fun FilterComponentSectionTitle(title: String) {
    Text(
        text = title,
        color = Color.LightGray,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun SearchComponentIdleState(onGenreClick: (String) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = CrunchyrollOrange.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(90.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Discover Anime",
                fontSize = 18.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Find your next favorite anime by titles, genres, or schedule tags.",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "POPULAR CATEGORIES",
                color = CrunchyrollOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val popularGenres = listOf("action", "comedy", "fantasy", "sci-fi", "romance", "thriller")
            OptFlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                popularGenres.forEach { genre ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .background(SurfaceDark, RoundedCornerShape(18.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                            .clickable { onGenreClick(genre) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = genre.replaceFirstChar { it.uppercase() },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchComponentEmptyState(
    query: String,
    selectedType: String?,
    selectedStatus: String?,
    selectedLanguage: String?,
    selectedGenres: Set<String>,
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = NetflixRed.copy(alpha = 0.8f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Titles Found",
                fontSize = 18.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            val filterTexts = remember { mutableStateListOf<String>() }
            LaunchedEffect(selectedType, selectedLanguage, selectedGenres) {
                filterTexts.clear()
                selectedType?.let { filterTexts.add(it.uppercase()) }
                selectedLanguage?.let { filterTexts.add(it.uppercase()) }
                if (selectedGenres.isNotEmpty()) {
                    filterTexts.add("${selectedGenres.size} genres")
                }
            }

            val messageText = if (query.isNotBlank()) {
                if (filterTexts.isNotEmpty()) {
                    "No matching results in ${filterTexts.joinToString(", ")} for \"$query\""
                } else {
                    "No results found for \"$query\""
                }
            } else {
                "No anime matches the current filter setup."
            }

            Text(
                text = messageText,
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onClearFilters,
                colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Reset Filters & View All", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SearchComponentErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = NetflixRed,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Failed to Fetch Anime",
                fontSize = 18.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Retry Connection", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SearchComponentShimmerLoader(columns: GridCells) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LazyVerticalGrid(
        columns = columns,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        items(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDarkVariant.copy(alpha = alphaAnim))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.08f * alphaAnim))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.05f * alphaAnim))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptFlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}

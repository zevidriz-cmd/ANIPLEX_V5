package com.aniplex.app.presentation.screens.seasonal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import java.util.Calendar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aniplex.app.domain.model.Anime
import com.aniplex.app.presentation.components.AnimeCard
import com.aniplex.app.presentation.components.AnimeCardShimmer
import com.aniplex.app.theme.BackgroundVoid
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.SurfaceDark
import com.aniplex.app.theme.TextMuted
import com.aniplex.app.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonalScreen(
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeasonalViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resolvingState by viewModel.resolvingState.collectAsStateWithLifecycle()

    val selectedSeason by viewModel.selectedSeason.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()

    val seasonsList = listOf("winter" to "Winter", "spring" to "Spring", "summer" to "Summer", "fall" to "Fall")
    val yearsList = remember {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        (currentYear + 1 downTo 2016).toList()
    }

    var yearDropdownExpanded by remember { mutableStateOf(false) }

    // Resolution Overlay Dialog
    if (resolvingState is ResolvingState.Loading) {
        val msg = (resolvingState as ResolvingState.Loading).message
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp, 150.dp)
                    .background(SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CrunchyrollOrange)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Resolving Stream Source",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundVoid)
    ) {
        // Controls Row: Seasons tabs + Year selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Season Tabs Selector
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(2.dp)
            ) {
                seasonsList.forEach { (id, label) ->
                    val isSelected = selectedSeason == id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) CrunchyrollOrange else Color.Transparent)
                            .clickable {
                                if (selectedSeason != id) {
                                    viewModel.changeFilters(id, selectedYear)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Year selector Dropdown
            Box {
                Button(
                    onClick = { yearDropdownExpanded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceDark,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(text = selectedYear.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                DropdownMenu(
                    expanded = yearDropdownExpanded,
                    onDismissRequest = { yearDropdownExpanded = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    yearsList.forEach { y ->
                        DropdownMenuItem(
                            text = { Text(text = y.toString(), color = Color.White) },
                            onClick = {
                                yearDropdownExpanded = false
                                if (selectedYear != y) {
                                    viewModel.changeFilters(selectedSeason, y)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Seasonal content
        when (val state = uiState) {
            is SeasonalUiState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(9) {
                        AnimeCardShimmer()
                    }
                }
            }
            is SeasonalUiState.Success -> {
                val results = state.data.animes
                val topAnime = remember(results) {
                    results.sortedByDescending { it.rate.toDoubleOrNull() ?: 0.0 }.firstOrNull()
                }

                if (results.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(text = "No seasonal anime found", color = TextMuted)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        // 1. Featured Top-Rated Hero Banner (Only on Page 1)
                        if (page == 1 && topAnime != null) {
                            item(span = { GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceDark)
                                        .clickable {
                                            viewModel.resolveAnime(
                                                anime = topAnime,
                                                onSuccess = { onAnimeClick(it) },
                                                onFailure = {
                                                    Toast.makeText(context, "Streaming source not resolved. Opening search...", Toast.LENGTH_SHORT).show()
                                                    onSearchClick()
                                                }
                                            )
                                        }
                                ) {
                                    // Blurred cover backdrop
                                    AsyncImage(
                                        model = topAnime.poster,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        alpha = 0.15f
                                    )

                                    // Content layout
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(CrunchyrollOrange, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "TOP RATED • ${selectedSeason.uppercase()} $selectedYear",
                                                    color = Color.Black,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }

                                            Text(
                                                text = topAnime.title,
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 18.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(text = topAnime.type, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(text = "•", color = TextMuted, fontSize = 11.sp)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF5A623), modifier = Modifier.size(12.dp))
                                                    Text(text = topAnime.rate, color = Color(0xFFF5A623), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Text(
                                                text = topAnime.description,
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 15.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        // Mini poster on the right
                                        AsyncImage(
                                            model = topAnime.poster,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(85.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Anime Cards Grid (excluding hero banner on page 1)
                        val gridAnimes = if (page == 1 && topAnime != null) {
                            results.filter { it.id != topAnime.id }
                        } else {
                            results
                        }

                        itemsIndexed(gridAnimes) { _, anime ->
                            AnimeCard(
                                anime = anime,
                                onClick = {
                                    viewModel.resolveAnime(
                                        anime = anime,
                                        onSuccess = { onAnimeClick(it) },
                                        onFailure = {
                                            Toast.makeText(context, "Streaming source not resolved. Opening search...", Toast.LENGTH_SHORT).show()
                                            onSearchClick()
                                        }
                                    )
                                }
                            )
                        }

                        // 3. Paginated Footer controls
                        if (state.data.totalPages > 1) {
                            item(span = { GridItemSpan(3) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Prev button
                                    IconButton(
                                        onClick = { if (page > 1) viewModel.setPage(page - 1) },
                                        enabled = page > 1,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (page > 1) SurfaceDark else Color.Transparent)
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Prev", tint = if (page > 1) Color.White else Color.Gray)
                                    }

                                    Spacer(modifier = Modifier.width(24.dp))

                                    Text(
                                        text = "Page $page of ${state.data.totalPages}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.width(24.dp))

                                    // Next button
                                    IconButton(
                                        onClick = { if (state.data.hasNextPage) viewModel.setPage(page + 1) },
                                        enabled = state.data.hasNextPage,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (state.data.hasNextPage) SurfaceDark else Color.Transparent)
                                    ) {
                                        Icon(Icons.Default.ArrowForward, contentDescription = "Next", tint = if (state.data.hasNextPage) Color.White else Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is SeasonalUiState.Error -> {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Failed to load seasonal anime", color = Color.Red)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = state.message, color = TextMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.fetchSeasonalData() },
                            colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
                        ) {
                            Text(text = "Retry", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

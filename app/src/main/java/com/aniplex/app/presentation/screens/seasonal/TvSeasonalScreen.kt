package com.aniplex.app.presentation.screens.seasonal

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import com.aniplex.app.presentation.components.AnimeCardShimmer
import com.aniplex.app.theme.BackgroundVoid
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.SurfaceDark
import com.aniplex.app.theme.TextMuted
import com.aniplex.app.theme.TextSecondary
import java.util.Calendar

@Composable
fun TvSeasonalScreen(
    onAnimeClick: (String) -> Unit,
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

    var focusedAnimeBackdrop by remember { mutableStateOf<String?>(null) }
    var focusedAnimeTitle by remember { mutableStateOf("") }
    var focusedAnimeDesc by remember { mutableStateOf("") }

    // Stream Resolution Spinner Overlay
    if (resolvingState is ResolvingState.Loading) {
        val msg = (resolvingState as ResolvingState.Loading).message
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp, 160.dp)
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
                        fontSize = 16.sp
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundVoid)
    ) {
        // Spotlight Blurred Backdrop
        focusedAnimeBackdrop?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.12f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, BackgroundVoid.copy(alpha = 0.8f), BackgroundVoid)
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            // Left Column: Filter Controls & Focused Metadata Info Pane
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(end = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Seasonal Anime",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    // TV D-Pad focusable seasons row
                    Column {
                        Text(text = "Season", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(seasonsList) { (id, label) ->
                                val isSelected = selectedSeason == id
                                var isFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { viewModel.changeFilters(id, selectedYear) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            isFocused -> CrunchyrollOrange
                                            isSelected -> SurfaceDark
                                            else -> Color.Transparent
                                        },
                                        contentColor = if (isFocused) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isSelected) CrunchyrollOrange else Color.Gray.copy(alpha = 0.3f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .onFocusChanged { isFocused = it.isFocused }
                                ) {
                                    Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // TV D-Pad focusable years row
                    Column {
                        Text(text = "Year", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            state = rememberLazyListState(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(yearsList) { y ->
                                val isSelected = selectedYear == y
                                var isFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { viewModel.changeFilters(selectedSeason, y) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            isFocused -> CrunchyrollOrange
                                            isSelected -> SurfaceDark
                                            else -> Color.Transparent
                                        },
                                        contentColor = if (isFocused) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isSelected) CrunchyrollOrange else Color.Gray.copy(alpha = 0.3f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .onFocusChanged { isFocused = it.isFocused }
                                ) {
                                    Text(text = y.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Info Panel showing details of currently focused poster card
                if (focusedAnimeTitle.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = focusedAnimeTitle,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = focusedAnimeDesc,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Right Column: Grid Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                when (val state = uiState) {
                    is SeasonalUiState.Loading -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(8) {
                                AnimeCardShimmer()
                            }
                        }
                    }
                    is SeasonalUiState.Success -> {
                        val list = state.data.animes
                        if (list.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = "No seasonal anime found", color = TextMuted)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(list, key = { _, item -> item.id }) { index, anime ->
                                    var isFocused by remember { mutableStateOf(false) }
                                    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f)
                                    val borderStroke = if (isFocused) BorderStroke(3.dp, CrunchyrollOrange) else null

                                    Card(
                                        modifier = Modifier
                                            .width(135.dp)
                                            .height(200.dp)
                                            .scale(scale)
                                            .onFocusChanged {
                                                isFocused = it.isFocused
                                                if (it.isFocused) {
                                                    focusedAnimeBackdrop = anime.poster
                                                    focusedAnimeTitle = anime.title
                                                    focusedAnimeDesc = anime.description
                                                }
                                            }
                                            .clickable {
                                                viewModel.resolveAnime(
                                                    anime = anime,
                                                    onSuccess = { onAnimeClick(it) },
                                                    onFailure = {
                                                        Toast.makeText(context, "Streaming source resolve failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            },
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
                                            
                                            // Top info badge (Rating / Score)
                                            if (anime.rate.isNotBlank()) {
                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF5A623), modifier = Modifier.size(10.dp))
                                                    Text(text = anime.rate, color = Color(0xFFF5A623), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Bottom info title overlay
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomCenter)
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                                        )
                                                    )
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = anime.title,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. TV Paginated footer row
                                if (state.data.totalPages > 1) {
                                    item(span = { GridItemSpan(4) }) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 24.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            var isPrevFocused by remember { mutableStateOf(false) }
                                            Button(
                                                onClick = { if (page > 1) viewModel.setPage(page - 1) },
                                                enabled = page > 1,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isPrevFocused) CrunchyrollOrange else SurfaceDark,
                                                    contentColor = if (isPrevFocused) Color.Black else Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.onFocusChanged { isPrevFocused = it.isFocused }
                                            ) {
                                                Text("Prev")
                                            }

                                            Spacer(modifier = Modifier.width(32.dp))

                                            Text(
                                                text = "Page $page of ${state.data.totalPages}",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Spacer(modifier = Modifier.width(32.dp))

                                            var isNextFocused by remember { mutableStateOf(false) }
                                            Button(
                                                onClick = { if (state.data.hasNextPage) viewModel.setPage(page + 1) },
                                                enabled = state.data.hasNextPage,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isNextFocused) CrunchyrollOrange else SurfaceDark,
                                                    contentColor = if (isNextFocused) Color.Black else Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.onFocusChanged { isNextFocused = it.isFocused }
                                            ) {
                                                Text("Next")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is SeasonalUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = state.message, color = Color.Red, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

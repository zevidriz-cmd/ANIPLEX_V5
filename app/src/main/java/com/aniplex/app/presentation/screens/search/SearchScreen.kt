package com.aniplex.app.presentation.screens.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.aniplex.app.presentation.components.SearchableAnimeGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    SearchableAnimeGrid(
        onAnimeClick = onAnimeClick,
        viewModel = viewModel,
        modifier = modifier.fillMaxSize()
    )
}

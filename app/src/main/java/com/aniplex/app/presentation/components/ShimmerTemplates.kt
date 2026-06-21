package com.aniplex.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aniplex.app.theme.SurfaceDark
import com.valentinilk.shimmer.shimmer

@Composable
fun SpotlightBannerShimmer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .shimmer()
            .background(Color.DarkGray)
    )
}

@Composable
fun AnimeCardShimmer() {
    Column(
        modifier = Modifier
            .width(135.dp)
            .shimmer()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
        )
    }
}

@Composable
fun AnimeRowShimmer() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Row Title Shimmer
        Box(
            modifier = Modifier
                .padding(start = 16.dp, bottom = 12.dp)
                .width(120.dp)
                .height(18.dp)
                .shimmer()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false
        ) {
            items(5) {
                AnimeCardShimmer()
            }
        }
    }
}

@Composable
fun ScheduleItemShimmer() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shimmer()
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(48.dp)) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
        )
    }
}


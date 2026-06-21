package com.aniplex.app.presentation.screens.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.aniplex.app.presentation.components.ScheduleItemShimmer
import com.aniplex.app.domain.model.ScheduleItem
import com.aniplex.app.theme.*
import com.valentinilk.shimmer.shimmer
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScheduleScreen(
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val currentMillis by viewModel.currentTimeMillis.collectAsState()
    val dayTabs = viewModel.dayTabs

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundVoid)
    ) {
        // Scrollable Calendar Day Selector
        ScrollableTabRow(
            selectedTabIndex = dayTabs.indexOfFirst { it.dateString == selectedDate }.coerceAtLeast(0),
            containerColor = SurfaceDark,
            contentColor = Color.White,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                val selectedIndex = dayTabs.indexOfFirst { it.dateString == selectedDate }.coerceAtLeast(0)
                if (selectedIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                        color = CrunchyrollOrange
                    )
                }
            },
            divider = {
                HorizontalDivider(color = SurfaceDarkVariant, thickness = 1.dp)
            }
        ) {
            dayTabs.forEach { tab ->
                val isSelected = tab.dateString == selectedDate
                Tab(
                    selected = isSelected,
                    onClick = { viewModel.selectDate(tab.dateString) },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tab.displayDay,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CrunchyrollOrange else TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.displayDate,
                            fontSize = 11.sp,
                            color = if (isSelected) Color.White else TextMuted
                        )
                    }
                }
            }
        }

        // Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (val state = uiState) {
                is ScheduleUiState.Loading -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(8) {
                            ScheduleItemShimmer()
                        }
                    }
                }
                is ScheduleUiState.Empty -> {
                    EmptyScheduleState()
                }
                is ScheduleUiState.Error -> {
                    ErrorScheduleState(
                        errorMessage = state.message,
                        onRetry = { viewModel.retry() }
                    )
                }
                is ScheduleUiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.schedules) { item ->
                            ScheduleItemCard(
                                item = item,
                                selectedDate = selectedDate,
                                currentMillis = currentMillis,
                                onClick = { onAnimeClick(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleItemCard(
    item: ScheduleItem,
    selectedDate: String,
    currentMillis: Long,
    onClick: () -> Unit
) {
    val targetMillis = remember(item.time, selectedDate) {
        parseAiringTime(selectedDate, item.time)
    }

    val countdownText = remember(targetMillis, currentMillis) {
        if (targetMillis != null) {
            formatCountdown(targetMillis, currentMillis)
        } else {
            "Scheduled"
        }
    }

    val isAiringSoon = remember(targetMillis, currentMillis) {
        if (targetMillis != null) {
            val diff = targetMillis - currentMillis
            diff in 1..3600000L // 0 to 60 minutes
        } else {
            false
        }
    }

    val localTimeText = remember(targetMillis, item.time) {
        if (targetMillis != null) {
            try {
                val date = java.util.Date(targetMillis)
                val sdfLocal = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getDefault()
                }
                sdfLocal.format(date)
            } catch (e: Exception) {
                item.time
            }
        } else {
            item.time
        }
    }

    val localTimeZoneAbbr = remember(targetMillis) {
        try {
            val tz = java.util.TimeZone.getDefault()
            val isDst = tz.inDaylightTime(java.util.Date(targetMillis ?: System.currentTimeMillis()))
            tz.getDisplayName(isDst, java.util.TimeZone.SHORT, java.util.Locale.getDefault())
        } catch (e: Exception) {
            "Local"
        }
    }

    val dayOffset = remember(targetMillis, selectedDate) {
        if (targetMillis != null) {
            try {
                val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val selectedDateParsed = sdfDate.parse(selectedDate)
                if (selectedDateParsed != null) {
                    val calUTC = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                        time = selectedDateParsed
                    }
                    val calLocal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault()).apply {
                        timeInMillis = targetMillis
                    }
                    
                    val calUtcNormalized = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                        set(calUTC.get(java.util.Calendar.YEAR), calUTC.get(java.util.Calendar.MONTH), calUTC.get(java.util.Calendar.DAY_OF_MONTH), 0, 0, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val calLocalNormalized = java.util.Calendar.getInstance(java.util.TimeZone.getDefault()).apply {
                        set(calLocal.get(java.util.Calendar.YEAR), calLocal.get(java.util.Calendar.MONTH), calLocal.get(java.util.Calendar.DAY_OF_MONTH), 0, 0, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    
                    val diffMs = calLocalNormalized.timeInMillis - calUtcNormalized.timeInMillis
                    val diffDays = Math.round(diffMs.toDouble() / (24 * 60 * 60 * 1000)).toInt()
                    
                    when {
                        diffDays > 0 -> "+${diffDays}d"
                        diffDays < 0 -> "${diffDays}d"
                        else -> ""
                    }
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark
        ),
        border = BorderStroke(1.dp, SurfaceDarkVariant),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster Image Column (if poster is present)
            if (!item.poster.isNullOrBlank()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 46.dp, height = 64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Airing Time Column
            Column(
                modifier = Modifier.width(76.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = localTimeText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (dayOffset.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = dayOffset,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrunchyrollOrange
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = localTimeZoneAbbr,
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info Column
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
                if (item.episode > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = SurfaceDarkVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Episode ${item.episode}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrunchyrollOrange,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Countdown Status Column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (isAiringSoon) {
                    PulsingDot()
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = countdownText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        countdownText == "Aired" -> SuccessColor
                        isAiringSoon -> CrunchyrollOrange
                        targetMillis != null && targetMillis - currentMillis > 0 -> TextSecondary
                        else -> TextMuted
                    }
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer(alpha = alpha)
            .background(CrunchyrollOrange, CircleShape)
    )
}

@Composable
fun EmptyScheduleState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.EventNote,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Airing Releases",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "There are no anime releases scheduled for this day. Try checking other dates in the calendar above.",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun ErrorScheduleState(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = ErrorColor,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Failed to Load Schedule",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry", fontWeight = FontWeight.Bold)
        }
    }
}



private fun parseAiringTime(dateString: String, timeString: String): Long? {
    return try {
        val date = LocalDate.parse(dateString)
        val time = try {
            LocalTime.parse(timeString.trim())
        } catch (e: Exception) {
            val formatter12h = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
            val formatter12hNoSpace = DateTimeFormatter.ofPattern("h:mma", Locale.US)
            try {
                LocalTime.parse(timeString.trim().uppercase(), formatter12h)
            } catch (e2: Exception) {
                LocalTime.parse(timeString.trim().uppercase(), formatter12hNoSpace)
            }
        }
        // Assume API returns times in UTC (universal)
        date.atTime(time).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

private fun formatCountdown(targetMillis: Long, currentMillis: Long): String {
    val diff = targetMillis - currentMillis
    if (diff <= 0) {
        return "Aired"
    }
    val diffSeconds = diff / 1000
    val days = diffSeconds / 86400
    val hours = (diffSeconds % 86400) / 3600
    val minutes = (diffSeconds % 3600) / 60
    
    return when {
        days > 0 -> "Airs in ${days}d ${hours}h"
        hours > 0 -> "Airs in ${hours}h ${minutes}m"
        else -> "Airs in ${minutes}m"
    }
}

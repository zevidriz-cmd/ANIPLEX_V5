package com.aniplex.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.aniplex.app.presentation.screens.auth.LoginScreen
import com.aniplex.app.presentation.screens.auth.TvLoginScreen
import com.aniplex.app.presentation.screens.auth.QrScannerScreen
import com.aniplex.app.presentation.screens.browse.BrowseScreen
import com.aniplex.app.presentation.screens.detail.DetailScreen
import com.aniplex.app.presentation.screens.detail.TvDetailScreen
import com.aniplex.app.presentation.screens.home.HomeScreen
import com.aniplex.app.presentation.screens.home.TvHomeScreen
import com.aniplex.app.presentation.screens.player.PlayerScreen
import com.aniplex.app.presentation.screens.profile.ProfileScreen
import com.aniplex.app.presentation.screens.profile.ProfileSelectionScreen
import com.aniplex.app.presentation.screens.schedule.ScheduleScreen
import com.aniplex.app.presentation.screens.watchlist.WatchlistScreen
import com.aniplex.app.presentation.screens.history.HistoryScreen
import com.aniplex.app.presentation.screens.search.SearchScreen
import com.aniplex.app.presentation.screens.splash.SplashScreen
import com.aniplex.app.presentation.screens.downloads.DownloadsScreen
import com.aniplex.app.theme.BackgroundVoid
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.SurfaceDark
import com.aniplex.app.theme.TextPrimary
import com.aniplex.app.theme.TextSecondary

@Composable
fun MainNavigation(isTv: Boolean = false) {
    if (isTv) {
        TvMainNavigation()
    } else {
        MobileMainNavigation()
    }
}

@Composable
fun MobileMainNavigation() {
    val backStack = rememberNavBackStack(Splash)


    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            slideInHorizontally(
                initialOffsetX = { it / 6 },
                animationSpec = tween(240, easing = EaseOutCubic)
            ) + fadeIn(animationSpec = tween(240, easing = EaseOutCubic)) togetherWith
            slideOutHorizontally(
                targetOffsetX = { -it / 24 },
                animationSpec = tween(240, easing = EaseOutCubic)
            ) + fadeOut(animationSpec = tween(160, easing = EaseOutCubic))
        },
        popTransitionSpec = {
            slideInHorizontally(
                initialOffsetX = { -it / 24 },
                animationSpec = tween(240, easing = EaseOutCubic)
            ) + fadeIn(animationSpec = tween(240, easing = EaseOutCubic)) togetherWith
            slideOutHorizontally(
                targetOffsetX = { it / 6 },
                animationSpec = tween(240, easing = EaseOutCubic)
            ) + fadeOut(animationSpec = tween(160, easing = EaseOutCubic))
        },
        entryProvider = entryProvider {
            entry<Splash> {
                SplashScreen(
                    onNavigate = { nextScreen ->
                        backStack.removeLastOrNull() // Pop Splash
                        backStack.add(nextScreen)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Login> {
                LoginScreen(
                    onLoginSuccess = {
                        backStack.removeLastOrNull() // Pop Login
                        backStack.add(ProfileSelection(showBack = false))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<ProfileSelection> { key ->
                ProfileSelectionScreen(
                    onProfileSelected = {
                        if (key.showBack) {
                            backStack.removeLastOrNull()
                        } else {
                            backStack.removeLastOrNull() // Pop ProfileSelection
                            backStack.add(Home)
                        }
                    },
                    onSignOut = {
                        backStack.clear()
                        backStack.add(Login)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Home> {
                DashboardShell(
                    onAnimeClick = { animeId ->
                        backStack.add(Detail(animeId))
                    },
                    onEpisodeClick = { epId, animId, title, epNum, cat, progress ->
                        backStack.add(Player(epId, animId, title, epNum, cat, resumePlayback = true, initialProgress = progress))
                    },
                    onSearchClick = {
                        backStack.add(Search)
                    },
                    onSignOut = {
                        backStack.clear()
                        backStack.add(Login)
                    },
                    onWatchlistClick = {
                        backStack.add(Watchlist)
                    },
                    onHistoryClick = {
                        backStack.add(History)
                    },
                    onSwitchProfile = {
                        backStack.add(ProfileSelection(showBack = true))
                    },
                    onScanTvClick = {
                        backStack.add(QrScanner)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            @androidx.camera.core.ExperimentalGetImage
            entry<QrScanner> {
                QrScannerScreen(
                    onCodeScanned = { sessionId ->
                        // This callback is triggered when QR code is scanned
                    },
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Search> {
                SearchScreen(
                    onAnimeClick = { animeId ->
                        backStack.add(Detail(animeId))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Watchlist> {
                WatchlistScreen(
                    onAnimeClick = { animeId ->
                        backStack.add(Detail(animeId))
                    },
                    onEpisodeClick = { epId, animId, title, epNum, cat, progress ->
                        backStack.add(Player(epId, animId, title, epNum, cat, resumePlayback = true, initialProgress = progress))
                    },
                    onSearchClick = { backStack.add(Search) },
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<History> {
                HistoryScreen(
                    onAnimeClick = { animeId ->
                        backStack.add(Detail(animeId))
                    },
                    onEpisodeClick = { epId, animId, title, epNum, cat, progress ->
                        backStack.add(Player(epId, animId, title, epNum, cat, resumePlayback = true, initialProgress = progress))
                    },
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Detail> { key ->
                DetailScreen(
                    animeId = key.animeId,
                    onBackClick = { backStack.removeLastOrNull() },
                    onPlayClick = { epId, animId, title, epNum, cat ->
                        backStack.add(Player(epId, animId, title, epNum, cat, resumePlayback = true))
                    },
                    onRecommendationClick = { recId ->
                        backStack.add(Detail(recId))
                    },
                    onSeasonSelect = { newId ->
                        backStack.removeLastOrNull()
                        backStack.add(Detail(newId))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Player> { key ->
                PlayerScreen(
                    episodeId = key.episodeId,
                    animeId = key.animeId,
                    animeTitle = key.animeTitle,
                    episodeNumber = key.episodeNumber,
                    category = key.category,
                    resumePlayback = key.resumePlayback,
                    initialProgressParam = key.initialProgress,
                    onBackClick = { backStack.removeLastOrNull() },
                    onAnimeClick = { animeId ->
                        backStack.add(Detail(animeId))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Downloads> {
                DownloadsScreen(
                    onPlayClick = { epId, animId, title, epNum, cat ->
                        backStack.add(Player(epId, animId, title, epNum, cat, resumePlayback = true))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

enum class DashboardTab {
    HOME, MY_LISTS, BROWSE, ACCOUNT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardShell(
    onAnimeClick: (String) -> Unit,
    onEpisodeClick: (String, String, String, Int, String, Long) -> Unit,
    onSearchClick: () -> Unit,
    onSignOut: () -> Unit,
    onWatchlistClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSwitchProfile: () -> Unit,
    onScanTvClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.HOME) }
    var browseInitialTab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            when (selectedTab) {
                DashboardTab.HOME -> {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFC5BAFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF13111E)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "ANIPLEX",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onSearchClick) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = { selectedTab = DashboardTab.ACCOUNT },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF221F36))
                                    .border(1.dp, Color(0xFFC5BAFF).copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = Color(0xFFC5BAFF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color.White
                        ),
                        modifier = Modifier.background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                            )
                        )
                    )
                }
                DashboardTab.BROWSE -> {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Discover",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundVoid)
                    )
                }
                DashboardTab.ACCOUNT -> {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Account & Settings",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundVoid)
                    )
                }
                else -> {
                    // Downloads and My Lists screens handle top bar rendering
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = TextSecondary,
                tonalElevation = 0.dp,
                modifier = Modifier.border(width = (0.5).dp, color = Color(0xFF221F36), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                val activeColor = CrunchyrollOrange
                
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.HOME,
                    onClick = { selectedTab = DashboardTab.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF13111E),
                        selectedTextColor = TextPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = activeColor
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.BROWSE,
                    onClick = { 
                        browseInitialTab = 0
                        selectedTab = DashboardTab.BROWSE 
                    },
                    icon = { Icon(Icons.Default.GridView, contentDescription = "Discover") },
                    label = { Text("Discover", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF13111E),
                        selectedTextColor = TextPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = activeColor
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.MY_LISTS,
                    onClick = { selectedTab = DashboardTab.MY_LISTS },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = "Library") },
                    label = { Text("Library", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF13111E),
                        selectedTextColor = TextPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = activeColor
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.ACCOUNT,
                    onClick = { selectedTab = DashboardTab.ACCOUNT },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF13111E),
                        selectedTextColor = TextPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = activeColor
                    )
                )
            }
        }
    ) { innerPadding ->
        val topPadding = if (selectedTab == DashboardTab.HOME || selectedTab == DashboardTab.MY_LISTS) 0.dp else innerPadding.calculateTopPadding()
        val screenModifier = Modifier.padding(
            top = topPadding,
            bottom = innerPadding.calculateBottomPadding()
        )
        
        when (selectedTab) {
            DashboardTab.HOME -> HomeScreen(
                onAnimeClick = onAnimeClick,
                onEpisodeClick = onEpisodeClick,
                onSearchClick = onSearchClick,
                onNavigateToDiscover = { tabIndex ->
                    browseInitialTab = tabIndex
                    selectedTab = DashboardTab.BROWSE
                },
                modifier = screenModifier
            )
            DashboardTab.MY_LISTS -> WatchlistScreen(
                onAnimeClick = onAnimeClick,
                onEpisodeClick = onEpisodeClick,
                onSearchClick = onSearchClick,
                onBackClick = null,
                modifier = screenModifier
            )
            DashboardTab.BROWSE -> BrowseScreen(
                onAnimeClick = onAnimeClick,
                onSearchClick = onSearchClick,
                initialTab = browseInitialTab,
                modifier = screenModifier
            )
            DashboardTab.ACCOUNT -> ProfileScreen(
                onSignOut = onSignOut,
                onWatchlistClick = onWatchlistClick,
                onHistoryClick = onHistoryClick,
                onSwitchProfile = onSwitchProfile,
                onScanTvClick = onScanTvClick,
                modifier = screenModifier
            )
        }
    }
}

@Composable
fun TvMainNavigation() {
    val backStack = rememberNavBackStack(Splash)


    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            slideInHorizontally(
                initialOffsetX = { it / 6 },
                animationSpec = tween(240, easing = EaseOutCubic)
            ) + fadeIn(animationSpec = tween(240, easing = EaseOutCubic)) togetherWith
            slideOutHorizontally(
                targetOffsetX = { -it / 24 },
                animationSpec = tween(240, easing = EaseOutCubic)
            ) + fadeOut(animationSpec = tween(160, easing = EaseOutCubic))
        },
        popTransitionSpec = {
            slideInHorizontally(
                initialOffsetX = { -it / 24 },
                animationSpec = tween(240, easing = EaseOutCubic)
            ) + fadeIn(animationSpec = tween(240, easing = EaseOutCubic)) togetherWith
            slideOutHorizontally(
                targetOffsetX = { it / 6 },
                animationSpec = tween(240, easing = EaseOutCubic)
            ) + fadeOut(animationSpec = tween(160, easing = EaseOutCubic))
        },
        entryProvider = entryProvider {
            entry<Splash> {
                SplashScreen(
                    onNavigate = { nextScreen ->
                        backStack.removeLastOrNull()
                        backStack.add(nextScreen)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Login> {
                TvLoginScreen(
                    onLoginSuccess = {
                        backStack.removeLastOrNull()
                        backStack.add(ProfileSelection(showBack = false))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<ProfileSelection> { key ->
                ProfileSelectionScreen(
                    onProfileSelected = {
                        if (key.showBack) {
                            backStack.removeLastOrNull()
                        } else {
                            backStack.removeLastOrNull()
                            backStack.add(Home)
                        }
                    },
                    onSignOut = {
                        backStack.clear()
                        backStack.add(Login)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Home> {
                com.aniplex.app.presentation.screens.home.TvDashboardShell(
                    onAnimeClick = { animeId ->
                        backStack.add(Detail(animeId))
                    },
                    onEpisodeClick = { epId, animId, title, epNum, cat, progress ->
                        backStack.add(Player(epId, animId, title, epNum, cat, resumePlayback = true, initialProgress = progress))
                    },
                    onSwitchProfile = {
                        backStack.clear()
                        backStack.add(ProfileSelection(showBack = false))
                    },
                    onSignOut = {
                        backStack.clear()
                        backStack.add(Login)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Detail> { key ->
                TvDetailScreen(
                    animeId = key.animeId,
                    onBackClick = { backStack.removeLastOrNull() },
                    onPlayClick = { epId, animId, title, epNum, cat ->
                        backStack.add(Player(epId, animId, title, epNum, cat, resumePlayback = true))
                    },
                    onRecommendationClick = { recId ->
                        backStack.add(Detail(recId))
                    },
                    onSeasonSelect = { newId ->
                        backStack.removeLastOrNull()
                        backStack.add(Detail(newId))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Player> { key ->
                PlayerScreen(
                    episodeId = key.episodeId,
                    animeId = key.animeId,
                    animeTitle = key.animeTitle,
                    episodeNumber = key.episodeNumber,
                    category = key.category,
                    resumePlayback = key.resumePlayback,
                    initialProgressParam = key.initialProgress,
                    isTv = true,
                    onBackClick = { backStack.removeLastOrNull() },
                    onAnimeClick = { animeId ->
                        backStack.add(Detail(animeId))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

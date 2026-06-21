package com.aniplex.app.presentation.screens.profile

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniplex.app.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onWatchlistClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val user = viewModel.currentUser
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()

    val defaultAudio by viewModel.defaultAudioCategory.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplayNextEpisode.collectAsStateWithLifecycle()
    val quality by viewModel.preferredQuality.collectAsStateWithLifecycle()
    val skipIntro by viewModel.skipIntro.collectAsStateWithLifecycle()
    val skipOutro by viewModel.skipOutro.collectAsStateWithLifecycle()
    val downloadOverCellular by viewModel.downloadOverCellular.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()

    var qualityExpanded by remember { mutableStateOf(false) }
    val qualityOptions = listOf("Ultra HD 4K", "1080p FHD", "725p HD", "Data Saver")

    // Performance Mode Switch State (Premium feature)
    var isHevcDecoderEnabled by remember { mutableStateOf(true) }
    var isDolbyAtmosEnabled by remember { mutableStateOf(true) }

    // Visual Accent Picker
    var selectedAccentColor by remember { mutableStateOf("Purple Neon") }
    val accentColors = listOf(
        Triple("Purple Neon", CrunchyrollOrange, "Celestial violet accent"),
        Triple("Cosmic Red", NetflixRed, "Radiant crimson flame"),
        Triple("Future Teal", Color(0xFF00E5FF), "Neon cyan cyber vibe"),
        Triple("Gold Master", Color(0xFFFFD700), "Sovereign absolute gold"),
        Triple("Pure Emerald", Color(0xFF00E676), "Bright zen emerald")
    )

    // Interactive Speed Test State
    var isSpeedTesting by remember { mutableStateOf(false) }
    var speedTestStage by remember { mutableStateOf("Ready") }
    var speedProgress by remember { mutableStateOf(0f) }
    var speedValue by remember { mutableStateOf(0.0) }

    // Interactive Deep Cache cleaner local state
    var isCleaningCache by remember { mutableStateOf(false) }
    var cacheCleanSuccess by remember { mutableStateOf(false) }

    // Parse profile settings and avatar list
    val parsedSettings = remember(activeProfile?.avatarUrl) {
        ProfileSettings.parse(activeProfile?.avatarUrl ?: "avatar_orange")
    }
    val premiumAvatars = getPremiumAvatars()
    val matchingAvatar = premiumAvatars.find { it.id == parsedSettings.avatarType } ?: premiumAvatars.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundVoid)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Sleek Profile Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            matchingAvatar.primaryColor.copy(alpha = 0.25f),
                            BackgroundVoid
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Ambient visual dots/glowing background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(matchingAvatar.primaryColor.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                // Outer VIP dynamic glowing border
                val infiniteTransition = rememberInfiniteTransition(label = "VIPGlow")
                val animatedScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "vipScale"
                )

                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .scale(animatedScale)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                colors = listOf(
                                    matchingAvatar.primaryColor,
                                    Color(0xFFFFD700),
                                    matchingAvatar.primaryColor
                                )
                            )
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Brush.radialGradient(matchingAvatar.gradientColors))
                            .clickable { onSwitchProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        // Render Avatar Dynamic Vector or original Initials
                        if (activeProfile?.avatarUrl?.isNotBlank() == true && activeProfile?.avatarUrl != "avatar_orange") {
                            Icon(
                                imageVector = matchingAvatar.icon,
                                contentDescription = matchingAvatar.name,
                                tint = Color.White,
                                modifier = Modifier.size(54.dp)
                            )
                        } else {
                            Text(
                                text = (activeProfile?.name ?: user?.displayName ?: "A").take(1).uppercase(),
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // Crown Icon VIP badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-4).dp, y = (-4).dp)
                                .size(28.dp)
                                .background(Color(0xFFFFD700), CircleShape)
                                .border(1.5.dp, Color.Black, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "VIP Member",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Profile Name
                Text(
                    text = activeProfile?.name ?: user?.displayName?.takeIf { it.isNotBlank() } ?: "Boss",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Mega Fan Premium Level tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(Color(0xFF211F36), RoundedCornerShape(12.dp))
                        .border(0.5.dp, matchingAvatar.primaryColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = matchingAvatar.primaryColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (parsedSettings.isKidsMode) "JUNIOR MODE ACTIVE" else "MEGA FAN • PREMIUM PLUS",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // 2. High-Fidelity VIP Subscription Benefit Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = (-20).dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            matchingAvatar.primaryColor.copy(alpha = 0.15f),
                            Color(0xFF171625)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(matchingAvatar.primaryColor.copy(alpha = 0.4f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Next Billing Cycle",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "July 15, 2026 • Auto-renews",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            Toast.makeText(context, "Aniplex Pro+ benefits are active!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = matchingAvatar.primaryColor),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Manage",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = SurfaceDarkVariant)
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "LOCKED-IN ANIME CLUB PERKS:",
                    color = matchingAvatar.primaryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                val benefits = listOf(
                    "Simulcast releases directly from Japan within 1 hour",
                    "Crisp, Ultra HD 4K video quality streams",
                    "Spatial Surround Audio powered by Dolby Atmos®",
                    "Ad-Free visual enjoyment on all screens"
                )

                benefits.forEach { benefit ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Included",
                            tint = SuccessColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = benefit,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Profile Configuration Quick Link Controls
        Text(
            text = "Profile Controls & Security",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(SurfaceDark, RoundedCornerShape(12.dp))
                .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(12.dp))
        ) {
            SettingsRow(
                title = "Switch Current Profile",
                valueText = activeProfile?.name,
                onClick = onSwitchProfile
            )
            HorizontalDivider(color = SurfaceDarkVariant)

            SettingsRow(
                title = "Profiles Settings & Access PIN Lock",
                onClick = onSwitchProfile
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // INTERACTIVE SPEED TESTING & STREAM OPTIMIZER
        Text(
            text = "Network Diagnostics & Stream Optimizer",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = matchingAvatar.primaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Simulcast Bandwidth Test",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Verify optimal latency & live stream capacity",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (!isSpeedTesting) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isSpeedTesting = true
                                    speedProgress = 0f
                                    speedValue = 0.0

                                    speedTestStage = "Locating nearby stream node..."
                                    delay(1000)

                                    speedTestStage = "Running latency check..."
                                    delay(800)

                                    speedTestStage = "Testing 4K pipeline bandwidth..."
                                    for (i in 1..20) {
                                        speedProgress = i / 20f
                                        speedValue = 35.0 + (Math.random() * 15.0) + (i * 6.5)
                                        delay(80)
                                    }

                                    speedTestStage = "Optimizing live video buffers..."
                                    delay(600)

                                    speedTestStage = "Completed"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkVariant),
                            border = BorderStroke(1.dp, matchingAvatar.primaryColor.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Run Test", fontSize = 12.sp, color = matchingAvatar.primaryColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isSpeedTesting) {
                    Spacer(modifier = Modifier.height(16.dp))

                    if (speedTestStage != "Completed") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = speedTestStage,
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (speedValue > 0) {
                                    Text(
                                        text = "${speedValue.toInt()} Mbps",
                                        color = matchingAvatar.primaryColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { speedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = matchingAvatar.primaryColor,
                                trackColor = SurfaceDarkVariant
                            )
                        }
                    } else {
                        // Success block showing results beautifully
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SuccessColor.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .border(0.5.dp, SuccessColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Success",
                                            tint = SuccessColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Diagnostics Completed!",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    TextButton(
                                        onClick = { isSpeedTesting = false },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Dismiss", color = SuccessColor, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Download speed: ${speedValue.toInt()} Mbps (Ping: 14ms). Your streaming network setup is optimal. You are fully geared to stream 4K Ultra HD on multiple devices without throttling.",
                                    color = Color.LightGray.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PREVIEW ACCENT STYLE PICKER
        Text(
            text = "Personalize App Atmosphere",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Streaming Accent Aura",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Subtly styles selection filters & highlights",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    accentColors.forEach { (name, color, desc) ->
                        val isSelected = selectedAccentColor == name
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = 3.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedAccentColor = name
                                    Toast.makeText(context, "$name accent activated!", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                val currentDesc = accentColors.find { it.first == selectedAccentColor }?.third ?: ""
                Text(
                    text = "Aura Mode: $currentDesc",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // VIEWING PREFERENCES
        Text(
            text = "Viewing Preferences",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(SurfaceDark, RoundedCornerShape(12.dp))
                .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(12.dp))
        ) {
            // Audio Language Toggle
            SettingsRow(
                title = "Default Audio Broadcast",
                valueText = defaultAudio.uppercase(),
                onClick = {
                    val nextVal = if (defaultAudio == "sub") "dub" else "sub"
                    viewModel.setDefaultAudioCategory(nextVal)
                }
            )
            HorizontalDivider(color = SurfaceDarkVariant)

            // Stream Quality Selection
            Box {
                SettingsRow(
                    title = "Streaming Playback Target",
                    valueText = quality,
                    onClick = { qualityExpanded = true }
                )
                DropdownMenu(
                    expanded = qualityExpanded,
                    onDismissRequest = { qualityExpanded = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    qualityOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = Color.White) },
                            onClick = {
                                viewModel.setPreferredQuality(option)
                                qualityExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // APP EXPERIENCE & HEVC STREAM SWITCHES
        Text(
            text = "Fine-Tuned Stream Playback",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(SurfaceDark, RoundedCornerShape(12.dp))
                .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(12.dp))
        ) {
            // Autoplay Next Episode
            SettingsSwitchRow(
                title = "Autoplay Next Episode",
                checked = autoplay,
                onCheckedChange = { viewModel.setAutoplayNextEpisode(it) }
            )
            HorizontalDivider(color = SurfaceDarkVariant)

            // Skip Intro
            SettingsSwitchRow(
                title = "Auto-Skip Intro Themes",
                checked = skipIntro,
                onCheckedChange = { viewModel.setSkipIntro(it) }
            )
            HorizontalDivider(color = SurfaceDarkVariant)

            // Skip Outro
            SettingsSwitchRow(
                title = "Auto-Skip End Credits",
                checked = skipOutro,
                onCheckedChange = { viewModel.setSkipOutro(it) }
            )
            HorizontalDivider(color = SurfaceDarkVariant)

            // Live HEVC decoder switcher
            SettingsSwitchRow(
                title = "GPU HEVC Decoding Stream Engine",
                checked = isHevcDecoderEnabled,
                onCheckedChange = { isHevcDecoderEnabled = it }
            )
            HorizontalDivider(color = SurfaceDarkVariant)

            // Atmos spatial switcher
            SettingsSwitchRow(
                title = "Spatial Surround Atmos soundstage",
                checked = isDolbyAtmosEnabled,
                onCheckedChange = { isDolbyAtmosEnabled = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // STORAGE MANAGEMENT & CLEANER VISUAL BAR
        Text(
            text = "Storage & Cache Optimization",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = matchingAvatar.primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Internal High-Speed Storage",
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isCleaningCache) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    isCleaningCache = true
                                    delay(1500)
                                    viewModel.clearCache {
                                        isCleaningCache = false
                                        cacheCleanSuccess = true
                                        Toast.makeText(context, "Aniplex dynamic cache cleared!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = "Deep Clean Cache",
                                color = matchingAvatar.primaryColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            color = matchingAvatar.primaryColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom elegant segmented storage bar visualizer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF2A2A3C))
                ) {
                    // Segment 1: Cache (vibrant lavender color)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(if (cacheCleanSuccess) 0.05f else 0.2f)
                            .background(matchingAvatar.primaryColor)
                    )
                    // Segment 2: Other apps data (medium dark slate grey)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.45f)
                            .background(Color(0xFF5D597A))
                    )
                    // Segment 3: Available Free Space
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(if (cacheCleanSuccess) 0.5f else 0.35f)
                            .background(Color(0xFF1F1E2E))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(matchingAvatar.primaryColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (cacheCleanSuccess) "Aniplex files (0.1 GB)" else "Aniplex Files (1.2 GB)",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF5D597A))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Other data (14.5 GB)",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.Gray, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (cacheCleanSuccess) "Free (17.4 GB)" else "Free (16.3 GB)",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Log Out Button
        Button(
            onClick = { viewModel.signOut(onSignOut) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(50.dp)
                .border(1.dp, NetflixRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Log Out",
                    tint = NetflixRed
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LOG OUT ACCOUNT",
                    color = NetflixRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Footer version & policy info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Version 4.210.3 (9958)",
                fontSize = 11.sp,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Terms of Service",
                    fontSize = 11.sp,
                    color = matchingAvatar.primaryColor,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Opening Terms of Service...", Toast.LENGTH_SHORT).show()
                    }
                )
                Text(
                    text = "Privacy Policy",
                    fontSize = 11.sp,
                    color = matchingAvatar.primaryColor,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Opening Privacy Policy...", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    valueText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (valueText != null) {
                Text(
                    text = valueText,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CrunchyrollOrange,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceDarkVariant
            )
        )
    }
}

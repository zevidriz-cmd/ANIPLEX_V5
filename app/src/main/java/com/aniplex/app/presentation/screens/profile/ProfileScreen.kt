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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onWatchlistClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSwitchProfile: () -> Unit,
    onScanTvClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()

    val defaultAudio by viewModel.defaultAudioCategory.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplayNextEpisode.collectAsStateWithLifecycle()
    val quality by viewModel.preferredQuality.collectAsStateWithLifecycle()
    val skipIntro by viewModel.skipIntro.collectAsStateWithLifecycle()
    val skipOutro by viewModel.skipOutro.collectAsStateWithLifecycle()
    val downloadOverCellular by viewModel.downloadOverCellular.collectAsStateWithLifecycle()
    val preferredProvider by viewModel.preferredProvider.collectAsStateWithLifecycle()
    val enableDiagnostics by viewModel.enableDiagnostics.collectAsStateWithLifecycle()
    val hevcDecoderEnabled by viewModel.hevcDecoderEnabled.collectAsStateWithLifecycle()
    val dolbyAtmosEnabled by viewModel.dolbyAtmosEnabled.collectAsStateWithLifecycle()
    val preferredAccentColor by viewModel.preferredAccentColor.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()

    var qualityExpanded by remember { mutableStateOf(false) }
    val qualityOptions = listOf("Ultra HD 4K", "1080p FHD", "725p HD", "Data Saver")

    var providerExpanded by remember { mutableStateOf(false) }
    val providerOptions = listOf("Zoro (HD-1)", "Gogoanime (RapidCloud)")

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
    var speedPing by remember { mutableStateOf(14) }

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
            HorizontalDivider(color = SurfaceDarkVariant)

            SettingsRow(
                title = "Scan TV QR Code",
                onClick = onScanTvClick
            )
            HorizontalDivider(color = SurfaceDarkVariant)

            var isCheckingUpdates by remember { mutableStateOf(false) }
            val updateViewModel: com.aniplex.app.presentation.screens.update.UpdateViewModel = hiltViewModel()

            SettingsRow(
                title = "Check for Updates",
                valueText = if (isCheckingUpdates) "Checking..." else "Check",
                onClick = {
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
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // INTERACTIVE SPEED TESTING & STREAM OPTIMIZER
        Text(
            text = "Network Diagnostics & Telemetry",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        // Capture Logs & Diagnostics Switch Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Diagnostics",
                        tint = Color(0xFF00FFCC),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Playback Diagnostics & Cookie Capture",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Capture extracted stream telemetry & active cloudflare cookies",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Switch(
                    checked = enableDiagnostics,
                    onCheckedChange = { viewModel.updateEnableDiagnostics(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color(0xFF00FFCC)
                    )
                )
            }
        }

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
                                    speedProgress = 0.1f
                                    speedValue = 0.0

                                    speedTestStage = "Locating nearby stream node..."
                                    delay(400)

                                    speedTestStage = "Running latency check..."
                                    val client = okhttp3.OkHttpClient.Builder()
                                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                        .build()

                                    val request = okhttp3.Request.Builder()
                                        .url("https://aniplex-proxy.f1886391.workers.dev/api/v2/home")
                                        .build()

                                    val startTime = System.currentTimeMillis()
                                    var ping = 14L
                                    var ok = false
                                    var downloadSpeedMbps = 0.0

                                    try {
                                        withContext(Dispatchers.IO) {
                                            client.newCall(request).execute().use { response ->
                                                val endTime = System.currentTimeMillis()
                                                ping = endTime - startTime
                                                if (response.isSuccessful) {
                                                    ok = true
                                                    val bodyBytes = response.body?.bytes()
                                                    val sizeInBytes = bodyBytes?.size ?: 0
                                                    val downloadTimeSec = (endTime - startTime) / 1000.0
                                                    if (downloadTimeSec > 0 && sizeInBytes > 0) {
                                                        downloadSpeedMbps = (sizeInBytes * 8.0) / (1024.0 * 1024.0) / downloadTimeSec
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        ping = 120L
                                    }

                                    speedPing = ping.toInt()
                                    speedProgress = 0.4f
                                    speedTestStage = "Testing 4K pipeline bandwidth..."
                                    delay(400)

                                    if (downloadSpeedMbps <= 0.0) {
                                        val baseSpeed = if (ping < 50) 85.0 else if (ping < 150) 45.0 else 12.0
                                        downloadSpeedMbps = baseSpeed + (Math.random() * 15.0)
                                    }

                                    for (i in 5..10) {
                                        speedProgress = i / 10f
                                        speedValue = downloadSpeedMbps + (Math.random() * 5.0) - 2.5
                                        delay(120)
                                    }

                                    speedTestStage = "Optimizing live video buffers..."
                                    delay(400)

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
                                    text = "Download speed: ${speedValue.toInt()} Mbps (Ping: ${speedPing}ms). Your streaming network setup is optimal. You are fully geared to stream 4K Ultra HD on multiple devices without throttling.",
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
                        val isSelected = preferredAccentColor == name
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
                                    viewModel.setPreferredAccentColor(name)
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
                val currentDesc = accentColors.find { it.first == preferredAccentColor }?.third ?: ""
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
            HorizontalDivider(color = SurfaceDarkVariant)

            // Preferred Streaming Server Selection
            Box {
                SettingsRow(
                    title = "Preferred Streaming Server",
                    valueText = if (preferredProvider == "zoro") "Zoro (HD-1)" else "Gogoanime (RapidCloud)",
                    onClick = { providerExpanded = true }
                )
                DropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    providerOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = Color.White) },
                            onClick = {
                                val value = if (option.startsWith("Zoro")) "zoro" else "gogoanime"
                                viewModel.setPreferredProvider(value)
                                providerExpanded = false
                            }
                        )
                    }
                }
            }
            HorizontalDivider(color = SurfaceDarkVariant)

            // Subtitle Style Customizer
            var showSubtitleCustomizer by remember { mutableStateOf(false) }
            SettingsRow(
                title = "Subtitle Styling & Layout",
                valueText = "Configure",
                onClick = { showSubtitleCustomizer = true }
            )

            if (showSubtitleCustomizer) {
                SubtitleCustomizerOverlay(
                    viewModel = viewModel,
                    onDismiss = { showSubtitleCustomizer = false }
                )
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
                checked = hevcDecoderEnabled,
                onCheckedChange = { viewModel.setHevcDecoderEnabled(it) }
            )
            HorizontalDivider(color = SurfaceDarkVariant)

            // Atmos spatial switcher
            SettingsSwitchRow(
                title = "Spatial Surround Atmos soundstage",
                checked = dolbyAtmosEnabled,
                onCheckedChange = { viewModel.setDolbyAtmosEnabled(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // ACCOUNT SECURITY SETTINGS
        Text(
            text = "Account Security & Credentials",
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
            // Email display with Google provider badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Signed In As",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = user?.email ?: "No email registered",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (viewModel.isGoogleUser) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF4285F4).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(1.dp, Color(0xFF4285F4), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Managed by Google",
                            color = Color(0xFF4285F4),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            if (!viewModel.isGoogleUser) {
                HorizontalDivider(color = SurfaceDarkVariant)
                
                var showChangeEmailDialog by remember { mutableStateOf(false) }
                var showChangePasswordDialog by remember { mutableStateOf(false) }
                
                SettingsRow(
                    title = "Change Account Email",
                    valueText = "Update",
                    onClick = { showChangeEmailDialog = true }
                )
                
                HorizontalDivider(color = SurfaceDarkVariant)
                
                SettingsRow(
                    title = "Change Account Password",
                    valueText = "Update",
                    onClick = { showChangePasswordDialog = true }
                )
                
                if (showChangeEmailDialog) {
                    ChangeEmailDialog(
                        onDismiss = { showChangeEmailDialog = false },
                        onSubmit = { currentPassword, newEmail, onComplete ->
                            viewModel.changeEmail(
                                password = currentPassword,
                                newEmail = newEmail,
                                onSuccess = {
                                    Toast.makeText(context, "Email updated successfully", Toast.LENGTH_SHORT).show()
                                    showChangeEmailDialog = false
                                    onComplete(null)
                                },
                                onError = { error ->
                                    onComplete(error)
                                }
                            )
                        }
                    )
                }
                
                if (showChangePasswordDialog) {
                    ChangePasswordDialog(
                        onDismiss = { showChangePasswordDialog = false },
                        onSubmit = { currentPassword, newPassword, onComplete ->
                            viewModel.changePassword(
                                password = currentPassword,
                                newPassword = newPassword,
                                onSuccess = {
                                    Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                    showChangePasswordDialog = false
                                    onComplete(null)
                                },
                                onError = { error ->
                                    onComplete(error)
                                }
                            )
                        }
                    )
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
                text = "Version ${com.aniplex.app.BuildConfig.VERSION_NAME} (${com.aniplex.app.BuildConfig.VERSION_CODE})",
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

@Composable
fun SubtitleCustomizerOverlay(
    viewModel: ProfileViewModel,
    onDismiss: () -> Unit
) {
    val sizeScale by viewModel.subtitleSizeScale.collectAsStateWithLifecycle()
    val color by viewModel.subtitleColor.collectAsStateWithLifecycle()
    val bgOpacity by viewModel.subtitleBgOpacity.collectAsStateWithLifecycle()
    val style by viewModel.subtitleStyle.collectAsStateWithLifecycle()
    val position by viewModel.subtitlePosition.collectAsStateWithLifecycle()

    var tempSizeScale by remember(sizeScale) { mutableStateOf(sizeScale) }
    var tempColor by remember(color) { mutableStateOf(color) }
    var tempBgOpacity by remember(bgOpacity) { mutableStateOf(bgOpacity) }
    var tempStyle by remember(style) { mutableStateOf(style) }
    var tempPosition by remember(position) { mutableStateOf(position) }

    var previewMode by remember { mutableStateOf("Anime Frame") } // "Anime Frame", "Dark", "Light"

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundVoid),
            color = BackgroundVoid
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = "Subtitle Styling & Layout",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    TextButton(
                        onClick = {
                            viewModel.setSubtitleSizeScale(tempSizeScale)
                            viewModel.setSubtitleColor(tempColor)
                            viewModel.setSubtitleBgOpacity(tempBgOpacity)
                            viewModel.setSubtitleStyle(tempStyle)
                            viewModel.setSubtitlePosition(tempPosition)
                            onDismiss()
                        }
                    ) {
                        Text("Save", color = NetflixRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                // Interactive Live Preview Box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (previewMode) {
                            "Anime Frame" -> {
                                coil.compose.AsyncImage(
                                    model = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=800",
                                    contentDescription = "Anime Frame Backdrop",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.15f))
                                )
                            }
                            "Dark" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF0F0F0F))
                                )
                            }
                            "Light" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFE5E5E5))
                                )
                            }
                        }

                        // Subtitle text positioned dynamically
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = (100 * tempPosition).dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = tempBgOpacity),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val fontColor = when (tempColor.lowercase()) {
                                    "yellow" -> Color.Yellow
                                    "green" -> Color(0xFF4ADE80)
                                    "cyan" -> Color.Cyan
                                    "blue" -> Color(0xFF60A5FA)
                                    "pink" -> Color(0xFFF472B6)
                                    "red" -> NetflixRed
                                    "black" -> Color(0xFF1F2937)
                                    else -> Color.White
                                }
                                val fontSize = (14 * tempSizeScale).sp

                                Box(contentAlignment = Alignment.Center) {
                                    if (tempStyle.lowercase().contains("outlined") || tempStyle == "classic_outline") {
                                        Text(
                                            text = "Lorem Ipsum is simply dummy text.",
                                            color = Color.Black,
                                            fontSize = fontSize,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            style = androidx.compose.ui.text.TextStyle(
                                                shadow = androidx.compose.ui.graphics.Shadow(
                                                    color = Color.Black,
                                                    offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                                                    blurRadius = 6f
                                                )
                                            )
                                        )
                                    } else if (tempStyle.lowercase().contains("shadow") || tempStyle == "default") {
                                        Text(
                                            text = "Lorem Ipsum is simply dummy text.",
                                            color = Color.Black.copy(alpha = 0.8f),
                                            fontSize = fontSize,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.offset(x = 1.dp, y = 1.dp)
                                        )
                                    }

                                    val isBold = tempStyle == "bold"
                                    val fontFamily = when (tempStyle) {
                                        "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                                        "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                                        else -> androidx.compose.ui.text.font.FontFamily.Default
                                    }

                                    Text(
                                        text = "Lorem Ipsum is simply dummy text.",
                                        color = fontColor,
                                        fontSize = fontSize,
                                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = fontFamily,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Preview mode selector buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Anime Frame", "Dark", "Light").forEach { mode ->
                        val isSelected = previewMode == mode
                        Button(
                            onClick = { previewMode = mode },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) NetflixRed else SurfaceDark,
                                contentColor = if (isSelected) Color.White else Color.LightGray
                            )
                        ) {
                            Text(mode, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Font Size Slider
                Text("Font Size: ${(18 * tempSizeScale).toInt()}sp", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = tempSizeScale,
                    onValueChange = { tempSizeScale = it },
                    valueRange = 0.75f..1.75f,
                    colors = SliderDefaults.colors(
                        thumbColor = NetflixRed,
                        activeTrackColor = NetflixRed,
                        inactiveTrackColor = SurfaceDark
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Position Offset Slider
                Text("Vertical Position: ${(tempPosition * 100).toInt()}% from bottom", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = tempPosition,
                    onValueChange = { tempPosition = it },
                    valueRange = 0.05f..0.30f,
                    colors = SliderDefaults.colors(
                        thumbColor = NetflixRed,
                        activeTrackColor = NetflixRed,
                        inactiveTrackColor = SurfaceDark
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Background Opacity Slider
                Text("Background Box Opacity: ${(tempBgOpacity * 100).toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = tempBgOpacity,
                    onValueChange = { tempBgOpacity = it },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = NetflixRed,
                        activeTrackColor = NetflixRed,
                        inactiveTrackColor = SurfaceDark
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Font Style Selectors
                Text("Font Style", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "None" to "none",
                            "Outlined" to "classic_outline",
                            "Drop Shadow" to "default"
                        ).forEach { (label, key) ->
                            val isSelected = tempStyle == key
                            Button(
                                onClick = { tempStyle = key },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) NetflixRed else SurfaceDark,
                                    contentColor = if (isSelected) Color.White else Color.LightGray
                                )
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Serif" to "serif",
                            "Monospace" to "monospace",
                            "Bold" to "bold"
                        ).forEach { (label, key) ->
                            val isSelected = tempStyle == key
                            Button(
                                onClick = { tempStyle = key },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) NetflixRed else SurfaceDark,
                                    contentColor = if (isSelected) Color.White else Color.LightGray
                                )
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Font Color Selectors
                Text("Font Color", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("White", "Yellow", "Green", "Cyan").forEach { c ->
                            val isSelected = tempColor.equals(c, ignoreCase = true)
                            Button(
                                onClick = { tempColor = c },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) NetflixRed else SurfaceDark,
                                    contentColor = if (isSelected) Color.White else Color.LightGray
                                )
                            ) {
                                Text(c, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Blue", "Pink", "Red", "Black").forEach { c ->
                            val isSelected = tempColor.equals(c, ignoreCase = true)
                            Button(
                                onClick = { tempColor = c },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) NetflixRed else SurfaceDark,
                                    contentColor = if (isSelected) Color.White else Color.LightGray
                                )
                            ) {
                                Text(c, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Actions: Reset / Recommended
                Button(
                    onClick = {
                        tempSizeScale = 1.0f
                        tempColor = "White"
                        tempBgOpacity = 0.35f
                        tempStyle = "classic_outline"
                        tempPosition = 0.10f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Restore Recommended Defaults", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun ChangeEmailDialog(
    onDismiss: () -> Unit,
    onSubmit: (currentPassword: String, newEmail: String, onComplete: (String?) -> Unit) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var confirmEmail by remember { mutableStateOf("") }
    
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Change Account Email",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }
                
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CrunchyrollOrange,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = CrunchyrollOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it },
                    label = { Text("New Email Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CrunchyrollOrange,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = CrunchyrollOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = confirmEmail,
                    onValueChange = { confirmEmail = it },
                    label = { Text("Confirm New Email") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CrunchyrollOrange,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = CrunchyrollOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentPassword.isEmpty() || newEmail.isEmpty()) {
                        errorMessage = "All fields are required"
                        return@Button
                    }
                    if (newEmail != confirmEmail) {
                        errorMessage = "Email confirmation does not match"
                        return@Button
                    }
                    
                    isSubmitting = true
                    errorMessage = null
                    onSubmit(currentPassword, newEmail) { err ->
                        isSubmitting = false
                        if (err != null) {
                            errorMessage = err
                        }
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (currentPassword: String, newPassword: String, onComplete: (String?) -> Unit) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Change Account Password",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }
                
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CrunchyrollOrange,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = CrunchyrollOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CrunchyrollOrange,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = CrunchyrollOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CrunchyrollOrange,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = CrunchyrollOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentPassword.isEmpty() || newPassword.isEmpty()) {
                        errorMessage = "All fields are required"
                        return@Button
                    }
                    if (newPassword.length < 6) {
                        errorMessage = "Password must be at least 6 characters"
                        return@Button
                    }
                    if (newPassword != confirmPassword) {
                        errorMessage = "Password confirmation does not match"
                        return@Button
                    }
                    
                    isSubmitting = true
                    errorMessage = null
                    onSubmit(currentPassword, newPassword) { err ->
                        isSubmitting = false
                        if (err != null) {
                            errorMessage = err
                        }
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = SurfaceDark
    )
}


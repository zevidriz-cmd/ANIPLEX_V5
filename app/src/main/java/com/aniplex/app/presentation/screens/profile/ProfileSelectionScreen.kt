package com.aniplex.app.presentation.screens.profile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aniplex.app.domain.model.UserProfile
import com.aniplex.app.theme.*
import java.util.Calendar

// Backward-compatible Profile Metadata Serializer
data class ProfileSettings(
    val avatarType: String = "avatar_orange",
    val isKidsMode: Boolean = false,
    val streamQuality: String = "Ultra HD 4K",
    val autoplayNext: Boolean = true
) {
    fun toUrlString(): String {
        return "$avatarType|$isKidsMode|$streamQuality|$autoplayNext"
    }

    companion object {
        fun parse(url: String): ProfileSettings {
            if (url.isBlank()) return ProfileSettings()
            val parts = url.split("|")
            if (parts.size < 2) {
                // If legacy format without pipes, return standard mapping
                val type = if (url in listOf("avatar_orange", "avatar_blue", "avatar_purple", "avatar_green")) {
                    url
                } else if (url.startsWith("avatar_")) {
                    url
                } else {
                    "avatar_orange"
                }
                return ProfileSettings(avatarType = type)
            }
            return ProfileSettings(
                avatarType = parts.getOrNull(0) ?: "avatar_orange",
                isKidsMode = parts.getOrNull(1)?.toBoolean() ?: false,
                streamQuality = parts.getOrNull(2) ?: "Ultra HD 4K",
                autoplayNext = parts.getOrNull(3)?.toBoolean() ?: true
            )
        }
    }
}

// Premium visual themed avatars
data class PremiumAvatar(
    val id: String,
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val primaryColor: Color,
    val gradientColors: List<Color>
)

@Composable
fun getPremiumAvatars(): List<PremiumAvatar> {
    return listOf(
        PremiumAvatar(
            id = "avatar_orange",
            name = "Celestial Flame",
            icon = Icons.Default.LocalFireDepartment,
            primaryColor = CrunchyrollOrange,
            gradientColors = listOf(Color(0xFFFF9E5A), CrunchyrollOrange)
        ),
        PremiumAvatar(
            id = "avatar_blue",
            name = "Future Neo",
            icon = Icons.Default.SmartToy,
            primaryColor = Color(0xFF00E5FF),
            gradientColors = listOf(Color(0xFF80D8FF), Color(0xFF00B0FF))
        ),
        PremiumAvatar(
            id = "avatar_purple",
            name = "Cosmic Spark",
            icon = Icons.Default.AutoAwesome,
            primaryColor = Color(0xFFE91E63),
            gradientColors = listOf(Color(0xFFEA80FC), Color(0xFF9C27B0))
        ),
        PremiumAvatar(
            id = "avatar_green",
            name = "Zen Master",
            icon = Icons.Default.Lightbulb,
            primaryColor = Color(0xFF4CAF50),
            gradientColors = listOf(Color(0xFFB9F6CA), Color(0xFF4CAF50))
        ),
        PremiumAvatar(
            id = "avatar_rose",
            name = "Romance Rose",
            icon = Icons.Default.Favorite,
            primaryColor = Color(0xFFFF3366),
            gradientColors = listOf(Color(0xFFFF8A80), Color(0xFFFF3366))
        ),
        PremiumAvatar(
            id = "avatar_cyber",
            name = "Shadow Shinobi",
            icon = Icons.Default.Bolt,
            primaryColor = Color(0xFF7C4DFF),
            gradientColors = listOf(Color(0xFFB388FF), Color(0xFF7C4DFF))
        ),
        PremiumAvatar(
            id = "avatar_gold",
            name = "Golden Isekai",
            icon = Icons.Default.EmojiEvents,
            primaryColor = Color(0xFFFFD700),
            gradientColors = listOf(Color(0xFFFFE57F), Color(0xFFFFD700))
        ),
        PremiumAvatar(
            id = "avatar_neko",
            name = "Chibi Neko",
            icon = Icons.Default.Pets,
            primaryColor = Color(0xFFFF9800),
            gradientColors = listOf(Color(0xFFFFCC80), Color(0xFFFF9800))
        )
    )
}

@Composable
fun ProfileSelectionScreen(
    onProfileSelected: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isMigrating by viewModel.isMigrating.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadProfilesAndMigrate()
    }

    // Screen State
    var isManageMode by remember { mutableStateOf(false) }
    var activeProfileForPin by remember { mutableStateOf<UserProfile?>(null) }
    var isPinVerificationForManage by remember { mutableStateOf(false) }
    var showCreateEditDialog by remember { mutableStateOf<UserProfile?>(null) } 

    // Handle back button when dialog or PIN entry is open
    BackHandler(enabled = activeProfileForPin != null || showCreateEditDialog != null || isManageMode) {
        if (activeProfileForPin != null) {
            activeProfileForPin = null
            isPinVerificationForManage = false
        } else if (showCreateEditDialog != null) {
            showCreateEditDialog = null
        } else if (isManageMode) {
            isManageMode = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BackgroundVoid, Color(0xFF0F0B1E), BackgroundVoid)
                )
            )
    ) {
        // Floating Ambient Gradient Orbs under the main UI for spectacular luxury feeling
        val infiniteTransition = rememberInfiniteTransition(label = "BackgroundGlow")
        val orb1Scale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Orb1Scale"
        )
        val orb2Scale by infiniteTransition.animateFloat(
            initialValue = 1.2f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(5000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Orb2Scale"
        )

        // Top Right Glowing Orb
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-100).dp)
                .size(400.dp)
                .scale(orb1Scale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            CrunchyrollOrange.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom Left Glowing Orb
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 150.dp)
                .size(450.dp)
                .scale(orb2Scale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8A2BE2).copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        when (val state = uiState) {
            is ProfileSelectionState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = CrunchyrollOrange,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(54.dp)
                        )
                        if (isMigrating) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Preparing your adventurer profiles...",
                                color = Color.LightGray.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            is ProfileSelectionState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = ErrorColor,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.loadProfilesAndMigrate() },
                            colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is ProfileSelectionState.Success -> {
                ProfileSelectionContent(
                    profiles = state.profiles,
                    isManageMode = isManageMode,
                    onProfileClick = { profile ->
                        if (isManageMode) {
                            if (profile.pin != null) {
                                isPinVerificationForManage = true
                                activeProfileForPin = profile
                            } else {
                                showCreateEditDialog = profile
                            }
                        } else {
                            if (profile.pin != null) {
                                activeProfileForPin = profile
                            } else {
                                viewModel.selectProfile(profile, onProfileSelected)
                            }
                        }
                    },
                    onAddProfileClick = {
                        showCreateEditDialog = UserProfile() // Empty profile indicates create
                    },
                    onToggleManageMode = {
                        isManageMode = !isManageMode
                    },
                    onLogOut = {
                        viewModel.signOut(onSignOut)
                    }
                )
            }
        }

        // Dialog for PIN entry (Gorgeous Ambient Blur-Sourced PIN Entry)
        activeProfileForPin?.let { profile ->
            PinEntryOverlay(
                profile = profile,
                viewModel = viewModel,
                onPinVerified = {
                    val verifiedProfile = activeProfileForPin
                    activeProfileForPin = null
                    if (isPinVerificationForManage) {
                        isPinVerificationForManage = false
                        showCreateEditDialog = verifiedProfile
                    } else {
                        viewModel.selectProfile(profile, onProfileSelected)
                    }
                },
                onCancel = {
                    activeProfileForPin = null
                    isPinVerificationForManage = false
                },
                verifyPin = { pin -> viewModel.verifyPin(profile, pin) }
            )
        }

        // Dialog for Profile Creation / Edit
        showCreateEditDialog?.let { profile ->
            CreateEditProfileDialog(
                profile = profile,
                onDismiss = { showCreateEditDialog = null },
                onSave = { name, avatarUrl, pin, question, answer ->
                    if (profile.id.isEmpty()) {
                        viewModel.createProfile(name, avatarUrl, pin, question, answer) { result ->
                            result.onSuccess {
                                showCreateEditDialog = null
                                Toast.makeText(context, "Welcome, $name! Profile created.", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "Failed to create profile", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        viewModel.updateProfile(profile.id, name, avatarUrl, pin, question, answer) { result ->
                            result.onSuccess {
                                showCreateEditDialog = null
                                isManageMode = false
                                Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "Failed to update profile", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onDelete = if (profile.id.isNotEmpty()) {
                    {
                        viewModel.deleteProfile(profile.id) { result ->
                            result.onSuccess {
                                showCreateEditDialog = null
                                isManageMode = false
                                Toast.makeText(context, "Profile deleted successfully", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "Failed to delete profile", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else null
            )
        }
    }
}

@Composable
fun ProfileSelectionContent(
    profiles: List<UserProfile>,
    isManageMode: Boolean,
    onProfileClick: (UserProfile) -> Unit,
    onAddProfileClick: () -> Unit,
    onToggleManageMode: () -> Unit,
    onLogOut: () -> Unit
) {
    // Time-of-day Adaptive Premium Greet Headers
    val greetingText = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good Morning, Adventurer"
            in 12..16 -> "Good Afternoon, Champion"
            in 17..21 -> "Good Evening, Otaku"
            else -> "Midnight Watch, Otaku"
        }
    }

    val subtitleText = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Prepare your tea. Who's going on an adventure?"
            in 12..16 -> "Your daily watchlist is fresh. Choose your avatar:"
            in 17..21 -> "Tune in and relax. Select your space:"
            else -> "Late hours stream? Choose a companion profile:"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Styled Cinematic Header with Glow
        Text(
            text = if (isManageMode) "Configure Profiles" else greetingText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isManageMode) "Tap any profile banner below to edit its settings." else subtitleText,
            fontSize = 14.sp,
            color = Color.LightGray.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 54.dp)
        )

        // Cards layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            profiles.forEach { profile ->
                ProfileItemCard(
                    profile = profile,
                    isManageMode = isManageMode,
                    onClick = { onProfileClick(profile) }
                )
            }

            if (profiles.size < 4) {
                AddProfileItemCard(onClick = onAddProfileClick)
            }
        }

        Spacer(modifier = Modifier.height(72.dp))

        // High gloss manage button
        var isManageFocused by remember { mutableStateOf(false) }
        Button(
            onClick = onToggleManageMode,
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(48.dp)
                .onFocusChanged { isManageFocused = it.isFocused },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isManageFocused) CrunchyrollOrange else if (isManageMode) CrunchyrollOrange.copy(alpha = 0.8f) else Color(0xFF14141A),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, if (isManageFocused) Color.White else if (isManageMode) Color.Transparent else Color(0xFF33333C))
        ) {
            Icon(
                imageVector = if (isManageMode) Icons.Default.Check else Icons.Default.Settings,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isManageMode) "Done Editing" else "Manage Profiles",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        var isLogoutFocused by remember { mutableStateOf(false) }
        TextButton(
            onClick = onLogOut,
            modifier = Modifier.onFocusChanged { isLogoutFocused = it.isFocused },
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (isLogoutFocused) Color.White else CrunchyrollOrange
            )
        ) {
            Text(
                text = "Log Out Account",
                color = if (isLogoutFocused) Color.White else CrunchyrollOrange.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = if (isLogoutFocused) FontWeight.Bold else FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
fun ProfileItemCard(
    profile: UserProfile,
    isManageMode: Boolean,
    onClick: () -> Unit
) {
    val settings = remember(profile.avatarUrl) { ProfileSettings.parse(profile.avatarUrl) }
    val avatarsList = getPremiumAvatars()
    val currentAvatar = avatarsList.find { it.id == settings.avatarType } ?: avatarsList.first()

    val avatarGradient = Brush.radialGradient(currentAvatar.gradientColors)

    // Floating wobble behavior when in Manage State
    val infiniteTransition = rememberInfiniteTransition(label = "WobbleEngine")
    val wobbleAngle by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(140, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WobbleAngle"
    )

    val currentRotation = if (isManageMode) wobbleAngle else 0f

    // Scale on press interaction
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "PressedScale"
    )

    var isFocused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(if (isFocused) 1.12f else 1.0f)
    val finalScale = focusScale * pressedScale

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .graphicsLayer {
                rotationZ = currentRotation
                scaleX = finalScale
                scaleY = finalScale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // High fidelity outer ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color.Transparent, CircleShape)
                .border(
                    BorderStroke(
                        width = if (isFocused) 3.5.dp else 2.5.dp,
                        color = if (isFocused) CrunchyrollOrange else if (isManageMode) CrunchyrollOrange.copy(alpha = 0.5f) else currentAvatar.primaryColor.copy(alpha = 0.4f)
                    ),
                    CircleShape
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(avatarGradient),
                contentAlignment = Alignment.Center
            ) {
                // If it is a premium avatar, render the illustration icon, else falling back to initials
                if (profile.avatarUrl.isNotBlank() && profile.avatarUrl != "avatar_orange" && profile.avatarUrl != "avatar_blue" && profile.avatarUrl != "avatar_purple" && profile.avatarUrl != "avatar_green") {
                    Icon(
                        imageVector = currentAvatar.icon,
                        contentDescription = currentAvatar.name,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                } else {
                    // Legacy avatar representation or default initials
                    Text(
                        text = profile.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Password lock badge
                if (profile.pin != null && !isManageMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "PIN Protected",
                                tint = CrunchyrollOrange,
                                modifier = Modifier.size(14.dp)
                              )
                        }
                    }
                }

                // Edit Overlay
                if (isManageMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Settings",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Profile Name with Kids or Premium Tag indicator underneath
        Text(
            text = profile.name,
            color = if (isFocused || isManageMode) CrunchyrollOrange else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Premium feature indicators below the name
        if (!isManageMode && settings.isKidsMode) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFE91E63), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "KIDS",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AddProfileItemCard(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "AddPressedScale"
    )

    var isFocused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(if (isFocused) 1.12f else 1.0f)
    val finalScale = focusScale * pressedScale

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .graphicsLayer {
                scaleX = finalScale
                scaleY = finalScale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color.Transparent, CircleShape)
                .border(
                    BorderStroke(
                        width = if (isFocused) 3.5.dp else 2.dp,
                        color = if (isFocused) CrunchyrollOrange else Color(0xFF22222A)
                    ),
                    CircleShape
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF13131A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Profile",
                    tint = if (isFocused) CrunchyrollOrange else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Add Profile",
            color = if (isFocused) CrunchyrollOrange else Color.Gray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PinEntryOverlay(
    profile: UserProfile,
    viewModel: ProfileSelectionViewModel,
    onPinVerified: () -> Unit,
    onCancel: () -> Unit,
    verifyPin: (String) -> Boolean
) {
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Recovery states
    var isResettingPin by remember { mutableStateOf(false) }
    var recoveryMethod by remember { mutableStateOf<String?>(null) } // "question" or "password"
    var recoveryAnswerInput by remember { mutableStateOf("") }
    var recoveryPasswordInput by remember { mutableStateOf("") }
    var recoveryPasswordVisible by remember { mutableStateOf(false) }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var isRecoveryLoading by remember { mutableStateOf(false) }
    var recoverySuccess by remember { mutableStateOf(false) }

    // Shake offset animation trigger!
    val shakeOffset by animateFloatAsState(
        targetValue = if (pinError) 15f else 0f,
        animationSpec = keyframes {
            durationMillis = 350
            0f at 0
            -15f at 50
            15f at 100
            -15f at 150
            15f at 200
            -10f at 250
            5f at 300
            0f at 350
        },
        label = "ShakeError",
        finishedListener = {
            if (pinError) {
                enteredPin = ""
                pinError = false
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxHeight(0.95f)
                .fillMaxWidth(0.9f)
        ) {
            // Close / Cancel Header Option
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isResettingPin && !recoverySuccess) {
                    TextButton(
                        onClick = {
                            if (recoveryMethod != null) {
                                recoveryMethod = null
                                recoveryError = null
                            } else {
                                isResettingPin = false
                            }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CrunchyrollOrange, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back", color = CrunchyrollOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (recoverySuccess) {
                // Success recovery screen
                Spacer(modifier = Modifier.weight(0.2f))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Identity Verified Successfully!",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Profile lock has been removed for ${profile.name}. Access granted.",
                    color = Color.LightGray.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onPinVerified,
                    colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                ) {
                    Text("Unlock & Launch", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.weight(0.8f))
            } else if (isResettingPin) {
                // Reset Pin Screen
                Icon(
                    imageVector = Icons.Default.LockReset,
                    contentDescription = null,
                    tint = CrunchyrollOrange,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Recover profile: ${profile.name}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (recoveryMethod == null) {
                    // Option Selection list
                    Text(
                        text = "Choose verification option to reset profile lock:",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        if (profile.recoveryQuestion != null) {
                            Card(
                                onClick = { recoveryMethod = "question" },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF14141C)),
                                border = BorderStroke(1.dp, Color(0xFF22222E)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Help, contentDescription = null, tint = CrunchyrollOrange, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("Answer Security Question", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Verify via pre-configured challenge question.", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Card(
                            onClick = { recoveryMethod = "password" },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141C)),
                            border = BorderStroke(1.dp, Color(0xFF22222E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Password, contentDescription = null, tint = CrunchyrollOrange, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Aniplex Password Verification", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Verify via master account login password.", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else if (recoveryMethod == "question") {
                    // Answer Security Question challenge
                    Text(
                        text = "CONFIRM SECURITY CHALLENGE",
                        color = CrunchyrollOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = profile.recoveryQuestion ?: "Security Question?",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = recoveryAnswerInput,
                        onValueChange = { recoveryAnswerInput = it; recoveryError = null },
                        placeholder = { Text("Your recovery answer", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrunchyrollOrange,
                            unfocusedBorderColor = Color(0xFF2C2C35),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF101016),
                            unfocusedContainerColor = Color(0xFF101016)
                        ),
                        modifier = Modifier.fillMaxWidth(0.9f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (recoveryError != null) {
                        Text(
                            text = recoveryError ?: "",
                            color = ErrorColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            val cleanInput = recoveryAnswerInput.trim().lowercase()
                            val cleanAnswer = profile.recoveryAnswer?.trim()?.lowercase() ?: ""
                            if (cleanInput.isNotEmpty() && cleanInput == cleanAnswer) {
                                isRecoveryLoading = true
                                // Request clearing the active lock in Firestore
                                viewModel.updateProfile(
                                    profile.id,
                                    profile.name,
                                    profile.avatarUrl,
                                    "REMOVE",
                                    null,
                                    null
                                ) { result ->
                                    isRecoveryLoading = false
                                    if (result.isSuccess) {
                                        recoverySuccess = true
                                    } else {
                                        recoveryError = "Network error clearing profile lock. Please try again."
                                    }
                                }
                            } else {
                                recoveryError = "Incorrect answer. Verification failed."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange),
                        shape = RoundedCornerShape(24.dp),
                        enabled = recoveryAnswerInput.isNotBlank() && !isRecoveryLoading,
                        modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                    ) {
                        if (isRecoveryLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Verify & Unlock", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (recoveryMethod == "password") {
                    // Password authentication verification
                    Text(
                        text = "ACCOUNT PASSWORD RE-AUTH",
                        color = CrunchyrollOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Verify your account password to securely disable the profile lock.",
                        color = Color.LightGray.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = recoveryPasswordInput,
                        onValueChange = { recoveryPasswordInput = it; recoveryError = null },
                        placeholder = { Text("Aniplex Account Password", color = Color.Gray) },
                        singleLine = true,
                        visualTransformation = if (recoveryPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrunchyrollOrange,
                            unfocusedBorderColor = Color(0xFF2C2C35),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF101016),
                            unfocusedContainerColor = Color(0xFF101016)
                        ),
                        trailingIcon = {
                            IconButton(onClick = { recoveryPasswordVisible = !recoveryPasswordVisible }) {
                                Icon(
                                    imageVector = if (recoveryPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (recoveryError != null) {
                        Text(
                            text = recoveryError ?: "",
                            color = ErrorColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            isRecoveryLoading = true
                            viewModel.verifyAccountPassword(recoveryPasswordInput) { successful ->
                                if (successful) {
                                    // Password is correct, disable PIN lock online
                                    viewModel.updateProfile(
                                        profile.id,
                                        profile.name,
                                        profile.avatarUrl,
                                        "REMOVE",
                                        null,
                                        null
                                    ) { result ->
                                        isRecoveryLoading = false
                                        if (result.isSuccess) {
                                            recoverySuccess = true
                                        } else {
                                            recoveryError = "Network error clearing profile lock. Please try again."
                                        }
                                    }
                                } else {
                                    isRecoveryLoading = false
                                    recoveryError = "Incorrect login password. Verification failed."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange),
                        shape = RoundedCornerShape(24.dp),
                        enabled = recoveryPasswordInput.isNotBlank() && !isRecoveryLoading,
                        modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                    ) {
                        if (isRecoveryLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Confirm & Disable Lock", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Pin Entry Panel (Default)
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = CrunchyrollOrange,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Enter Profile PIN Lock",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = "Accessing profile: ${profile.name}",
                    color = Color.LightGray.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
                )

                // Pin dots with shake offset
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.offset(x = shakeOffset.dp)
                ) {
                    for (i in 0 until 4) {
                        val filled = i < enteredPin.length
                        val dotColor = if (pinError) ErrorColor else if (filled) CrunchyrollOrange else Color(0xFF33333F)
                        val scaleFactor = if (filled) 1.25f else 1.0f

                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .scale(scaleFactor)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(
                                    1.dp,
                                    if (filled) Color.Transparent else Color(0xFF444455),
                                    CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Premium frosted key panel layout
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    val digits = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "⌫")
                    )

                    digits.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(0.85f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { char ->
                                var isKeyFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .onFocusChanged { isKeyFocused = it.isFocused }
                                        .clickable {
                                            if (pinError) return@clickable

                                            when (char) {
                                                "C" -> enteredPin = ""
                                                "⌫" -> {
                                                    if (enteredPin.isNotEmpty()) {
                                                        enteredPin = enteredPin.dropLast(1)
                                                    }
                                                }
                                                else -> {
                                                    if (enteredPin.length < 4) {
                                                        enteredPin += char
                                                        if (enteredPin.length == 4) {
                                                            if (verifyPin(enteredPin)) {
                                                                onPinVerified()
                                                            } else {
                                                                pinError = true
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .background(
                                            when {
                                                isKeyFocused -> CrunchyrollOrange
                                                char == "C" || char == "⌫" -> Color.Transparent
                                                else -> Color(0xFF14141C)
                                            }
                                        )
                                        .border(
                                            width = if (isKeyFocused) 2.5.dp else 1.dp,
                                            color = when {
                                                isKeyFocused -> Color.White
                                                char == "C" || char == "⌫" -> Color.Transparent
                                                else -> Color(0xFF25252F)
                                            },
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = char,
                                        color = if (isKeyFocused) Color.White else if (char == "C" || char == "⌫") CrunchyrollOrange else Color.White,
                                        fontSize = if (char == "C" || char == "⌫") 16.sp else 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Forgot password button
                TextButton(
                    onClick = { isResettingPin = true }
                ) {
                    Text(
                        text = "Forgot PIN?",
                        color = CrunchyrollOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CreateEditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (name: String, avatarUrl: String, pin: String?, recoveryQuestion: String?, recoveryAnswer: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val initialSettings = remember(profile.avatarUrl) { ProfileSettings.parse(profile.avatarUrl) }

    var name by remember { mutableStateOf(profile.name) }
    var selectedAvatarId by remember { mutableStateOf(initialSettings.avatarType) }
    var isKidsMode by remember { mutableStateOf(initialSettings.isKidsMode) }
    var streamQuality by remember { mutableStateOf(initialSettings.streamQuality) }
    var autoplayNext by remember { mutableStateOf(initialSettings.autoplayNext) }
    
    var isPinEnabled by remember { mutableStateOf(profile.pin != null) }
    var pinText by remember { mutableStateOf("") }

    val defaultQuestion = "What was your very first anime?"
    var recoveryQuestion by remember { mutableStateOf(profile.recoveryQuestion ?: defaultQuestion) }
    var recoveryAnswer by remember { mutableStateOf(profile.recoveryAnswer ?: "") }
    var isCustomQuestionSelected by remember { mutableStateOf(profile.recoveryQuestion != null && profile.recoveryQuestion != "What was your very first anime?" && profile.recoveryQuestion != "Who is your favorite anime character?" && profile.recoveryQuestion != "What was the name of your first pet?") }
    var customQuestionInput by remember { mutableStateOf(if (isCustomQuestionSelected) profile.recoveryQuestion ?: "" else "") }
    
    val premiumCandidates = getPremiumAvatars()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (profile.id.isEmpty()) "Premium Profile Setup" else "Configure Profile Settings",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                // Name Input Section
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 12) name = it },
                        label = { Text("Adventurer Name", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrunchyrollOrange,
                            unfocusedBorderColor = Color(0xFF2C2C35),
                            focusedLabelColor = CrunchyrollOrange,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF101016),
                            unfocusedContainerColor = Color(0xFF101016)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Avatar curation selector space
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose Streaming Avatar Character:",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 2-row horizontal grid or simple wrapping Row for premium avatars
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val firstHalf = premiumCandidates.take(4)
                        val secondHalf = premiumCandidates.takeLast(4)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            firstHalf.forEach { avatar ->
                                var isAvatarFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Brush.radialGradient(avatar.gradientColors))
                                        .onFocusChanged { isAvatarFocused = it.isFocused }
                                        .clickable { selectedAvatarId = avatar.id }
                                        .border(
                                            width = 3.dp,
                                            color = if (isAvatarFocused) CrunchyrollOrange else if (selectedAvatarId == avatar.id) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = avatar.icon,
                                        contentDescription = avatar.name,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            secondHalf.forEach { avatar ->
                                var isAvatarFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Brush.radialGradient(avatar.gradientColors))
                                        .onFocusChanged { isAvatarFocused = it.isFocused }
                                        .clickable { selectedAvatarId = avatar.id }
                                        .border(
                                            width = 3.dp,
                                            color = if (isAvatarFocused) CrunchyrollOrange else if (selectedAvatarId == avatar.id) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = avatar.icon,
                                        contentDescription = avatar.name,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Show Selected Avatar Bio Name
                item {
                    val activeAvatar = premiumCandidates.find { it.id == selectedAvatarId }
                    if (activeAvatar != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(activeAvatar.primaryColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, activeAvatar.primaryColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = activeAvatar.icon,
                                    contentDescription = null,
                                    tint = activeAvatar.primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Selected Class: ${activeAvatar.name}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Divider separating layout
                item {
                    HorizontalDivider(color = Color(0xFF2C2C35))
                }

                // Kids Protected Mode Segment
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isKidsMode) Icons.Default.ChildCare else Icons.Default.Face,
                                contentDescription = null,
                                tint = if (isKidsMode) Color(0xFFE91E63) else Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Enable Junior Safe Mode",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Restricts content to friendly titles only",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Switch(
                            checked = isKidsMode,
                            onCheckedChange = { isKidsMode = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFE91E63),
                                checkedTrackColor = Color(0xFFE91E63).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF2A2A34)
                            )
                        )
                    }
                }

                // Streaming Quality Preset Selection
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Stream Quality Preference:",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val qualities = listOf("Ultra HD 4K", "1080p FHD", "Data Saver")
                            qualities.forEach { q ->
                                val selected = streamQuality == q
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) CrunchyrollOrange else Color(0xFF14141C))
                                        .border(
                                            1.dp,
                                            if (selected) Color.Transparent else Color(0xFF2C2C35),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { streamQuality = q }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = q,
                                        color = if (selected) Color.White else Color.Gray,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Autoplay Next Switch Option
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleOutline,
                                contentDescription = null,
                                tint = if (autoplayNext) CrunchyrollOrange else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Continuous Autoplay",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Continuously plays the next episode online",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Switch(
                            checked = autoplayNext,
                            onCheckedChange = { autoplayNext = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CrunchyrollOrange,
                                checkedTrackColor = CrunchyrollOrange.copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF2A2A34)
                            )
                        )
                    }
                }

                item {
                    HorizontalDivider(color = Color(0xFF2C2C35))
                }

                // PIN Protection Settings
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isPinEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isPinEnabled) CrunchyrollOrange else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Profile Lock PIN",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = isPinEnabled,
                            onCheckedChange = { isPinEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CrunchyrollOrange,
                                checkedTrackColor = CrunchyrollOrange.copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF2A2A34)
                            )
                        )
                    }
                }

                if (isPinEnabled) {
                    item {
                        OutlinedTextField(
                            value = pinText,
                            onValueChange = {
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    pinText = it
                                }
                            },
                            label = {
                                Text(
                                    text = if (profile.pin != null && pinText.isEmpty()) "Leave empty to keep current PIN" else "Enter 4-Digit PIN",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrunchyrollOrange,
                                unfocusedBorderColor = Color(0xFF2C2C35),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF101016),
                                unfocusedContainerColor = Color(0xFF101016)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(
                                text = "Setup Security Recovery Question",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Used if you ever forget your profile lock PIN code.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )

                            val questions = listOf(
                                "What was your very first anime?",
                                "Who is your favorite anime character?",
                                "What was the name of your first pet?",
                                "Type custom question..."
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                questions.forEach { question ->
                                    val isSelected = if (question == "Type custom question...") {
                                        isCustomQuestionSelected
                                    } else {
                                        !isCustomQuestionSelected && recoveryQuestion == question
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFF14141F) else Color.Transparent)
                                            .border(1.dp, if (isSelected) CrunchyrollOrange else Color(0xFF2C2C35), RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (question == "Type custom question...") {
                                                    isCustomQuestionSelected = true
                                                    recoveryQuestion = customQuestionInput
                                                } else {
                                                    isCustomQuestionSelected = false
                                                    recoveryQuestion = question
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                if (question == "Type custom question...") {
                                                    isCustomQuestionSelected = true
                                                    recoveryQuestion = customQuestionInput
                                                } else {
                                                    isCustomQuestionSelected = false
                                                    recoveryQuestion = question
                                                }
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = CrunchyrollOrange, unselectedColor = Color.Gray)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(question, color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }

                            if (isCustomQuestionSelected) {
                                OutlinedTextField(
                                    value = customQuestionInput,
                                    onValueChange = {
                                        customQuestionInput = it
                                        recoveryQuestion = it
                                    },
                                    label = { Text("Enter Custom Security Question", color = Color.Gray, fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CrunchyrollOrange,
                                        unfocusedBorderColor = Color(0xFF2C2C35),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color(0xFF101016),
                                        unfocusedContainerColor = Color(0xFF101016)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = recoveryAnswer,
                                onValueChange = { recoveryAnswer = it },
                                label = { Text("Security Question Answer", color = Color.Gray, fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CrunchyrollOrange,
                                    unfocusedBorderColor = Color(0xFF2C2C35),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF101016),
                                    unfocusedContainerColor = Color(0xFF101016)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            var isSaveFocused by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val finalPin = if (isPinEnabled) {
                        if (pinText.isNotEmpty()) pinText else null 
                    } else {
                        "REMOVE" 
                    }

                    if (isPinEnabled && profile.pin == null && pinText.length != 4) {
                        return@Button
                    }

                    if (isPinEnabled && (recoveryQuestion.isBlank() || recoveryAnswer.isBlank())) {
                        return@Button
                    }

                    // Pack settings to composite URL String
                    val compositeAvatarUrl = ProfileSettings(
                        avatarType = selectedAvatarId,
                        isKidsMode = isKidsMode,
                        streamQuality = streamQuality,
                        autoplayNext = autoplayNext
                    ).toUrlString()

                    onSave(
                        name,
                        compositeAvatarUrl,
                        finalPin,
                        if (isPinEnabled) recoveryQuestion else null,
                        if (isPinEnabled) recoveryAnswer else null
                    )
                },
                modifier = Modifier.onFocusChanged { isSaveFocused = it.isFocused },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSaveFocused) CrunchyrollOrange else Color(0xFF22222A),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, if (isSaveFocused) Color.White else Color.Transparent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply & Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    var isDeleteFocused by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.onFocusChanged { isDeleteFocused = it.isFocused },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (isDeleteFocused) Color.White else ErrorColor
                        )
                    ) {
                        Text(
                            text = "Delete Profile", 
                            color = if (isDeleteFocused) Color.White else ErrorColor,
                            fontWeight = if (isDeleteFocused) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                var isCancelFocused by remember { mutableStateOf(false) }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.onFocusChanged { isCancelFocused = it.isFocused },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isCancelFocused) Color.White else Color.LightGray
                    )
                ) {
                    Text(
                        text = "Cancel", 
                        color = if (isCancelFocused) Color.White else Color.LightGray,
                        fontWeight = if (isCancelFocused) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        },
        containerColor = SurfaceDark
    )
}

fun getAvatarColor(avatarUrl: String): Color {
    val decoded = ProfileSettings.parse(avatarUrl)
    return when (decoded.avatarType) {
        "avatar_orange" -> CrunchyrollOrange
        "avatar_blue" -> Color(0xFF00E5FF)
        "avatar_purple" -> Color(0xFFE91E63)
        "avatar_green" -> Color(0xFF4CAF50)
        "avatar_rose" -> Color(0xFFFF3366)
        "avatar_cyber" -> Color(0xFF7C4DFF)
        "avatar_gold" -> Color(0xFFFFD700)
        "avatar_neko" -> Color(0xFFFF9800)
        else -> Color(0xFF757575)
    }
}

fun getAvatarGradient(avatarUrl: String): Brush {
    val decoded = ProfileSettings.parse(avatarUrl)
    return when (decoded.avatarType) {
        "avatar_orange" -> Brush.radialGradient(listOf(Color(0xFFFF9E5A), CrunchyrollOrange))
        "avatar_blue" -> Brush.radialGradient(listOf(Color(0xFF80D8FF), Color(0xFF00B0FF)))
        "avatar_purple" -> Brush.radialGradient(listOf(Color(0xFFEA80FC), Color(0xFF9C27B0)))
        "avatar_green" -> Brush.radialGradient(listOf(Color(0xFFB9F6CA), Color(0xFF4CAF50)))
        "avatar_rose" -> Brush.radialGradient(listOf(Color(0xFFFF8A80), Color(0xFFFF3366)))
        "avatar_cyber" -> Brush.radialGradient(listOf(Color(0xFFB388FF), Color(0xFF7C4DFF)))
        "avatar_gold" -> Brush.radialGradient(listOf(Color(0xFFFFE57F), Color(0xFFFFD700)))
        "avatar_neko" -> Brush.radialGradient(listOf(Color(0xFFFFCC80), Color(0xFFFF9800)))
        else -> Brush.radialGradient(listOf(Color(0xFFB5B5C1), Color(0xFF757575)))
    }
}

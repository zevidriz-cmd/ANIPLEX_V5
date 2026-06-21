package com.aniplex.app.presentation.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import com.aniplex.app.Home
import com.aniplex.app.Login
import com.aniplex.app.ProfileSelection
import com.aniplex.app.R
import com.aniplex.app.theme.BackgroundVoid
import com.aniplex.app.theme.CrunchyrollOrange
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(key1 = true) {
        // Animate scale and alpha simultaneously
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
        
        // Wait on the splash screen for a moment
        delay(1200)

        // Check auth state from ViewModel
        if (viewModel.isUserLoggedIn) {
            onNavigate(ProfileSelection(showBack = false))
        } else {
            onNavigate(Login)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundVoid),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alpha.value)
        ) {
            // App Icon Logo (Generated Premium Logo)
            Image(
                painter = painterResource(id = R.drawable.ic_aniplex_logo),
                contentDescription = "ANIPLEX Logo",
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale.value)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App Title Text
            Text(
                text = "ANIPLEX",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Cinematic Anime Streaming",
                fontSize = 14.sp,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )
        }

        // Circular progress at the bottom
        CircularProgressIndicator(
            color = CrunchyrollOrange,
            strokeWidth = 3.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .size(36.dp)
        )
    }
}

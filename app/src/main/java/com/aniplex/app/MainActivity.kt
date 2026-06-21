package com.aniplex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.aniplex.app.data.download.DownloadManager
import com.aniplex.app.data.local.preferences.ProfileManager
import com.aniplex.app.theme.ANIPLEXTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var profileManager: ProfileManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DownloadManager.loadDownloads(applicationContext)

    lifecycleScope.launch {
      profileManager.activeProfile.collect { profile ->
        DownloadManager.setActiveProfileId(profile?.id)
      }
    }

    enableEdgeToEdge()
    setContent {
      ANIPLEXTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}


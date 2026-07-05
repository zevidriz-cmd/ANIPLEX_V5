package com.aniplex.app

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
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
  @Inject lateinit var preferenceManager: com.aniplex.app.data.local.preferences.PreferenceManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DownloadManager.loadDownloads(applicationContext)

    lifecycleScope.launch {
      profileManager.activeProfile.collect { profile ->
        DownloadManager.setActiveProfileId(profile?.id)
      }
    }

    val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
    val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    enableEdgeToEdge()
    setContent {
      val updateViewModel: com.aniplex.app.presentation.screens.update.UpdateViewModel = hiltViewModel()
      val updateInfo by updateViewModel.updateInfo.collectAsState()
      val isDownloading by updateViewModel.isDownloading.collectAsState()
      val downloadProgress by updateViewModel.downloadProgress.collectAsState()
      val context = LocalContext.current

      LaunchedEffect(Unit) {
        updateViewModel.checkForUpdates()
      }

      val accentColorNameState = preferenceManager.accentColorFlow.collectAsState(
        initial = preferenceManager.preferredAccentColor
      )
      val accentColor = com.aniplex.app.theme.getAccentColor(accentColorNameState.value)

      ANIPLEXTheme(accentColor = accentColor) { 
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
          MainNavigation(isTv = isTv) 

          updateInfo?.let { info ->
            if (!updateViewModel.checkDismissed) {
              com.aniplex.app.presentation.components.UpdateDialog(
                updateInfo = info,
                isDownloading = isDownloading,
                downloadProgress = downloadProgress,
                onUpgrade = { updateViewModel.downloadAndInstall(context, info.downloadUrl) },
                onCancel = { updateViewModel.dismissUpdate() }
              )
            }
          }
        } 
      }
    }
  }
}


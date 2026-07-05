package com.aniplex.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Splash : NavKey

@Serializable
data object Login : NavKey

@Serializable
data class ProfileSelection(val showBack: Boolean = false) : NavKey

@Serializable
data object Home : NavKey

@Serializable
data object Search : NavKey

@Serializable
data class Detail(val animeId: String) : NavKey

@Serializable
data class Player(
      val episodeId: String,
      val animeId: String,
      val animeTitle: String,
      val episodeNumber: Int,
      val category: String = "sub",
      val resumePlayback: Boolean = false,
      val initialProgress: Long = 0L
  ) : NavKey

@Serializable
data object Browse : NavKey

@Serializable
data object Schedule : NavKey

@Serializable
data object Watchlist : NavKey

@Serializable
data object History : NavKey

@Serializable
data object Profile : NavKey

@Serializable
data object Downloads : NavKey

@Serializable
data object QrScanner : NavKey

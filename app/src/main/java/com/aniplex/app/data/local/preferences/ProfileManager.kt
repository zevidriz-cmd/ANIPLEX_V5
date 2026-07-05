package com.aniplex.app.data.local.preferences

import com.aniplex.app.domain.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferenceManager: PreferenceManager
) {
    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    private var activeProfileListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        // Load active profile from preferences on startup if user is logged in
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val savedId = preferenceManager.getSelectedProfileId(userId)
            if (savedId != null) {
                _activeProfile.value = UserProfile(id = savedId)
                startActiveProfileListener(userId, savedId)
            }
        }
    }

    private fun startActiveProfileListener(userId: String, profileId: String) {
        activeProfileListener?.remove()
        activeProfileListener = firestore.collection("users").document(userId)
            .collection("profiles").document(profileId)
            .addSnapshotListener { doc, error ->
                if (error != null) return@addSnapshotListener
                if (doc != null && doc.exists()) {
                    val name = doc.getString("name") ?: ""
                    val avatarUrl = doc.getString("avatarUrl") ?: ""
                    val pin = doc.getString("pin")
                    val recoveryQuestion = doc.getString("recoveryQuestion")
                    val recoveryAnswer = doc.getString("recoveryAnswer")
                    _activeProfile.value = UserProfile(profileId, name, avatarUrl, pin, recoveryQuestion, recoveryAnswer)

                    @Suppress("UNCHECKED_CAST")
                    val settings = doc.get("settings") as? Map<String, Any>
                    if (settings != null) {
                        applySettingsFromFirestore(settings)
                    }
                }
            }
    }

    private fun applySettingsFromFirestore(settings: Map<String, Any>) {
        (settings["defaultAudio"] as? String)?.let {
            preferenceManager.defaultAudioCategory = it
        }
        (settings["autoplayNextEpisode"] as? Boolean)?.let {
            preferenceManager.autoplayNextEpisode = it
        }
        (settings["preferredQuality"] as? String)?.let {
            preferenceManager.preferredQuality = it
        }
        (settings["skipIntro"] as? Boolean)?.let {
            preferenceManager.skipIntro = it
        }
        (settings["skipOutro"] as? Boolean)?.let {
            preferenceManager.skipOutro = it
        }
        (settings["playbackSpeed"] as? Number)?.let {
            preferenceManager.playbackSpeed = it.toFloat()
        }
        (settings["preferredProvider"] as? String)?.let {
            preferenceManager.preferredProvider = it
        }
        (settings["hevcDecoderEnabled"] as? Boolean)?.let {
            preferenceManager.hevcDecoderEnabled = it
        }
        (settings["dolbyAtmosEnabled"] as? Boolean)?.let {
            preferenceManager.dolbyAtmosEnabled = it
        }
        (settings["preferredAccentColor"] as? String)?.let {
            preferenceManager.preferredAccentColor = it
        }

        @Suppress("UNCHECKED_CAST")
        val subtitles = settings["subtitles"] as? Map<String, Any>
        if (subtitles != null) {
            (subtitles["enabled"] as? Boolean)?.let {
                preferenceManager.subtitlesEnabled = it
            }
            (subtitles["sizeScale"] as? Number)?.let {
                preferenceManager.subtitleSizeScale = it.toFloat()
            }
            (subtitles["color"] as? String)?.let {
                preferenceManager.subtitleColor = it
            }
            (subtitles["bgOpacity"] as? Number)?.let {
                preferenceManager.subtitleBgOpacity = it.toFloat()
            }
            (subtitles["style"] as? String)?.let {
                preferenceManager.subtitleStyle = it
            }
            (subtitles["position"] as? Number)?.let {
                preferenceManager.subtitlePosition = it.toFloat()
            }
        }
    }

    fun saveSettingsToFirestore() {
        val userId = auth.currentUser?.uid ?: return
        val profileId = _activeProfile.value?.id ?: return

        val settingsMap = hashMapOf(
            "defaultAudio" to preferenceManager.defaultAudioCategory,
            "autoplayNextEpisode" to preferenceManager.autoplayNextEpisode,
            "preferredQuality" to preferenceManager.preferredQuality,
            "skipIntro" to preferenceManager.skipIntro,
            "skipOutro" to preferenceManager.skipOutro,
            "playbackSpeed" to preferenceManager.playbackSpeed,
            "preferredProvider" to preferenceManager.preferredProvider,
            "hevcDecoderEnabled" to preferenceManager.hevcDecoderEnabled,
            "dolbyAtmosEnabled" to preferenceManager.dolbyAtmosEnabled,
            "preferredAccentColor" to preferenceManager.preferredAccentColor,
            "subtitles" to hashMapOf(
                "enabled" to preferenceManager.subtitlesEnabled,
                "sizeScale" to preferenceManager.subtitleSizeScale,
                "color" to preferenceManager.subtitleColor,
                "bgOpacity" to preferenceManager.subtitleBgOpacity,
                "style" to preferenceManager.subtitleStyle,
                "position" to preferenceManager.subtitlePosition
            )
        )

        firestore.collection("users").document(userId)
            .collection("profiles").document(profileId)
            .set(mapOf("settings" to settingsMap), SetOptions.merge())
    }

    fun selectProfile(profile: UserProfile?) {
        activeProfileListener?.remove()
        activeProfileListener = null

        _activeProfile.value = profile
        val userId = auth.currentUser?.uid ?: return
        preferenceManager.setSelectedProfileId(userId, profile?.id)

        if (profile != null) {
            startActiveProfileListener(userId, profile.id)
        }
    }

    fun clearActiveProfile() {
        activeProfileListener?.remove()
        activeProfileListener = null
        _activeProfile.value = null
        val userId = auth.currentUser?.uid ?: return
        preferenceManager.setSelectedProfileId(userId, null)
    }

    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun getProfiles(userId: String): List<UserProfile> {
        return try {
            val snapshot = firestore.collection("users").document(userId)
                .collection("profiles").get().await()
            snapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val name = doc.getString("name") ?: ""
                val avatarUrl = doc.getString("avatarUrl") ?: ""
                val pin = doc.getString("pin")
                val recoveryQuestion = doc.getString("recoveryQuestion")
                val recoveryAnswer = doc.getString("recoveryAnswer")
                UserProfile(id, name, avatarUrl, pin, recoveryQuestion, recoveryAnswer)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createProfile(userId: String, name: String, avatarUrl: String, rawPin: String?, recoveryQuestion: String?, recoveryAnswer: String?): Result<UserProfile> {
        return try {
            val profiles = getProfiles(userId)
            if (profiles.size >= 4) {
                return Result.failure(Exception("Maximum 4 profiles allowed per account"))
            }

            val id = UUID.randomUUID().toString()
            val hashedPin = if (!rawPin.isNullOrBlank()) hashPin(rawPin) else null
            val profileMap = hashMapOf(
                "id" to id,
                "name" to name,
                "avatarUrl" to avatarUrl,
                "pin" to hashedPin,
                "recoveryQuestion" to if (hashedPin != null) recoveryQuestion else null,
                "recoveryAnswer" to if (hashedPin != null) recoveryAnswer else null
            )

            firestore.collection("users").document(userId)
                .collection("profiles").document(id)
                .set(profileMap)
                .await()

            Result.success(UserProfile(id, name, avatarUrl, hashedPin, recoveryQuestion, recoveryAnswer))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(userId: String, profileId: String, name: String, avatarUrl: String, rawPin: String?, recoveryQuestion: String?, recoveryAnswer: String?): Result<Unit> {
        return try {
            val currentDoc = firestore.collection("users").document(userId)
                .collection("profiles").document(profileId).get().await()

            val existingPin = currentDoc.getString("pin")
            val existingQuestion = currentDoc.getString("recoveryQuestion")
            val existingAnswer = currentDoc.getString("recoveryAnswer")

            val hashedPin = if (rawPin == "REMOVE") {
                null
            } else if (!rawPin.isNullOrBlank()) {
                hashPin(rawPin)
            } else {
                existingPin
            }

            val finalQuestion = if (rawPin == "REMOVE") {
                null
            } else if (!rawPin.isNullOrBlank()) {
                recoveryQuestion
            } else {
                existingQuestion
            }

            val finalAnswer = if (rawPin == "REMOVE") {
                null
            } else if (!rawPin.isNullOrBlank()) {
                recoveryAnswer
            } else {
                existingAnswer
            }

            val profileMap = hashMapOf(
                "name" to name,
                "avatarUrl" to avatarUrl,
                "pin" to hashedPin,
                "recoveryQuestion" to finalQuestion,
                "recoveryAnswer" to finalAnswer
            )

            firestore.collection("users").document(userId)
                .collection("profiles").document(profileId)
                .set(profileMap, SetOptions.merge())
                .await()

            // If this was the active profile, update activeProfile StateFlow
            if (_activeProfile.value?.id == profileId) {
                _activeProfile.value = UserProfile(profileId, name, avatarUrl, hashedPin, finalQuestion, finalAnswer)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProfile(userId: String, profileId: String): Result<Unit> {
        return try {
            firestore.collection("users").document(userId)
                .collection("profiles").document(profileId)
                .delete()
                .await()

            // Clean up profile subcollections (watchlist, history, ratings)
            // Wait, we can let Firestore rules handles deletion or do it client-side.
            // Client-side clean up of profile items:
            val watchlist = firestore.collection("users").document(userId)
                .collection("profiles").document(profileId).collection("watchlist").get().await()
            for (doc in watchlist.documents) {
                doc.reference.delete().await()
            }
            val history = firestore.collection("users").document(userId)
                .collection("profiles").document(profileId).collection("history").get().await()
            for (doc in history.documents) {
                doc.reference.delete().await()
            }
            val ratings = firestore.collection("users").document(userId)
                .collection("profiles").document(profileId).collection("ratings").get().await()
            for (doc in ratings.documents) {
                doc.reference.delete().await()
            }

            if (_activeProfile.value?.id == profileId) {
                clearActiveProfile()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkAndMigrateUser(userId: String) {
        try {
            val profilesCollection = firestore.collection("users").document(userId)
                .collection("profiles")
            val profilesSnapshot = profilesCollection.get().await()

            if (profilesSnapshot.isEmpty) {
                // 1. Create a default profile
                val currentUser = auth.currentUser
                val defaultName = currentUser?.displayName?.ifBlank { null }
                    ?: currentUser?.email?.substringBefore("@")?.ifBlank { null }
                    ?: "Boss"
                val defaultAvatar = "avatar_orange" // default orange theme avatar
                val defaultProfileId = UUID.randomUUID().toString()

                val defaultProfileMap = hashMapOf(
                    "id" to defaultProfileId,
                    "name" to defaultName,
                    "avatarUrl" to defaultAvatar,
                    "pin" to null
                )
                profilesCollection.document(defaultProfileId).set(defaultProfileMap).await()

                // 2. Migrate watchlist
                val oldWatchlist = firestore.collection("users").document(userId)
                    .collection("watchlist").get().await()
                for (doc in oldWatchlist.documents) {
                    val data = doc.data
                    if (data != null) {
                        profilesCollection.document(defaultProfileId)
                            .collection("watchlist").document(doc.id)
                            .set(data).await()
                        doc.reference.delete().await()
                    }
                }

                // 3. Migrate history
                val oldHistory = firestore.collection("users").document(userId)
                    .collection("history").get().await()
                for (doc in oldHistory.documents) {
                    val data = doc.data
                    if (data != null) {
                        profilesCollection.document(defaultProfileId)
                            .collection("history").document(doc.id)
                            .set(data).await()
                        doc.reference.delete().await()
                    }
                }

                // 4. Migrate ratings
                val oldRatings = firestore.collection("users").document(userId)
                    .collection("ratings").get().await()
                for (doc in oldRatings.documents) {
                    val data = doc.data
                    if (data != null) {
                        profilesCollection.document(defaultProfileId)
                            .collection("ratings").document(doc.id)
                            .set(data).await()
                        doc.reference.delete().await()
                    }
                }
            }
        } catch (e: Exception) {
            // Log or ignore migration failure
        }
    }
}

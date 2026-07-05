package com.aniplex.app.presentation.screens.auth

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniplex.app.theme.BackgroundVoid
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.SurfaceDark
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.tasks.await
import java.util.UUID

@Composable
fun TvLoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firestore = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }

    val sessionId = remember { UUID.randomUUID().toString().substring(0, 8).uppercase() }
    var loginStatus by remember { mutableStateOf("Generating QR Code...") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(sessionId) {
        try {
            // Write session init to Firestore
            val sessionData = hashMapOf(
                "status" to "pending",
                "sessionId" to sessionId,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("temp_logins").document(sessionId)
                .set(sessionData)
                .await()

            // Generate QR Code containing the sessionId
            val size = 512
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(sessionId, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            qrBitmap = bitmap
            loginStatus = "Waiting for authorization from mobile app..."

            // Set up Firestore listener
            firestore.collection("temp_logins").document(sessionId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        loginStatus = "Connection error: ${error.localizedMessage}"
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val status = snapshot.getString("status")
                        if (status == "success") {
                            val provider = snapshot.getString("provider") ?: "email"
                            loginStatus = "Authorizing credentials..."

                            if (provider == "google") {
                                val idToken = snapshot.getString("idToken") ?: ""
                                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                                auth.signInWithCredential(credential)
                                    .addOnSuccessListener {
                                        loginStatus = "Signed in successfully!"
                                        firestore.collection("temp_logins").document(sessionId).delete()
                                        onLoginSuccess()
                                    }
                                    .addOnFailureListener { e ->
                                        loginStatus = "Auth failed: ${e.localizedMessage}"
                                        firestore.collection("temp_logins").document(sessionId)
                                            .update("status", "pending")
                                    }
                            } else {
                                val email = snapshot.getString("email") ?: ""
                                val password = snapshot.getString("password") ?: ""
                                auth.signInWithEmailAndPassword(email, password)
                                    .addOnSuccessListener {
                                        loginStatus = "Signed in successfully!"
                                        firestore.collection("temp_logins").document(sessionId).delete()
                                        onLoginSuccess()
                                    }
                                    .addOnFailureListener { e ->
                                        loginStatus = "Auth failed: ${e.localizedMessage}"
                                        firestore.collection("temp_logins").document(sessionId)
                                            .update("status", "pending")
                                    }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            loginStatus = "Error starting session: ${e.localizedMessage}"
        }
    }

    // Cleanup session if screen is exited
    DisposableEffect(Unit) {
        onDispose {
            firestore.collection("temp_logins").document(sessionId).delete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundVoid),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Left column: Instructions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Link AniStream TV App",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "1. Open the AniStream app on your mobile phone.\n" +
                           "2. Tap the Profile icon and select 'Scan TV QR Code'.\n" +
                           "3. Scan this QR code to authenticate instantly.\n\n" +
                           "Activation Session Code: $sessionId",
                    fontSize = 18.sp,
                    color = Color.LightGray,
                    lineHeight = 28.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = loginStatus,
                        fontSize = 16.sp,
                        color = CrunchyrollOrange,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Right column: QR Code container
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                qrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Login QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: CircularProgressIndicator(color = CrunchyrollOrange)
            }
        }
    }
}

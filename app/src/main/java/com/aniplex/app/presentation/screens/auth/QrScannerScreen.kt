package com.aniplex.app.presentation.screens.auth

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@androidx.camera.core.ExperimentalGetImage
@Composable
fun QrScannerScreen(
    onCodeScanned: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }

    var scannedSessionId by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLinking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Google Sign-In setup for TV Linking
    val googleSignInClient = remember {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.aniplex.app.R.string.default_web_client_id))
            .requestEmail()
            .build()
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val idToken = account.idToken
            if (!idToken.isNullOrBlank()) {
                val payload = hashMapOf(
                    "status" to "success",
                    "provider" to "google",
                    "idToken" to idToken
                )
                firestore.collection("temp_logins").document(scannedSessionId ?: "")
                    .set(payload)
                    .addOnSuccessListener {
                        Toast.makeText(context, "TV App Linked successfully!", Toast.LENGTH_SHORT).show()
                        isLinking = false
                        scannedSessionId = null
                        onBackClick()
                    }
                    .addOnFailureListener { err ->
                        isLinking = false
                        errorMessage = "Database update failed: ${err.localizedMessage}"
                    }
            } else {
                isLinking = false
                errorMessage = "Google authentication failed: ID Token was null."
            }
        } catch (e: Exception) {
            isLinking = false
            errorMessage = "Google verification failed: ${e.localizedMessage ?: e.message}"
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan TV QR Code", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                
                DisposableEffect(Unit) {
                    onDispose {
                        cameraExecutor.shutdown()
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().apply {
                                setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val scannerOptions = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                            val scanner = BarcodeScanning.getClient(scannerOptions)

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && scannedSessionId == null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                barcode.rawValue?.let { code ->
                                                    // Set scannedSessionId to pause analyzer and show prompt
                                                    if (scannedSessionId == null) {
                                                        scannedSessionId = code
                                                        onCodeScanned(code)
                                                    }
                                                }
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("QrScannerScreen", "Barcode scanning failed", e)
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                Log.e("QrScannerScreen", "Use case binding failed", e)
                            }

                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Camera permission is required to scan QR Code",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Authentication Confirmation Dialog
            scannedSessionId?.let { sessionId ->
                val currentUser = auth.currentUser
                if (currentUser == null || currentUser.email.isNullOrEmpty()) {
                    AlertDialog(
                        onDismissRequest = { scannedSessionId = null },
                        title = { Text("Sign In Required") },
                        text = { Text("You must be logged into your account to link a TV app.") },
                        confirmButton = {
                            TextButton(onClick = { scannedSessionId = null }) {
                                Text("OK")
                            }
                        }
                    )
                } else {
                    val isGoogleUser = currentUser.providerData.any { it.providerId == "google.com" }
                    if (isGoogleUser) {
                        AlertDialog(
                            onDismissRequest = { if (!isLinking) scannedSessionId = null },
                            title = { Text("Authorize TV Login") },
                            text = {
                                Column {
                                    Text("Confirm linking TV session code: $sessionId\n\nSince you are signed in with Google, you need to authorize using your Google account.")
                                    errorMessage?.let { error ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = error, color = Color.Red, fontSize = 13.sp)
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    enabled = !isLinking,
                                    onClick = {
                                        isLinking = true
                                        errorMessage = null
                                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                    }
                                ) {
                                    if (isLinking) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                    } else {
                                        Text("Authorize with Google")
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    enabled = !isLinking,
                                    onClick = { scannedSessionId = null }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    } else {
                        AlertDialog(
                            onDismissRequest = { if (!isLinking) scannedSessionId = null },
                            title = { Text("Authorize TV Login") },
                            text = {
                                Column {
                                    Text("Confirm linking TV session code: $sessionId\n\nEnter your AniStream password to authorize:")
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text("Password") },
                                        singleLine = true,
                                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        trailingIcon = {
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(
                                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Toggle password visibility"
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    errorMessage?.let { error ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = error, color = Color.Red, fontSize = 13.sp)
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    enabled = password.isNotEmpty() && !isLinking,
                                    onClick = {
                                        isLinking = true
                                        errorMessage = null
                                        
                                        // 1. Re-authenticate user to verify password is correct before writing
                                        val credential = EmailAuthProvider.getCredential(currentUser.email!!, password)
                                        currentUser.reauthenticate(credential)
                                            .addOnSuccessListener {
                                                // 2. Write verification payload to temp_logins
                                                val payload = hashMapOf(
                                                    "status" to "success",
                                                    "provider" to "email",
                                                    "email" to currentUser.email!!,
                                                    "password" to password
                                                )
                                                firestore.collection("temp_logins").document(sessionId)
                                                    .set(payload)
                                                    .addOnSuccessListener {
                                                        Toast.makeText(context, "TV App Linked successfully!", Toast.LENGTH_SHORT).show()
                                                        isLinking = false
                                                        scannedSessionId = null
                                                        onBackClick()
                                                    }
                                                    .addOnFailureListener { err ->
                                                        isLinking = false
                                                        errorMessage = "Database update failed: ${err.localizedMessage}"
                                                    }
                                            }
                                            .addOnFailureListener { err ->
                                                isLinking = false
                                                errorMessage = "Invalid password: ${err.localizedMessage}"
                                            }
                                    }
                                ) {
                                    if (isLinking) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                    } else {
                                        Text("Authorize")
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    enabled = !isLinking,
                                    onClick = { scannedSessionId = null }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

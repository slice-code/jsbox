package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                SplashContent(
                    onPermissionGranted = {
                        ProjectStorage.bootstrapDemoProject(this)
                        navigateToDashboard()
                    }
                )
            }
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@Composable
fun SplashContent(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }

    // Helper to check permission based on API level
    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            Environment.isExternalStorageManager()
        } else {
            // Android 10 and below
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Initialize and check
    LaunchedEffect(Unit) {
        hasPermission = checkPermission()
        if (hasPermission) {
            delay(1200) // Beautiful cinematic brief delay
            onPermissionGranted()
        } else {
            showRationaleDialog = true
        }
    }

    // Launchers for Requesting Permissions
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasPermission = true
            onPermissionGranted()
        } else {
            Toast.makeText(
                context,
                "Akses Penyimpanan Ditolak. Aplikasi menggunakan folder lokal demi kestabilan.",
                Toast.LENGTH_LONG
            ).show()
            // Continue using internal fallback storage
            onPermissionGranted()
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (checkPermission()) {
            hasPermission = true
            onPermissionGranted()
        } else {
            Toast.makeText(
                context,
                "Akses Penyimpanan Ditolak. Aplikasi menggunakan folder lokal demi kestabilan.",
                Toast.LENGTH_LONG
            ).show()
            // Continue using internal fallback storage
            onPermissionGranted()
        }
    }

    fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                // If special setting intent is missing on customized emulator, request regular storage or fallback
                legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C20),
                        Color(0xFF15102A),
                        Color(0xFF080614)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Centered Animated Logo Icon with glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF6200EE).copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "JS Sandbox Logo",
                    tint = Color(0xFF03DAC6),
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Application Title
            Text(
                text = "JS-Sandbox",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.5.sp
            )

            Text(
                text = "APP BUILDER & RUNTIME",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8888AA),
                letterSpacing = 3.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Subtitle state / Dialog
            if (showRationaleDialog && !hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1F1B35).copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Peringatan Izin",
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(36.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Izin Akses File Diperlukan",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Aplikasi ini menyimpan file code (main.js, service.js) di folder Documents publik luar. Hal ini memudahkan Anda mengedit isi file dari editor teks PC atau File Manager eksternal pilihan Anda.",
                            fontSize = 14.sp,
                            color = Color(0xFFBEBED0),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                showRationaleDialog = false
                                requestStorageAccess()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF03DAC6),
                                contentColor = Color(0xFF121212)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Minta Izin Akses",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = {
                                showRationaleDialog = false
                                // Automatically fallback to internal Storage
                                onPermissionGranted()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF8888AA)
                            )
                        ) {
                            Text("Gunakan Penyimpanan Internal Saja")
                        }
                    }
                }
            } else {
                // Beautiful pulsating progress loader
                CircularProgressIndicator(
                    color = Color(0xFF03DAC6),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

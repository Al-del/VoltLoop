package com.example.voltloop.QRScanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voltloop.NetworkStuff.getUsers
import kotlinx.coroutines.launch

data class CameraPermissionState(
    val isGranted: Boolean,
    val shouldShowRationale: Boolean,
    val requestPermission: () -> Unit
)

expect class CameraPermissionHelper() {
    @Composable
    fun getPermissionState(): CameraPermissionState
}

expect class QRCameraRenderer() {
    @Composable
    fun Render(onQRCodeScanned: (String) -> Unit, modifier: Modifier)
}


@Composable
fun rememberCameraPermissionState(): CameraPermissionState =
    remember { CameraPermissionHelper() }.getPermissionState()

@Composable
fun QRCameraView(onQRCodeScanned: (String) -> Unit, modifier: Modifier = Modifier) =
    remember { QRCameraRenderer() }.Render(onQRCodeScanned, modifier)


@Composable
fun QRScannerScreen(onResult: (String) -> Unit = {}) {
    val permissionState = rememberCameraPermissionState()
    var scannedValue by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        when {
            permissionState.isGranted -> {
                if (isScanning) {
                    QRCameraView(
                        onQRCodeScanned = { result ->
                            scannedValue = result
                            isScanning = false
                            onResult(result)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    ScannerOverlay()
                } else {
                    scannedValue?.let { value ->
                        ResultScreen(
                            result = value,
                            onScanAgain = {
                                scannedValue = null
                                isScanning = true
                            }
                        )
                    }
                }
            }

            permissionState.shouldShowRationale -> {
                PermissionRationaleScreen(onRequestPermission = permissionState.requestPermission)
            }

            else -> {
                PermissionRequestScreen(onRequestPermission = permissionState.requestPermission)
            }
        }
    }
}


@Composable
private fun ScannerOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Scan QR Code", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Point your camera at a QR code", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }

        Box(modifier = Modifier.size(260.dp).align(Alignment.Center)) {
            CornerBrackets()
        }

        Text(
            text = "Scanning automatically…",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
        )
    }
}

@Composable
private fun CornerBrackets() {
    val color = Color(0xFF00E5FF)
    val strokeWidth = 4.dp
    val cornerSize = 32.dp
    val radius = 8.dp

    listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)
        .forEach { alignment ->
            Box(modifier = Modifier.fillMaxSize()) {
                val isStart = alignment == Alignment.TopStart || alignment == Alignment.BottomStart
                Box(
                    modifier = Modifier
                        .width(cornerSize).height(strokeWidth)
                        .clip(RoundedCornerShape(radius)).background(color)
                        .align(alignment)
                        .let { if (isStart) it.padding(start = 0.dp) else it.padding(end = 0.dp) }
                )
                Box(
                    modifier = Modifier
                        .width(strokeWidth).height(cornerSize)
                        .clip(RoundedCornerShape(radius)).background(color)
                        .align(alignment)
                )
            }
        }
}


@Composable
private fun ResultScreen(result: String, onScanAgain: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✅", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text("QR Code Detected!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E2E))
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(
                text = result,
                color = Color(0xFF00E5FF),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
             /*
              TODO: Raspberry pi request

              */
                scope.launch {
                    /* DONE: GET REQUEST HOW TO DO
                    try {
                        val users = getUsers()
                        users.forEach { user ->
                            println("USERS_SUCCESS: Name: ${user.name} | Email: ${user.email}")
                        }
                    } catch (e: Exception) {
                        println("USERS_ERROR: ${e.message}")  // 👈 this
                    }
                    */




                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Accept!", color = Color.Black, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onScanAgain,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Reject!", color = Color.Black, fontWeight = FontWeight.SemiBold)
        }
    }
}


@Composable
private fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📷", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Camera Access Needed", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "We need camera permission to scan QR codes.",
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PermissionRationaleScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Permission Required", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Camera permission is required to scan QR codes. Please grant it in your device settings.",
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Open Settings", color = Color.Black, fontWeight = FontWeight.SemiBold)
        }
    }
}
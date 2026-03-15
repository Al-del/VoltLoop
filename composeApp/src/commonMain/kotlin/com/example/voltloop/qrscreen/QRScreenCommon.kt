package com.example.voltloop.QRScanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voltloop.NetworkStuff.getUsers
import com.example.voltloop.TimerScreen
import kotlinx.coroutines.launch

// ── Shared palette (mirrors AccountScreen) ───────────────────
private val BlueAccent  = Color(0xFF43BBF7)
private val BlueSoft    = Color(0xFFE8F6FD)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecond  = Color(0xFF888888)
private val PageBg      = Color(0xFFF4F6F8)

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
    var isScanning   by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        when {
            permissionState.isGranted -> {
                if (isScanning) {
                    QRCameraView(
                        onQRCodeScanned = { result ->
                            scannedValue = result
                            isScanning   = false
                            onResult(result)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    ScannerOverlay()
                } else {
                    scannedValue?.let { value ->
                        ResultScreen(
                            result      = value,
                            onScanAgain = {
                                scannedValue = null
                                isScanning   = true
                            }
                        )
                    }
                }
            }

            permissionState.shouldShowRationale ->
                PermissionRationaleScreen(onRequestPermission = permissionState.requestPermission)

            else ->
                PermissionRequestScreen(onRequestPermission = permissionState.requestPermission)
        }
    }
}


// ── Scanner overlay ───────────────────────────────────────────
@Composable
private fun ScannerOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {

        // Top header card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.White.copy(alpha = 0.92f))
                .padding(top = 52.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconCircle(icon = Icons.Filled.QrCodeScanner, size = 52)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Scan QR Code",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = TextPrimary
                )
                Text(
                    "Point your camera at a VoltLoop QR code",
                    fontSize = 13.sp,
                    color    = TextSecond
                )
            }
        }

        // Viewfinder bracket
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.Center)
        ) {
            CornerBrackets()
        }

        // Bottom hint
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.White.copy(alpha = 0.92f))
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Scanning automatically…",
                color      = TextSecond,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CornerBrackets() {
    val strokeWidth = 4.dp
    val cornerSize  = 32.dp
    val radius      = 8.dp

    listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)
        .forEach { alignment ->
            Box(modifier = Modifier.fillMaxSize()) {
                val isStart = alignment == Alignment.TopStart || alignment == Alignment.BottomStart
                Box(
                    modifier = Modifier
                        .width(cornerSize).height(strokeWidth)
                        .clip(RoundedCornerShape(radius))
                        .background(BlueAccent)
                        .align(alignment)
                        .let { if (isStart) it.padding(start = 0.dp) else it.padding(end = 0.dp) }
                )
                Box(
                    modifier = Modifier
                        .width(strokeWidth).height(cornerSize)
                        .clip(RoundedCornerShape(radius))
                        .background(BlueAccent)
                        .align(alignment)
                )
            }
        }
}


// ── Result screen ─────────────────────────────────────────────
@Composable
private fun ResultScreen(result: String, onScanAgain: () -> Unit) {
    val scope     = rememberCoroutineScope()
    var showTimer by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    if (showTimer) {
        TimerScreen()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(top = 52.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconCircle(
                    icon     = Icons.Filled.CheckCircle,
                    size     = 64,
                    iconSize = 32,
                    shadow   = true
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "QR Code Detected!",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = TextPrimary
                )
                Text("Review the result below", fontSize = 13.sp, color = TextSecond)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Result card
        Surface(
            modifier        = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape           = RoundedCornerShape(20.dp),
            color           = Color.White,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconCircle(icon = Icons.Filled.Bolt, size = 44)
                Spacer(Modifier.width(14.dp))
                Text(
                    text       = result,
                    color      = BlueAccent,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f),
                    maxLines   = 3
                )
            }
        }

        if (errorMsg != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                errorMsg!!,
                color     = Color(0xFFE53935),
                fontSize  = 13.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        // Accept button
        Button(
            onClick  = {
                scope.launch {
                    isLoading = true
                    errorMsg  = null
                    try {
                        val users = getUsers()
                        users.forEach { user ->
                            println("USERS_SUCCESS: Name: ${user.name} | Email: ${user.email}")
                        }
                        showTimer = true
                    } catch (e: Exception) {
                        println("USERS_ERROR: ${e.message}")
                        errorMsg = "Something went wrong. Please try again."
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled  = !isLoading,
            colors   = ButtonDefaults.buttonColors(containerColor = BlueAccent),
            shape    = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(54.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color       = Color.White,
                    modifier    = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    imageVector        = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Accept", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Reject button
        OutlinedButton(
            onClick  = onScanAgain,
            shape    = RoundedCornerShape(16.dp),
            border   = androidx.compose.foundation.BorderStroke(1.5.dp, BlueAccent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(54.dp)
        ) {
            Icon(
                imageVector        = Icons.Filled.Close,
                contentDescription = null,
                tint               = BlueAccent,
                modifier           = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Reject & Scan Again", color = BlueAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}


// ── Permission screens ────────────────────────────────────────
@Composable
private fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    PermissionScreen(
        icon        = Icons.Filled.CameraAlt,
        title       = "Camera Access Needed",
        body        = "VoltLoop needs camera permission to scan ride QR codes.",
        buttonLabel = "Grant Permission",
        onAction    = onRequestPermission
    )
}

@Composable
private fun PermissionRationaleScreen(onRequestPermission: () -> Unit) {
    PermissionScreen(
        icon        = Icons.Filled.Lock,
        title       = "Permission Required",
        body        = "Camera permission is required to scan QR codes. Please grant it in your device settings.",
        buttonLabel = "Open Settings",
        onAction    = onRequestPermission
    )
}

@Composable
private fun PermissionScreen(
    icon: ImageVector,
    title: String,
    body: String,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(top = 52.dp, bottom = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconCircle(icon = icon, size = 64, iconSize = 30)
                Spacer(Modifier.height(12.dp))
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    body,
                    fontSize  = 13.sp,
                    color     = TextSecond,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick  = onAction,
            colors   = ButtonDefaults.buttonColors(containerColor = BlueAccent),
            shape    = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(54.dp)
        ) {
            Text(buttonLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}


// ── Reusable icon-in-circle ───────────────────────────────────
@Composable
private fun IconCircle(
    icon: ImageVector,
    size: Int,
    iconSize: Int = size / 2,
    shadow: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .then(
                if (shadow) Modifier.shadow(
                    8.dp, CircleShape,
                    ambientColor = BlueAccent.copy(alpha = 0.3f),
                    spotColor    = BlueAccent.copy(alpha = 0.3f)
                ) else Modifier
            )
            .clip(CircleShape)
            .background(BlueSoft),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = BlueAccent,
            modifier           = Modifier.size(iconSize.dp)
        )
    }
}
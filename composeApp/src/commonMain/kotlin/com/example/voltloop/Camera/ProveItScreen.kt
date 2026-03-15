package com.example.voltloop.Camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voltloop.NetworkStuff.submitProof
import kotlinx.coroutines.launch

private val BgDark      = Color(0xFF0D0F14)
private val SurfaceDark = Color(0xFF161920)
private val AccentCyan  = Color(0xFF00E5FF)
private val AccentGreen = Color(0xFF00FF9C)
private val AccentRed   = Color(0xFFFF3D5A)
private val TextPrimary = Color(0xFFE8EAF0)
private val TextMuted   = Color(0xFF6B7280)

private enum class CaptureState { PREVIEWING, CAPTURED, UPLOADING, SUCCESS, ERROR }

@Composable
fun ProveItScreen(
    challengeText: String,
    onProved: (base64: String) -> Unit,
    onDismiss: () -> Unit
) {
    var captureState  by remember { mutableStateOf(CaptureState.PREVIEWING) }
    var capturedB64   by remember { mutableStateOf<String?>(null) }
    var errorMessage  by remember { mutableStateOf<String?>(null) }
    var proofShape    by remember { mutableStateOf<String?>(null) }
    val scope         = rememberCoroutineScope()

    val helper = remember { PhotoCaptureHelper() }

    Box(modifier = Modifier.fillMaxSize().background(BgDark)) {

        if (captureState == CaptureState.PREVIEWING) {
            helper.Render(
                modifier = Modifier.fillMaxSize(),
                onDismiss = onDismiss,
                onPhotoCaptured = { base64 ->
                    capturedB64  = base64
                    captureState = CaptureState.CAPTURED
                }
            )

            ChallengeLabelOverlay(text = challengeText)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.TopStart
            ) {
                IconTextButton(label = "Cancel", icon = Icons.Default.Close, onClick = onDismiss)
            }
        }

        if (captureState == CaptureState.CAPTURED) {
            ReviewScreen(
                challengeText = challengeText,
                onConfirm = {
                    captureState = CaptureState.UPLOADING
                    capturedB64?.let { b64 ->
                        println("BASE64_IMAGE: $b64")
                        scope.launch {
                            try {
                                val response = submitProof(b64, challengeText)
                                proofShape = "Score: ${response.similarity}"
                                if (response.accepted) {
                                    onProved(b64)
                                    captureState = CaptureState.SUCCESS
                                } else {
                                    errorMessage = "Not accepted (score: ${response.similarity})"

                                    captureState = CaptureState.ERROR
                                }

                            } catch (e: Exception) {
                                println("SUBMIT_ERROR: ${e.message}")
                                errorMessage = e.message ?: "Upload failed"
                                captureState = CaptureState.ERROR
                            }
                        }
                    }
                },
                onRetake = {
                    capturedB64  = null
                    captureState = CaptureState.PREVIEWING
                }
            )
        }

        if (captureState == CaptureState.UPLOADING) {
            LoadingOverlay(message = "Submitting proof…")
        }

        if (captureState == CaptureState.SUCCESS) {
            ResultOverlay(
                icon     = Icons.Default.CheckCircle,
                title    = "Proof submitted!",
                subtitle = proofShape,
                color    = AccentGreen,
                onClose  = onDismiss
            )
        }

        if (captureState == CaptureState.ERROR) {
            ResultOverlay(
                icon     = Icons.Default.Cancel,
                title    = errorMessage ?: "Something went wrong",
                subtitle = if (errorMessage?.contains("score") == true) "Try taking a clearer photo that matches the challenge" else null,
                color    = AccentRed,
                onClose  = {
                    capturedB64  = null          // <-- clear the old photo
                    captureState = CaptureState.PREVIEWING  // <-- go back to camera
                    errorMessage = null
                }
            )
        }
    }
}

@Composable
private fun ChallengeLabelOverlay(text: String) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.7f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Glowing pill label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(AccentCyan.copy(alpha = 0.15f))
                    .border(1.dp, AccentCyan.copy(alpha = pulse), RoundedCornerShape(50.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "PROVE IT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = AccentCyan
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgDark.copy(alpha = 0.82f))
                    .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = text,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ReviewScreen(
    challengeText: String,
    onConfirm: () -> Unit,
    onRetake: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Camera,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(64.dp)
        )

        Text(
            text = "Photo captured!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        // Challenge reminder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = challengeText,
                color = AccentCyan,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = "Does this photo prove the challenge?",
            color = TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Retake
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                border = androidx.compose.foundation.BorderStroke(1.dp, TextMuted.copy(alpha = 0.3f))
            ) {
                Text("Retake", fontWeight = FontWeight.SemiBold)
            }

            // Submit
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(2f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor   = BgDark
                )
            ) {
                Text("Submit Proof", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CircularProgressIndicator(color = AccentCyan, strokeWidth = 3.dp)
            Text(message, color = TextMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ResultOverlay(icon: ImageVector, title: String, subtitle: String?, color: Color, onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(64.dp)
            )
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = BgDark)
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun IconTextButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
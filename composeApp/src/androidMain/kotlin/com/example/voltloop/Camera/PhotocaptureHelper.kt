package com.example.voltloop.Camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

actual class PhotoCaptureHelper actual constructor() {

    private var imageCaptureUseCase: ImageCapture? = null

    @Composable
    actual fun Render(
        onPhotoCaptured: (base64: String) -> Unit,
        onDismiss: () -> Unit,
        modifier: Modifier
    ) {
        val context        = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val executor       = remember { Executors.newSingleThreadExecutor() }

        val previewView = remember { PreviewView(context) }

        // Bind CameraX use-cases
        LaunchedEffect(Unit) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCaptureUseCase = imageCapture

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            }, ContextCompat.getMainExecutor(context))
        }

        Box(modifier = modifier) {
            // Live viewfinder
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Shutter button at the bottom centre
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                ShutterButton(onClick = {
                    capturePhoto(context, executor, onPhotoCaptured)
                })
            }
        }
    }

    private fun capturePhoto(
        context: Context,
        executor: java.util.concurrent.Executor,
        onPhotoCaptured: (String) -> Unit
    ) {
        val imageCapture = imageCaptureUseCase ?: return

        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes  = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    // Decode → compress to JPEG at 80 % quality → Base64
                    val bitmap     = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                    image.close()
                    onPhotoCaptured(base64)
                }

                override fun onError(exception: ImageCaptureException) {
                    println("CAPTURE_ERROR: ${exception.message}")
                }
            }
        )
    }
}

// ─── Shutter button ───────────────────────────────────────────────────────────
@Composable
private fun ShutterButton(onClick: () -> Unit) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(3.dp, Color.White, CircleShape)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
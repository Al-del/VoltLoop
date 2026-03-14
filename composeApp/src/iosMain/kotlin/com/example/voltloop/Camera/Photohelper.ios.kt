package com.example.voltloop.Camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.Foundation.*
import platform.UIKit.*
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual class PhotoCaptureHelper actual constructor() {

    // AVFoundation capture session
    private val session     = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private var delegate: PhotoDelegate? = null

    @Composable
    actual fun Render(
        onPhotoCaptured: (base64: String) -> Unit,
        onDismiss: () -> Unit,
        modifier: Modifier
    ) {
        // Set up the AVCaptureSession once
        LaunchedEffect(Unit) {
            setupSession()
        }

        Box(modifier = modifier) {
            // Native camera preview layer embedded via UIKitView
            UIKitView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    val uiView = UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
                    val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
                        videoGravity = AVLayerVideoGravityResizeAspectFill
                    }
                    uiView.layer.addSublayer(previewLayer)
                    session.startRunning()
                    uiView
                },
                update = { view ->
                    // Keep the preview layer filling the UIView
                    (view.layer.sublayers?.firstOrNull() as? AVCaptureVideoPreviewLayer)
                        ?.frame = view.bounds
                }
            )

            // Shutter button
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                ShutterButton(onClick = {
                    capturePhoto(onPhotoCaptured)
                })
            }
        }
    }

    private fun setupSession() {
        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetPhoto

        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        val input  = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return

        if (session.canAddInput(input))  session.addInput(input)
        if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput)

        session.commitConfiguration()
    }

    private fun capturePhoto(onPhotoCaptured: (String) -> Unit) {
        val settings = AVCapturePhotoSettings.photoSettings()
        val d = PhotoDelegate { base64 -> onPhotoCaptured(base64) }
        delegate = d
        photoOutput.capturePhotoWithSettings(settings, d as AVCapturePhotoCaptureDelegateProtocol)
    }
}

// ─── AVCapturePhotoCaptureDelegate ───────────────────────────────────────────
private class PhotoDelegate(
    private val onResult: (String) -> Unit
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {

    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?
    ) {
        if (error != null) {
            println("IOS_CAPTURE_ERROR: ${error.localizedDescription}")
            return
        }
        val data   = didFinishProcessingPhoto.fileDataRepresentation() ?: return
        val base64 = data.base64EncodedStringWithOptions(0u)
        onResult(base64)
    }
}

// ─── Shutter button ───────────────────────────────────────────────────────────
@Composable
private fun ShutterButton(onClick: () -> Unit) {
    IconButton(
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
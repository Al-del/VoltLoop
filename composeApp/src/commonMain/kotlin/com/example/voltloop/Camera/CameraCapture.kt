package com.example.voltloop.Camera

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable

/**
 * expect/actual mirrors the QRCameraRenderer pattern already in your project.
 * Android actual  → uses CameraX + ImageCapture
 * iOS actual      → uses UIImagePickerController
 */
expect class PhotoCaptureHelper() {
    /**
     * Renders a full-screen camera viewfinder.
     * [onPhotoCaptured] is called with the Base64-encoded JPEG string.
     */
    @Composable
    fun Render(
        onPhotoCaptured: (base64: String) -> Unit,
        onDismiss: () -> Unit,
        modifier: Modifier
    )
}
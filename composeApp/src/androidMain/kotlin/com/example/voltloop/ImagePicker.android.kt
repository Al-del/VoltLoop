package com.example.voltloop

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class ImagePicker(
    private val launcher: androidx.activity.result.ActivityResultLauncher<String>,
    private val contentResolver: ContentResolver
) {
    private var onResultCallback: ((ByteArray?) -> Unit)? = null

    init {
        // Internal bridge to pass the URI back to our callback
        internalCallback = { uri ->
            if (uri == null) {
                onResultCallback?.invoke(null)
            } else {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    onResultCallback?.invoke(bytes)
                } catch (e: Exception) {
                    println("IMAGE_PICKER_ERROR: ${e.message}")
                    onResultCallback?.invoke(null)
                }
            }
        }
    }

    actual fun launch(onResult: (ByteArray?) -> Unit) {
        onResultCallback = onResult
        launcher.launch("image/*") // Launch Android photo picker / gallery
    }

    companion object {
        internal var internalCallback: ((Uri?) -> Unit)? = null
    }
}

@Composable
actual fun rememberImagePicker(): ImagePicker {
    val context = LocalContext.current
    val contentResolver = remember { context.contentResolver }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        ImagePicker.internalCallback?.invoke(uri)
    }

    return remember(launcher, contentResolver) {
        ImagePicker(launcher, contentResolver)
    }
}

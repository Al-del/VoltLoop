package com.example.voltloop

/**
 * Cross-platform image picker.
 * Call [launch] to open the gallery. The callback receives raw PNG/JPEG bytes,
 * or null if the user cancelled.
 */
expect class ImagePicker {
    fun launch(onResult: (ByteArray?) -> Unit)
}

/** Creates a platform image picker, hoisted at the composable call site. */
@androidx.compose.runtime.Composable
expect fun rememberImagePicker(): ImagePicker

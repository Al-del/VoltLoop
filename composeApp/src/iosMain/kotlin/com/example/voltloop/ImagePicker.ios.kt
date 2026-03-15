package com.example.voltloop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual class ImagePicker {
    private val delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
        private var onResult: ((ByteArray?) -> Unit)? = null

        fun setup(callback: (ByteArray?) -> Unit) {
            this.onResult = callback
        }

        override fun imagePickerController(
            picker: UIImagePickerController,
            didFinishPickingMediaWithInfo: Map<Any?, *>
        ) {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
            if (image != null) {
                // Compress image to JPEG
                val data = UIImageJPEGRepresentation(image, 0.8)
                if (data != null) {
                    val bytes = ByteArray(data.length.toInt())
                    bytes.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), data.bytes, data.length)
                    }
                    onResult?.invoke(bytes)
                } else {
                    onResult?.invoke(null)
                }
            } else {
                onResult?.invoke(null)
            }
            picker.dismissViewControllerAnimated(true, null)
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            onResult?.invoke(null)
            picker.dismissViewControllerAnimated(true, null)
        }
    }

    actual fun launch(onResult: (ByteArray?) -> Unit) {
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        delegate.setup(onResult)
        picker.delegate = delegate

        // Find root view controller to present the picker
        val window = UIApplication.sharedApplication.windows.firstOrNull { 
            val uiWindow = it as? platform.UIKit.UIWindow
            uiWindow?.isKeyWindow() == true 
        } as? platform.UIKit.UIWindow
        
        val rootVc = window?.rootViewController ?: UIApplication.sharedApplication.keyWindow?.rootViewController
        
        rootVc?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun rememberImagePicker(): ImagePicker {
    return remember { ImagePicker() }
}

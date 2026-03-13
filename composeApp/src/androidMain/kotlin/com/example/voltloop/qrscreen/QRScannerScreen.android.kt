package com.example.voltloop.QRScanner
import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.voltloop.QRScanner.CameraPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors


actual class CameraPermissionHelper actual constructor() {
    @Composable
    actual fun getPermissionState(): CameraPermissionState {
        val context = LocalContext.current
        var isGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
            )
        }
        var shouldShowRationale by remember { mutableStateOf(false) }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            isGranted = granted
            shouldShowRationale = !granted
        }

        return CameraPermissionState(
            isGranted = isGranted,
            shouldShowRationale = shouldShowRationale,
            requestPermission = { launcher.launch(Manifest.permission.CAMERA) }
        )
    }
}


actual class QRCameraRenderer actual constructor() {
    @Composable
    actual fun Render(onQRCodeScanned: (String) -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val executor = remember { Executors.newSingleThreadExecutor() }
        var lastScanned by remember { mutableStateOf("") }

        DisposableEffect(Unit) {
            onDispose { executor.shutdown() }
        }

        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val previewView = PreviewView(ctx)

                ProcessCameraProvider.getInstance(ctx).also { future ->
                    future.addListener({
                        val cameraProvider = future.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            processImageProxy(imageProxy) { result ->
                                if (result != lastScanned) {
                                    lastScanned = result
                                    onQRCodeScanned(result)
                                }
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }

                previewView
            }
        )
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy, onResult: (String) -> Unit) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        BarcodeScanning.getClient()
            .process(image)
            .addOnSuccessListener { barcodes ->
                barcodes
                    .firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                    ?.rawValue
                    ?.let { onResult(it) }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
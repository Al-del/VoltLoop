@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package com.example.voltloop.QRScanner

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.example.voltloop.QRScanner.CameraPermissionState
import platform.AVFoundation.*
import platform.Foundation.NSURL
import platform.UIKit.*


actual class CameraPermissionHelper actual constructor() {
    @Composable
    actual fun getPermissionState(): CameraPermissionState {
        var status by remember {
            mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo))
        }

        val isGranted = status == AVAuthorizationStatusAuthorized
        val isDenied = status == AVAuthorizationStatusDenied || status == AVAuthorizationStatusRestricted

        return CameraPermissionState(
            isGranted = isGranted,
            shouldShowRationale = isDenied,
            requestPermission = {
                if (status == AVAuthorizationStatusNotDetermined) {
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                        status = if (granted) AVAuthorizationStatusAuthorized
                        else AVAuthorizationStatusDenied
                    }
                } else if (isDenied) {
                    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
                    if (url != null) UIApplication.sharedApplication.openURL(url)
                }
            }
        )
    }
}


actual class QRCameraRenderer actual constructor() {
    @Composable
    actual fun Render(onQRCodeScanned: (String) -> Unit, modifier: Modifier) {
        val callbackRef = rememberUpdatedState(onQRCodeScanned)

        UIKitView(
            modifier = modifier,
            factory = {
                val controller = QRScannerViewController()
                controller.onScanned = { result -> callbackRef.value(result) }
                controller.view
            }
        )
    }
}


class QRScannerViewController :
    UIViewController(nibName = null, bundle = null),
    AVCaptureMetadataOutputObjectsDelegateProtocol {

    var onScanned: ((String) -> Unit)? = null

    private val session = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null

    override fun viewDidLoad() {
        super.viewDidLoad()
        setupCamera()
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    private fun setupCamera() {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        val input = try {
            AVCaptureDeviceInput(device = device, error = null)
        } catch (e: Exception) { return }

        session.beginConfiguration()
        if (session.canAddInput(input)) session.addInput(input)

        val output = AVCaptureMetadataOutput()
        if (session.canAddOutput(output)) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(this, queue = platform.darwin.dispatch_get_main_queue())
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        }
        session.commitConfiguration()

        val layer = AVCaptureVideoPreviewLayer(session = session)
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        layer.frame = view.bounds
        view.layer.insertSublayer(layer, atIndex = 0u)
        previewLayer = layer

        platform.darwin.dispatch_async(platform.darwin.dispatch_get_global_queue(0, 0u)) {
            session.startRunning()
        }
    }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        val obj = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject ?: return
        val value = obj.stringValue ?: return
        onScanned?.invoke(value)
    }
}
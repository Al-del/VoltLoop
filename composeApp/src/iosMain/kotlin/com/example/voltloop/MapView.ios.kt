package com.example.voltloop

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.MapKit.*
import platform.UIKit.*
import platform.Foundation.*
import platform.CoreGraphics.*
import platform.darwin.NSObject
import platform.objc.sel_registerName
import kotlinx.cinterop.BetaInteropApi

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun MapView(
    modifier: Modifier,
    batteries: List<BatteryLocation>,
    onMapClick: ((Double, Double) -> Unit)?
) {
    val locationManager = remember { CLLocationManager() }
    
    LaunchedEffect(Unit) {
        locationManager.requestWhenInUseAuthorization()
    }

    // Load multiple sizes to handle different zoom levels - Increased default sizes
    val batteryImageSmall = remember { createResizedImage("one_battery", 40.0) }  // Was 30.0
    val batteryImageMedium = remember { createResizedImage("one_battery", 55.0) } // Was 45.0
    val batteryImageLarge = remember { createResizedImage("one_battery", 70.0) }  // Was 60.0

    val combinedDelegate = remember {
        object : NSObject(), MKMapViewDelegateProtocol, UIGestureRecognizerDelegateProtocol {
            var hasZoomedToUser = false

            override fun mapView(mapView: MKMapView, didUpdateUserLocation: MKUserLocation) {
                val location = didUpdateUserLocation.location
                if (!hasZoomedToUser && location != null) {
                    val region = MKCoordinateRegionMakeWithDistance(
                        didUpdateUserLocation.coordinate,
                        1000.0, // 1km zoom level
                        1000.0
                    )
                    mapView.setRegion(region, animated = true)
                    hasZoomedToUser = true
                }
            }

            override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
                // Update all visible annotations when zoom level changes
                mapView.annotations.forEach { annotation ->
                    if (annotation !is MKUserLocation) {
                        val view = mapView.viewForAnnotation(annotation as MKAnnotationProtocol)
                        mapView.region.useContents {
                            updateAnnotationImage(view, span.latitudeDelta)
                        }
                    }
                }
            }

            override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
                if (viewForAnnotation is MKUserLocation) return null
                
                val identifier = "Battery"
                var annotationView = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
                
                if (annotationView == null) {
                    annotationView = MKAnnotationView(viewForAnnotation, identifier)
                    annotationView.canShowCallout = true
                } else {
                    annotationView.annotation = viewForAnnotation
                }
                
                mapView.region.useContents {
                    updateAnnotationImage(annotationView, span.latitudeDelta)
                }
                return annotationView
            }

            private fun updateAnnotationImage(view: MKAnnotationView?, latitudeDelta: Double) {
                if (view == null) return
                
                // Adjust size based on latitudeDelta (smaller delta = more zoomed in)
                view.image = when {
                    latitudeDelta < 0.005 -> batteryImageLarge  // Very zoomed in
                    latitudeDelta < 0.02 -> batteryImageMedium // Medium zoom
                    else -> batteryImageSmall                  // Zoomed out
                }
            }

            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun gestureRecognizer(
                gestureRecognizer: UIGestureRecognizer,
                shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer
            ): Boolean = true
        }
    }

    val tapHandler = remember(onMapClick) {
        object : NSObject() {
            @ObjCAction
            fun handleTap(sender: UITapGestureRecognizer) {
                val mapView = sender.view as? MKMapView ?: return
                if (sender.state == UIGestureRecognizerStateEnded) {
                    val point = sender.locationInView(mapView)
                    val coordinate = mapView.convertPoint(point, toCoordinateFromView = mapView)
                    coordinate.useContents {
                        onMapClick?.invoke(latitude, longitude)
                    }
                }
            }
        }
    }

    UIKitView(
        factory = {
            MKMapView().apply {
                setUserInteractionEnabled(true)
                setScrollEnabled(true)
                setZoomEnabled(true)
                setRotateEnabled(true)
                setPitchEnabled(true)
                showsUserLocation = true
                setDelegate(combinedDelegate)
            }
        },
        modifier = modifier,
        interactive = true,
        update = { view ->
            // Update annotations
            view.removeAnnotations(view.annotations)
            val annotations = batteries.map { battery ->
                val annotation = MKPointAnnotation()
                annotation.setCoordinate(CLLocationCoordinate2DMake(battery.latitude, battery.longitude))
                annotation.setTitle(battery.name)
                annotation
            }
            view.addAnnotations(annotations)

            // Make gestures snappy
            view.gestureRecognizers?.forEach {
                val recognizer = it as? UIGestureRecognizer ?: return@forEach
                recognizer.delaysTouchesBegan = false
            }

            // Handle Tap Gesture updates - use the delegate as a marker to find our gesture
            val existingTap = view.gestureRecognizers?.filterIsInstance<UITapGestureRecognizer>()
                ?.find { it.delegate == combinedDelegate }

            if (onMapClick != null) {
                if (existingTap == null) {
                    val tapGesture = UITapGestureRecognizer(target = tapHandler, action = sel_registerName("handleTap:"))
                    tapGesture.setDelegate(combinedDelegate)
                    tapGesture.numberOfTapsRequired = 1u
                    tapGesture.cancelsTouchesInView = false 
                    tapGesture.delaysTouchesBegan = false
                    tapGesture.delaysTouchesEnded = false
                    view.addGestureRecognizer(tapGesture)
                }
            } else {
                existingTap?.let { view.removeGestureRecognizer(it) }
            }
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun createResizedImage(name: String, dimension: Double): UIImage? {
    val original = UIImage.imageNamed(name) ?: return null
    val size = CGSizeMake(dimension, dimension)
    UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
    original.drawInRect(CGRectMake(0.0, 0.0, dimension, dimension))
    val resized = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return resized
}

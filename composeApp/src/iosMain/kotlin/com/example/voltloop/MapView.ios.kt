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

    // Load icons - use higher resolution for markers
    val batteryImageSmall = remember { createResizedImage("one_battery", 40.0) }
    val batteryImageMedium = remember { createResizedImage("one_battery", 55.0) }
    val batteryImageLarge = remember { createResizedImage("one_battery", 70.0) }
    val clusterImage = remember { createResizedImage("more_batteries", 80.0) }

    val combinedDelegate = remember(batteryImageSmall, batteryImageMedium, batteryImageLarge, clusterImage) {
        object : NSObject(), MKMapViewDelegateProtocol, UIGestureRecognizerDelegateProtocol {
            var hasZoomedToUser = false

            override fun mapView(mapView: MKMapView, didUpdateUserLocation: MKUserLocation) {
                val location = didUpdateUserLocation.location
                if (!hasZoomedToUser && location != null) {
                    val region = MKCoordinateRegionMakeWithDistance(
                        didUpdateUserLocation.coordinate,
                        1000.0,
                        1000.0
                    )
                    mapView.setRegion(region, animated = true)
                    hasZoomedToUser = true
                }
            }

            override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
                mapView.annotations.forEach { annotation ->
                    if (annotation !is MKUserLocation && annotation is MKAnnotationProtocol) {
                        val view = mapView.viewForAnnotation(annotation)
                        if (view != null) {
                            mapView.region.useContents {
                                updateAnnotationImage(view, annotation, span.latitudeDelta)
                            }
                        }
                    }
                }
            }

            override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
                if (viewForAnnotation is MKUserLocation) return null
                
                if (viewForAnnotation is MKClusterAnnotation) {
                    val identifier = "Cluster"
                    var annotationView = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
                    if (annotationView == null) {
                        annotationView = MKAnnotationView(viewForAnnotation, identifier)
                        annotationView.canShowCallout = false
                    } else {
                        annotationView.annotation = viewForAnnotation
                    }
                    annotationView.image = clusterImage
                    annotationView.displayPriority = MKFeatureDisplayPriorityRequired
                    return annotationView
                }

                val identifier = "Battery"
                var annotationView = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
                
                if (annotationView == null) {
                    annotationView = MKAnnotationView(viewForAnnotation, identifier)
                    annotationView.canShowCallout = true
                    // This is essential for clustering on iOS
                    annotationView.clusteringIdentifier = "batteryCluster"
                    annotationView.displayPriority = MKFeatureDisplayPriorityDefaultHigh
                } else {
                    annotationView.annotation = viewForAnnotation
                }
                
                mapView.region.useContents {
                    updateAnnotationImage(annotationView, viewForAnnotation, span.latitudeDelta)
                }
                return annotationView
            }

            private fun updateAnnotationImage(view: MKAnnotationView?, annotation: Any?, latitudeDelta: Double) {
                if (view == null) return
                if (annotation is MKClusterAnnotation) {
                    view.image = clusterImage
                    return
                }
                
                view.image = when {
                    latitudeDelta < 0.005 -> batteryImageLarge
                    latitudeDelta < 0.02 -> batteryImageMedium
                    else -> batteryImageSmall
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
                
                // Hide default Apple Map markers (POIs)
                showsPointsOfInterest = false
            }
        },
        modifier = modifier,
        interactive = true,
        update = { view ->
            // Efficiently update annotations only when needed to allow clustering to calculate
            val currentAnnotations = view.annotations.filterIsInstance<MKPointAnnotation>()
            if (currentAnnotations.size != batteries.size) {
                view.removeAnnotations(view.annotations)
                val newAnnotations = batteries.map { battery ->
                    val annotation = MKPointAnnotation()
                    annotation.setCoordinate(CLLocationCoordinate2DMake(battery.latitude, battery.longitude))
                    annotation.setTitle(battery.name)
                    annotation
                }
                view.addAnnotations(newAnnotations)
            }

            view.gestureRecognizers?.forEach {
                val recognizer = it as? UIGestureRecognizer ?: return@forEach
                recognizer.delaysTouchesBegan = false
            }

            val existingTap = view.gestureRecognizers?.filterIsInstance<UITapGestureRecognizer>()
                ?.find { it.delegate == combinedDelegate }

            if (onMapClick != null && existingTap == null) {
                val tapGesture = UITapGestureRecognizer(target = tapHandler, action = sel_registerName("handleTap:"))
                tapGesture.setDelegate(combinedDelegate)
                tapGesture.numberOfTapsRequired = 1u
                tapGesture.cancelsTouchesInView = false 
                view.addGestureRecognizer(tapGesture)
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

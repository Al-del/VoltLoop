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
import platform.CoreLocation.CLLocationManagerDelegateProtocol

// Use custom classes to strictly separate markers
class FriendAnnotation(val friendId: String, val username: String) : MKPointAnnotation()
class StationAnnotation(val stationId: String) : MKPointAnnotation()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun MapView(
    modifier: Modifier,
    batteries: List<BatteryLocation>,
    friends: List<UserLocation>,
    onLocationUpdate: ((Double, Double) -> Unit)?,
    onMapClick: ((Double, Double) -> Unit)?
) {
    val locationManager = remember { CLLocationManager() }
    
    val locationDelegate = remember {
        object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val location = didUpdateLocations.lastOrNull() as? platform.CoreLocation.CLLocation ?: return
                onLocationUpdate?.invoke(location.coordinate.useContents { latitude }, location.coordinate.useContents { longitude })
            }
        }
    }

    LaunchedEffect(Unit) {
        locationManager.delegate = locationDelegate
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
    }

    // Load icons - use higher resolution for markers
    val batteryImageSmall = remember { createResizedImage("one_battery", 40.0) }
    val batteryImageMedium = remember { createResizedImage("one_battery", 55.0) }
    val batteryImageLarge = remember { createResizedImage("one_battery", 70.0) }
    val clusterImage = remember { createResizedImage("more_batteries", 80.0) }

    val combinedDelegate = remember(friends, batteryImageSmall, batteryImageMedium, batteryImageLarge, clusterImage) {
        object : NSObject(), MKMapViewDelegateProtocol, UIGestureRecognizerDelegateProtocol {
            var hasZoomedToUser = false

            override fun mapView(mapView: MKMapView, didUpdateUserLocation: MKUserLocation) {
                val location = mapView.userLocation.location
                if (!hasZoomedToUser && location != null) {
                    val region = MKCoordinateRegionMakeWithDistance(location.coordinate, 1000.0, 1000.0)
                    mapView.setRegion(region, animated = true)
                    hasZoomedToUser = true
                }
            }

            override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
                if (viewForAnnotation is MKUserLocation) return null
                
                if (viewForAnnotation is MKClusterAnnotation) {
                    val identifier = "ClusterView"
                    var annotationView = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
                    if (annotationView == null) {
                        annotationView = MKAnnotationView(viewForAnnotation, identifier)
                    } else {
                        annotationView.annotation = viewForAnnotation
                    }
                    annotationView.image = clusterImage ?: createFallbackClusterImage(viewForAnnotation.memberAnnotations.size)
                    return annotationView
                }

                if (viewForAnnotation is FriendAnnotation) {
                    val identifier = "FriendView_${viewForAnnotation.friendId}"
                    var annotationView = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
                    if (annotationView == null) {
                        annotationView = MKAnnotationView(viewForAnnotation, identifier)
                        annotationView.canShowCallout = true
                    } else {
                        annotationView.annotation = viewForAnnotation
                    }
                    annotationView.image = createFriendUIImage(viewForAnnotation.username.take(1).uppercase())
                    annotationView.clusteringIdentifier = null // Friends don't cluster
                    return annotationView
                }

                if (viewForAnnotation is StationAnnotation) {
                    val identifier = "StationView"
                    var annotationView = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
                    if (annotationView == null) {
                        annotationView = MKAnnotationView(viewForAnnotation, identifier)
                        annotationView.canShowCallout = true
                    } else {
                        annotationView.annotation = viewForAnnotation
                    }
                    
                    annotationView.clusteringIdentifier = "battery"
                    mapView.region.useContents {
                        annotationView.image = when {
                            span.latitudeDelta < 0.005 -> batteryImageLarge
                            span.latitudeDelta < 0.02 -> batteryImageMedium
                            else -> batteryImageSmall
                        }
                    }
                    return annotationView
                }

                return null
            }

            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun gestureRecognizer(gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer): Boolean = true
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
                    coordinate.useContents { onMapClick?.invoke(latitude, longitude) }
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
                showsUserLocation = true
                setDelegate(combinedDelegate)
                showsPointsOfInterest = false
                registerClass(MKAnnotationView.`class`(), forAnnotationViewWithReuseIdentifier = "StationView")
                registerClass(MKAnnotationView.`class`(), forAnnotationViewWithReuseIdentifier = "ClusterView")
            }
        },
        modifier = modifier,
        interactive = true,
        update = { view ->
            val currentAnnotations = view.annotations.toList()
            
            // Sync Friends
            friends.forEach { friend ->
                val existing = currentAnnotations.filterIsInstance<FriendAnnotation>().find { it.friendId == friend.id }
                if (existing != null) {
                    existing.setCoordinate(CLLocationCoordinate2DMake(friend.latitude, friend.longitude))
                } else {
                    val newAnn = FriendAnnotation(friend.id, friend.username ?: "Friend")
                    newAnn.setCoordinate(CLLocationCoordinate2DMake(friend.latitude, friend.longitude))
                    newAnn.setTitle(friend.username ?: "Friend")
                    view.addAnnotation(newAnn)
                }
            }
            currentAnnotations.filterIsInstance<FriendAnnotation>().forEach { ann ->
                if (friends.none { it.id == ann.friendId }) view.removeAnnotation(ann)
            }

            // Sync Stations - Improved ID handling to prevent disappearing
            batteries.forEach { battery ->
                // Use a fallback stable identifier if ID is null (e.g. for new placements)
                val batteryId = battery.id ?: "${battery.latitude},${battery.longitude}"
                val existing = currentAnnotations.filterIsInstance<StationAnnotation>().find { it.stationId == batteryId }
                
                if (existing != null) {
                    existing.setCoordinate(CLLocationCoordinate2DMake(battery.latitude, battery.longitude))
                } else {
                    val newAnn = StationAnnotation(batteryId)
                    newAnn.setCoordinate(CLLocationCoordinate2DMake(battery.latitude, battery.longitude))
                    newAnn.setTitle(battery.name)
                    view.addAnnotation(newAnn)
                }
            }
            
            // Only remove if it strictly doesn't exist in the data anymore
            currentAnnotations.filterIsInstance<StationAnnotation>().forEach { ann ->
                val exists = batteries.any { (it.id ?: "${it.latitude},${it.longitude}") == ann.stationId }
                if (!exists) view.removeAnnotation(ann)
            }

            // Setup Tap Gesture
            val existingTap = view.gestureRecognizers?.filterIsInstance<UITapGestureRecognizer>()?.find { it.delegate == combinedDelegate }
            if (onMapClick != null && existingTap == null) {
                val tapGesture = UITapGestureRecognizer(target = tapHandler, action = sel_registerName("handleTap:"))
                tapGesture.setDelegate(combinedDelegate)
                view.addGestureRecognizer(tapGesture)
            }
        }
    )
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun createFallbackClusterImage(count: Int): UIImage? {
    val size = 80.0
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(size, size), false, 0.0)
    val context = UIGraphicsGetCurrentContext()
    CGContextSetFillColorWithColor(context, UIColor.colorWithRed(0.08, 0.32, 0.17, 1.0).CGColor)
    CGContextFillEllipseInRect(context, CGRectMake(0.0, 0.0, size, size))
    val attributes = mapOf(NSFontAttributeName to UIFont.boldSystemFontOfSize(22.0), NSForegroundColorAttributeName to UIColor.whiteColor)
    val text = count.toString()
    val attributedString = NSAttributedString.create(text, attributes as Map<Any?, *>)
    val textSize = attributedString.size()
    attributedString.drawAtPoint(CGPointMake((size - textSize.useContents { width }) / 2.0, (size - textSize.useContents { height }) / 2.0))
    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return image
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun createFriendUIImage(initial: String): UIImage? {
    val size = 48.0
    val rect = CGRectMake(0.0, 0.0, size, size)
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(size, size), false, 0.0)
    val context = UIGraphicsGetCurrentContext()
    CGContextSetFillColorWithColor(context, UIColor.colorWithRed(0.29, 0.87, 0.50, 1.0).CGColor)
    CGContextFillEllipseInRect(context, rect)
    CGContextSetStrokeColorWithColor(context, UIColor.whiteColor.CGColor)
    CGContextSetLineWidth(context, 2.0)
    CGContextStrokeEllipseInRect(context, CGRectMake(1.0, 1.0, size - 2.0, size - 2.0))
    val attributes = mapOf(NSFontAttributeName to UIFont.boldSystemFontOfSize(20.0), NSForegroundColorAttributeName to UIColor.whiteColor)
    val attributedString = NSAttributedString.create(initial, attributes as Map<Any?, *>)
    val textSize = attributedString.size()
    attributedString.drawAtPoint(CGPointMake((size - textSize.useContents { width }) / 2.0, (size - textSize.useContents { height }) / 2.0))
    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return image
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

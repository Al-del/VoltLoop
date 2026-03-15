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

class PanState(var location: Pair<Double, Double>? = null)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun MapView(
    modifier: Modifier,
    batteries: List<BatteryLocation>,
    friends: List<UserLocation>,
    onLocationUpdate: ((Double, Double) -> Unit)?,
    onMapClick: ((Double, Double) -> Unit)?,
    onFriendChatClick: ((String, String) -> Unit)?,
    panToLocation: Pair<Double, Double>?
) {
    val panState = remember { PanState() }
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

    val batteryImageSmall = remember { createResizedImage("one_battery", 40.0) }
    val batteryImageMedium = remember { createResizedImage("one_battery", 55.0) }
    val batteryImageLarge = remember { createResizedImage("one_battery", 70.0) }
    val clusterImage = remember { createResizedImage("more_batteries", 80.0) }

    val currentOnFriendChatClick by rememberUpdatedState(onFriendChatClick)

    val combinedDelegate = remember(batteryImageSmall, batteryImageMedium, batteryImageLarge, clusterImage) {
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
                    // Always re-apply image — dequeued views lose it on recycle
                    val memberCount = (viewForAnnotation as MKClusterAnnotation).memberAnnotations.size
                    annotationView!!.image = clusterImage ?: createFallbackClusterImage(memberCount)
                    return annotationView
                }

                if (viewForAnnotation is FriendAnnotation) {
                    val identifier = "FriendView"
                    var annotationView = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
                    if (annotationView == null) {
                        annotationView = MKAnnotationView(viewForAnnotation, identifier)
                        annotationView.canShowCallout = true
                        val btn = UIButton.buttonWithType(UIButtonTypeDetailDisclosure)
                        btn.setImage(UIImage.systemImageNamed("message.fill"), forState = UIControlStateNormal)
                        annotationView.rightCalloutAccessoryView = btn
                    } else {
                        annotationView.annotation = viewForAnnotation
                    }
                    // Always re-apply — dequeued views must be refreshed
                    annotationView!!.image = createFriendUIImage(viewForAnnotation.username.take(1).uppercase())
                    annotationView.clusteringIdentifier = null
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
                    // Always set clusteringIdentifier AND image on every call
                    // (dequeued views lose their image — this is the cause of red default pins)
                    annotationView!!.clusteringIdentifier = "battery"
                    val img = mapView.region.useContents {
                        when {
                            span.latitudeDelta < 0.005 -> batteryImageLarge
                            span.latitudeDelta < 0.02  -> batteryImageMedium
                            else                        -> batteryImageSmall
                        }
                    }
                    annotationView.image = img ?: batteryImageSmall
                    return annotationView
                }

                return null
            }

            override fun mapView(mapView: MKMapView, annotationView: MKAnnotationView, calloutAccessoryControlTapped: UIControl) {
                val annotation = annotationView.annotation
                if (annotation is FriendAnnotation) {
                    currentOnFriendChatClick?.invoke(annotation.friendId, annotation.username)
                }
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

    // Keep strong Kotlin references to custom annotations to prevent K/N GC from stripping their Kotlin identity
    val stationAnnotationsMap = remember { mutableMapOf<String, StationAnnotation>() }
    val friendAnnotationsMap = remember { mutableMapOf<String, FriendAnnotation>() }

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
            view.delegate = combinedDelegate
            
            if (panToLocation != null && panToLocation != panState.location) {
                val loc = CLLocationCoordinate2DMake(panToLocation.first, panToLocation.second)
                val region = MKCoordinateRegionMakeWithDistance(loc, 1500.0, 1500.0)
                view.setRegion(region, animated = true)
                panState.location = panToLocation
            }
            
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
                    friendAnnotationsMap[friend.id] = newAnn // Retain in Kotlin
                    view.addAnnotation(newAnn)
                }
            }
            currentAnnotations.filterIsInstance<FriendAnnotation>().forEach { ann ->
                if (friends.none { it.id == ann.friendId }) {
                    view.removeAnnotation(ann)
                    friendAnnotationsMap.remove(ann.friendId)
                }
            }

            // Sync Stations
            batteries.forEach { battery ->
                val batteryId = battery.id ?: "${battery.latitude},${battery.longitude}"
                val existing = currentAnnotations.filterIsInstance<StationAnnotation>().find { it.stationId == batteryId }
                
                if (existing != null) {
                    existing.setCoordinate(CLLocationCoordinate2DMake(battery.latitude, battery.longitude))
                } else {
                    val newAnn = StationAnnotation(batteryId)
                    newAnn.setCoordinate(CLLocationCoordinate2DMake(battery.latitude, battery.longitude))
                    newAnn.setTitle(battery.name)
                    stationAnnotationsMap[batteryId] = newAnn // Retain in Kotlin
                    view.addAnnotation(newAnn)
                }
            }
            
            currentAnnotations.filterIsInstance<StationAnnotation>().forEach { ann ->
                val exists = batteries.any { (it.id ?: "${it.latitude},${it.longitude}") == ann.stationId }
                if (!exists) {
                    view.removeAnnotation(ann)
                    stationAnnotationsMap.remove(ann.stationId)
                }
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
    CGContextSetFillColorWithColor(context, UIColor.colorWithRed(0.26, 0.73, 0.97, 1.0).CGColor)
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

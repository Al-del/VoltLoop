package com.example.voltloop

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.clustering.ClusterItem
import org.jetbrains.compose.resources.ExperimentalResourceApi
import voltloop.composeapp.generated.resources.Res

// Helper class for clustering
data class BatteryClusterItem(
    val battery: BatteryLocation
) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(battery.latitude, battery.longitude)
    override fun getTitle(): String = battery.name
    override fun getSnippet(): String = ""
    override fun getZIndex(): Float? = null
}

@OptIn(ExperimentalResourceApi::class)
@SuppressLint("MissingPermission")
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
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions.values.any { it }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(1.35, 103.87), 10f)
    }

    // Start periodic location updates for the user
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && onLocationUpdate != null) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(3000)
                .build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let {
                        onLocationUpdate(it.latitude, it.longitude)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, context.mainLooper)
        }
    }

    // Marker icons state
    var oneBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var moreBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        try {
            MapsInitializer.initialize(context)
            val oneBytes = Res.readBytes("drawable/one_battery.png")
            oneBitmap = BitmapFactory.decodeByteArray(oneBytes, 0, oneBytes.size)
            val moreBytes = Res.readBytes("drawable/more_batteries.png")
            moreBitmap = BitmapFactory.decodeByteArray(moreBytes, 0, moreBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val clusterItems = remember(batteries) {
        batteries.map { BatteryClusterItem(it) }
    }

    // Map style to hide POIs but keep user location visible
    val mapStyle = remember {
        MapStyleOptions("""
            [
              { "featureType": "poi", "stylers": [{ "visibility": "off" }] },
              { "featureType": "transit", "stylers": [{ "visibility": "off" }] }
            ]
        """.trimIndent())
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                }
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(panToLocation) {
        panToLocation?.let {
            val userLatLng = LatLng(it.first, it.second)
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapStyleOptions = mapStyle
        ),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = hasLocationPermission,
            zoomControlsEnabled = false,
            compassEnabled = false
        ),
        onMapClick = { latLng -> onMapClick?.invoke(latLng.latitude, latLng.longitude) }
    ) {
        // Render Friends
        friends.forEach { friend ->
            val friendIcon = remember(friend.username) {
                createFriendIcon(friend.username ?: "F", context)
            }
            MarkerInfoWindow(
                state = MarkerState(position = LatLng(friend.latitude, friend.longitude)),
                icon = friendIcon,
                onInfoWindowClick = {
                    onFriendChatClick?.invoke(friend.id, friend.username ?: "Friend")
                }
            ) {
                // Custom Info Window
                Box(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = friend.username ?: "Friend",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Chat",
                            tint = Color(0xFF15803D), // GreenBright
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (oneBitmap != null && moreBitmap != null) {
            Clustering(
                items = clusterItems,
                onClusterClick = { cluster ->
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(cluster.position, cameraPositionState.position.zoom + 2f))
                    true
                },
                clusterContent = {
                    Image(
                        bitmap = moreBitmap!!.asImageBitmap(),
                        contentDescription = "Battery Group",
                        modifier = Modifier.size(80.dp)
                    )
                },
                clusterItemContent = { item ->
                    val zoom = cameraPositionState.position.zoom
                    val size = when {
                        zoom >= 15f -> 70.dp
                        zoom >= 12f -> 55.dp
                        else -> 40.dp
                    }
                    Image(
                        bitmap = oneBitmap!!.asImageBitmap(),
                        contentDescription = item.title,
                        modifier = Modifier.size(size)
                    )
                }
            )
        }
    }
}

private fun createFriendIcon(username: String, context: Context): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val size = (48 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val paint = Paint()
    paint.isAntiAlias = true
    
    // Background Circle
    paint.color = 0xFF4ADE80.toInt() // GreenAccent
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    
    // Border
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2 * density
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (1 * density), paint)
    
    // Initial text
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 20 * density
    paint.color = android.graphics.Color.WHITE
    val initial = username.take(1).uppercase()
    val textHeight = paint.descent() - paint.ascent()
    val textOffset = textHeight / 2 - paint.descent()
    canvas.drawText(initial, size / 2f, size / 2f + textOffset, paint)
    
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

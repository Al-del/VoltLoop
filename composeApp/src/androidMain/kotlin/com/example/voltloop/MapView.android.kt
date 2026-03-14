package com.example.voltloop

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import voltloop.composeapp.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
@SuppressLint("MissingPermission")
@Composable
actual fun MapView(
    modifier: Modifier,
    batteries: List<BatteryLocation>,
    onMapClick: ((Double, Double) -> Unit)?
) {
    val context = LocalContext.current
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
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

    // Custom marker icon state
    var batterySmallIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var batteryMediumIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var batteryLargeIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }

    // Load markers from the PNG file
    LaunchedEffect(Unit) {
        try {
            // Load PNG bytes from Multiplatform Resources
            val pngBytes = Res.readBytes("drawable/one_battery.png")
            val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
            
            if (bitmap != null) {
                MapsInitializer.initialize(context)
                val density = context.resources.displayMetrics.density
                
                // Create high-quality scaled markers matching the iOS sizes (40, 55, 70)
                batterySmallIcon = BitmapDescriptorFactory.fromBitmap(
                    Bitmap.createScaledBitmap(bitmap, (40 * density).toInt(), (40 * density).toInt(), true)
                )
                batteryMediumIcon = BitmapDescriptorFactory.fromBitmap(
                    Bitmap.createScaledBitmap(bitmap, (55 * density).toInt(), (55 * density).toInt(), true)
                )
                batteryLargeIcon = BitmapDescriptorFactory.fromBitmap(
                    Bitmap.createScaledBitmap(bitmap, (70 * density).toInt(), (70 * density).toInt(), true)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val currentIcon = remember(cameraPositionState.position.zoom, batterySmallIcon, batteryMediumIcon, batteryLargeIcon) {
        val zoom = cameraPositionState.position.zoom
        when {
            zoom >= 15f -> batteryLargeIcon
            zoom >= 12f -> batteryMediumIcon
            else -> batterySmallIcon
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                }
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
        onMapClick = { latLng ->
            onMapClick?.invoke(latLng.latitude, latLng.longitude)
        }
    ) {
        // Only render markers when the custom PNG icons are loaded to avoid showing default markers
        if (currentIcon != null) {
            batteries.forEach { battery ->
                Marker(
                    state = MarkerState(position = LatLng(battery.latitude, battery.longitude)),
                    title = battery.name,
                    icon = currentIcon
                )
            }
        }
    }
}

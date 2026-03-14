package com.example.voltloop

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
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
    onMapClick: ((Double, Double) -> Unit)?
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

    var oneBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var moreBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

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

    // Hide default Google map labels/markers (POI and Transit)
    val mapStyle = remember {
        MapStyleOptions("""
            [
              {
                "featureType": "poi",
                "stylers": [{ "visibility": "off" }]
              },
              {
                "featureType": "transit",
                "stylers": [{ "visibility": "off" }]
              }
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

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapStyleOptions = mapStyle
        ),
        uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
        onMapClick = { latLng -> onMapClick?.invoke(latLng.latitude, latLng.longitude) }
    ) {
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

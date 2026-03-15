package com.example.voltloop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MapView(
    modifier: Modifier = Modifier,
    batteries: List<BatteryLocation> = emptyList(),
    friends: List<UserLocation> = emptyList(),
    onLocationUpdate: ((Double, Double) -> Unit)? = null,
    onMapClick: ((Double, Double) -> Unit)? = null,
    onFriendChatClick: ((String, String) -> Unit)? = null,
    panToLocation: Pair<Double, Double>? = null
)

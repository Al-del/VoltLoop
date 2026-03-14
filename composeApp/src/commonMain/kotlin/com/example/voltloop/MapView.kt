package com.example.voltloop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MapView(
    modifier: Modifier = Modifier,
    batteries: List<BatteryLocation> = emptyList(),
    onMapClick: ((Double, Double) -> Unit)? = null
)

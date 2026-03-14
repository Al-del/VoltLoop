package com.example.voltloop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

private val GreenDark       = Color(0xFF14532D)
private val GreenMid        = Color(0xFF166534)
private val GreenBright     = Color(0xFF15803D)
private val GreenAccent     = Color(0xFF4ADE80)
private val GreenLight      = Color(0xFF86EFAC)
private val GreenStartBtn   = Color(0xFF22C55E)
private val GreenStartBtnDark = Color(0xFF16A34A)
private val LabelInactive   = Color(0xFFBBF7D0).copy(alpha = 0.60f)
private val IconInactive    = Color(0xFFBBF7D0).copy(alpha = 0.65f)

sealed class Screen(val route: String) {
    object Settings : Screen("settings")
    object Map : Screen("map")
    object StartTrip : Screen("start_trip")
}

enum class NavItem(val label: String, val icon: ImageVector, val screen: Screen) {
    Settings("Settings", Icons.Filled.Settings, Screen.Settings),
    Map("Map", Icons.Filled.Map, Screen.Map),
    StartTrip("Start Trip", Icons.Filled.PlayArrow, Screen.StartTrip),
}

@Composable
fun GreenNavBar(
    modifier: Modifier = Modifier,
    selected: NavItem = NavItem.StartTrip,
    onItemSelected: (NavItem) -> Unit = {},
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = GreenBright.copy(alpha = 0.4f),
                spotColor = GreenBright.copy(alpha = 0.4f),
            )
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(GreenDark, GreenMid, GreenBright)
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NavItem.entries.forEach { item ->
                NavButton(
                    item = item,
                    isSelected = item == selected,
                    onClick = { onItemSelected(item) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavButton(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isStartTrip = item == NavItem.StartTrip

    val scale by animateFloatAsState(
        targetValue = if (isSelected && isStartTrip) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isStartTrip        -> Color.White
            isSelected         -> GreenLight
            else               -> IconInactive
        },
        animationSpec = tween(200),
        label = "iconColor",
    )

    val labelColor by animateColorAsState(
        targetValue = when {
            isStartTrip        -> Color.White.copy(alpha = 0.9f)
            isSelected         -> GreenLight
            else               -> LabelInactive
        },
        animationSpec = tween(200),
        label = "labelColor",
    )

    val bgBrush: Brush = if (isStartTrip) {
        Brush.linearGradient(listOf(GreenStartBtnDark, GreenStartBtn))
    } else if (isSelected) {
        Brush.linearGradient(
            listOf(
                GreenAccent.copy(alpha = 0.18f),
                GreenAccent.copy(alpha = 0.18f),
            )
        )
    } else {
        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }

    val interactionSource = remember { MutableInteractionSource() }
    val rippleConfig = RippleConfiguration(color = GreenLight)

    CompositionLocalProvider(LocalRippleConfiguration provides rippleConfig) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(bgBrush)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    onClick = onClick,
                )
                .then(
                    if (isStartTrip) Modifier.shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = GreenStartBtn.copy(alpha = 0.5f),
                        spotColor = GreenStartBtn.copy(alpha = 0.5f),
                    ) else Modifier
                )
                .padding(horizontal = if (isStartTrip) 20.dp else 16.dp, vertical = if (isStartTrip) 12.dp else 10.dp)
                .widthIn(min = 72.dp),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = item.label.uppercase(),
                color = labelColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.05.sp,
            )

            // Active indicator dot (non-Start Trip only)
            if (isSelected && !isStartTrip) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(GreenAccent),
                )
            }
        }
    }
}


@Composable
fun Nav_Bar_ussage() {
    val navController = rememberNavController()
    var selected by remember { mutableStateOf(NavItem.StartTrip) }
    val scope = rememberCoroutineScope()
    
    // Admin & Battery state
    var isAdmin by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf<String?>(null) }
    var isPlacementMode by remember { mutableStateOf(false) }
    var batteries by remember { mutableStateOf(listOf<BatteryLocation>()) }

    // Check admin status
    LaunchedEffect(Unit) {
        val user = supabase.auth.currentUserOrNull()
        isAdmin = user?.email == "admin@voltloop.com"
        userId = user?.id
    }

    // Real-time battery updates
    LaunchedEffect(Unit) {
        // Initial fetch
        try {
            val initialBatteries = supabase.postgrest["battery_locations"].select().decodeList<BatteryLocation>()
            batteries = initialBatteries
        } catch (e: Exception) {
            println("Error fetching batteries: ${e.message}")
        }

        // Real-time subscription
        val channel = supabase.channel("batteries_channel")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "battery_locations"
        }
        val json = Json { ignoreUnknownKeys = true }
        flow.onEach { action ->
            when (action) {
                is PostgresAction.Insert -> {
                    val newBattery = json.decodeFromJsonElement<BatteryLocation>(action.record)
                    batteries = batteries + newBattery
                }
                is PostgresAction.Update -> {
                    val updatedBattery = json.decodeFromJsonElement<BatteryLocation>(action.record)
                    batteries = batteries.map { if (it.id == updatedBattery.id) updatedBattery else it }
                }
                is PostgresAction.Delete -> {
                    val deletedId = action.oldRecord["id"]?.toString()
                    batteries = batteries.filter { it.id != deletedId }
                }
                else -> {}
            }
        }.launchIn(this)
        channel.subscribe()
    }

    // Use movableContentOf to preserve state
    val mapLayer = remember(batteries, isPlacementMode, userId) {
        movableContentOf {
            MapView(
                modifier = Modifier.fillMaxSize(),
                batteries = batteries,
                onMapClick = if (isPlacementMode) { lat, lon ->
                    scope.launch {
                        try {
                            val newBattery = BatteryLocation(
                                latitude = lat, 
                                longitude = lon,
                                name = "battery_${batteries.size + 1}",
                                userId = userId
                            )
                            // Removed .decodeSingle() to avoid crash on empty response
                            supabase.postgrest["battery_locations"].insert(newBattery)
                        } catch (e: Exception) {
                            println("Error adding battery: ${e.message}")
                        }
                    }
                } else null
            )
        }
    }

    val navHostLayer = remember {
        movableContentOf {
            NavHost(
                navController = navController,
                startDestination = Screen.StartTrip.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Settings.route) { SettingsScreen() }
                composable(Screen.StartTrip.route) { Start_Trip() }
                composable(Screen.Map.route) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selected == NavItem.Map) {
            mapLayer()
        } else {
            mapLayer()
            navHostLayer()
        }

        // Admin Placement Toggle Button
        if (isAdmin) {
            LargeFloatingActionButton(
                onClick = { isPlacementMode = !isPlacementMode },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .padding(top = 40.dp), // Extra padding for status bar
                containerColor = if (isPlacementMode) Color.Red else GreenBright,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.AddLocation,
                    contentDescription = "Toggle Placement Mode"
                )
            }
            
            if (isPlacementMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Placement Mode: Tap map to add battery",
                        color = Color.White,
                        modifier = Modifier.padding(8.dp),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Floating Navbar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .wrapContentSize()
        ) {
            GreenNavBar(
                selected = selected,
                onItemSelected = { item ->
                    selected = item
                    navController.navigate(item.screen.route) {
                        popUpTo(Screen.StartTrip.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

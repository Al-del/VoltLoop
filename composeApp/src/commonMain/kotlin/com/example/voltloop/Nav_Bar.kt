package com.example.voltloop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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

private val GreenDark = Color(0xFF14532D)
private val GreenBright = Color(0xFF15803D)
private val NavBarBg = Color(0xFFFFFFFF)
private val NavIconInactive = Color(0xFF1A1A1A)
private val NavIconSelected = Color(0xFF43BBF7)
private val LightningBlue = Color(0xFF43BBF7)

sealed class Screen(val route: String) {
    object Settings : Screen("settings")
    object Notifications : Screen("notifications")
    object Map : Screen("map")
    object StartTrip : Screen("start_trip")
    object Account : Screen("account")
    object Friends : Screen("friends")
    object Store : Screen("store")
    object QrScanner : Screen("qr_scanner")
    object Chat : Screen("chat/{friendId}/{friendName}") {
        fun createRoute(friendId: String, friendName: String) = "chat/$friendId/$friendName"
    }
}

enum class NavItem(val label: String, val icon: ImageVector, val screen: Screen) {
    History("History", Icons.Filled.History, Screen.Settings),
    Account("Account", Icons.Filled.Person, Screen.Account),
    Map("Map", Icons.Filled.Language, Screen.Map),
    Friends("Friends", Icons.Filled.PersonAdd, Screen.Friends),
}

@Composable
fun WhiteNavBar(
    modifier: Modifier = Modifier,
    selected: NavItem = NavItem.Map,
    onItemSelected: (NavItem) -> Unit = {},
) {
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(32.dp),
                ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .background(NavBarBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).padding(horizontal = 8.dp),
        ) {
            listOf(NavItem.Account, NavItem.History).forEach { item ->
                NavIconButton(item = item, isSelected = item == selected, onClick = { onItemSelected(item) })
            }
            Spacer(modifier = Modifier.width(72.dp))
            listOf(NavItem.Map, NavItem.Friends).forEach { item ->
                NavIconButton(item = item, isSelected = item == selected, onClick = { onItemSelected(item) })
            }
        }
    }
}

fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371.0
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLon = (lon2 - lon1) * PI / 180.0
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}

@Composable
fun MapSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val cornerRadius by animateDpAsState(targetValue = if (isFocused) 16.dp else 27.dp, animationSpec = tween(300))
    val elevation by animateDpAsState(targetValue = if (isFocused) 4.dp else 16.dp, animationSpec = tween(300))

    Box(
        modifier = modifier.fillMaxWidth().height(54.dp)
            .shadow(elevation = elevation, shape = RoundedCornerShape(cornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.15f), spotColor = Color.Black.copy(alpha = 0.15f))
            .background(color = Color.White.copy(alpha = 0.9f), shape = RoundedCornerShape(cornerRadius))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = query.isEmpty(),
                    enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)),
                    exit = androidx.compose.animation.fadeOut(animationSpec = tween(200))
                ) {
                    Text("Search", color = Color.Gray.copy(alpha = 0.5f), fontSize = 20.sp, fontWeight = FontWeight.Normal)
                }
                BasicTextField(
                    value = query, onValueChange = onQueryChange,
                    textStyle = TextStyle(fontSize = 20.sp, color = Color.Black, textAlign = TextAlign.Center, fontWeight = FontWeight.Normal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                        isFocused = state.isFocused
                        onFocusChanged(state.isFocused)
                    }
                )
            }
            Icon(imageVector = Icons.Default.Search, contentDescription = "Search",
                tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(26.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavIconButton(item: NavItem, isSelected: Boolean, onClick: () -> Unit) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) NavIconSelected else NavIconInactive,
        animationSpec = tween(200), label = "iconColor"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val rippleConfig = RippleConfiguration(color = NavIconSelected.copy(alpha = 0.3f))
    CompositionLocalProvider(LocalRippleConfiguration provides rippleConfig) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(interactionSource = interactionSource, indication = ripple(bounded = true), onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Icon(imageVector = item.icon, contentDescription = item.label, tint = iconColor, modifier = Modifier.size(26.dp))
            if (isSelected) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(NavIconSelected))
            } else {
                Spacer(modifier = Modifier.size(5.dp))
            }
        }
    }
}

@Composable
fun Nav_Bar_ussage() {
    val navController = rememberNavController()
    var selected by remember { mutableStateOf(NavItem.Map) }
    var previousSelected by remember { mutableStateOf(NavItem.Map) }
    val scope = rememberCoroutineScope()

    var isAdmin by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf<String?>(null) }
    var isPlacementMode by remember { mutableStateOf(false) }
    var mapSearchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var currentPanLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var batteries by remember { mutableStateOf(listOf<BatteryLocation>()) }
    var friendsLocations by remember { mutableStateOf(listOf<UserLocation>()) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isChatScreen = currentRoute?.startsWith("chat/") == true

    val selectedRef = remember { mutableStateOf(selected) }
    LaunchedEffect(selected) { selectedRef.value = selected }

    LaunchedEffect(currentRoute) {
        when {
            currentRoute == Screen.Settings.route            -> { previousSelected = selected; selected = NavItem.History }
            currentRoute == Screen.Friends.route             -> { previousSelected = selected; selected = NavItem.Friends }
            currentRoute == Screen.Map.route                 -> { previousSelected = selected; selected = NavItem.Map }
            currentRoute == Screen.Account.route             -> { previousSelected = selected; selected = NavItem.Account }
            currentRoute == Screen.StartTrip.route           -> { previousSelected = selected }
            currentRoute?.startsWith("chat/") == true        -> { previousSelected = selected }
            currentRoute == null                             -> selected = previousSelected
        }
    }

    LaunchedEffect(Unit) {
        val user = supabase.auth.currentUserOrNull()
        isAdmin = user?.email == "admin@voltloop.com"
        userId = user?.id
    }

    LaunchedEffect(Unit) {
        val json = Json { ignoreUnknownKeys = true }
        try {
            batteries = supabase.postgrest["battery_locations"].select().decodeList<BatteryLocation>()
            val currentUserId = supabase.auth.currentUserOrNull()?.id ?: ""
            val friendships = supabase.postgrest["friendships"]
                .select {
                    filter {
                        and {
                            or { eq("user_id", currentUserId); eq("friend_id", currentUserId) }
                            eq("status", "accepted")
                        }
                    }
                }.decodeList<Friendship>()
            val friendIds = friendships.map { if (it.userId == currentUserId) it.friendId else it.userId }
            if (friendIds.isNotEmpty()) {
                val locations = supabase.postgrest["user_locations"]
                    .select { filter { or { friendIds.forEach { id -> eq("id", id) } } } }
                    .decodeList<UserLocation>()
                val profiles = supabase.postgrest["profiles"]
                    .select { filter { or { friendIds.forEach { id -> eq("id", id) } } } }
                    .decodeList<Profile>()
                friendsLocations = locations.map { loc ->
                    loc.copy(username = profiles.find { it.id == loc.id }?.username ?: "Friend")
                }
            }
        } catch (e: Exception) { println("Error fetching initial data: ${e.message}") }

        val locChannel = supabase.channel("locations_channel")
        locChannel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "user_locations" }
            .onEach { action ->
                if (action is PostgresAction.Insert || action is PostgresAction.Update) {
                    val updatedLoc = Json { ignoreUnknownKeys = true }.decodeFromJsonElement<UserLocation>(action.record)
                    if (friendsLocations.none { it.id == updatedLoc.id }) return@onEach
                    val existingUsername = friendsLocations.find { it.id == updatedLoc.id }?.username
                    val locWithUsername = updatedLoc.copy(username = existingUsername ?: "Friend")
                    friendsLocations = if (friendsLocations.any { it.id == updatedLoc.id }) {
                        friendsLocations.map { if (it.id == updatedLoc.id) locWithUsername else it }
                    } else { friendsLocations + locWithUsername }
                }
            }.launchIn(this)
        locChannel.subscribe()

        val batChannel = supabase.channel("batteries_channel")
        batChannel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "battery_locations" }
            .onEach { action ->
                val j = Json { ignoreUnknownKeys = true }
                when (action) {

                    is PostgresAction.Insert -> batteries = batteries + j.decodeFromJsonElement<BatteryLocation>(action.record)
                    is PostgresAction.Update -> {
                        val updated = j.decodeFromJsonElement<BatteryLocation>(action.record)
                        batteries = batteries.map { if (it.id == updated.id) updated else it }
                    }
                    is PostgresAction.Delete -> {
                        val deletedId = action.oldRecord["id"]?.toString()
                        batteries = batteries.filter { it.id != deletedId }
                    }
                    else -> {}
                }
            }.launchIn(this)
        batChannel.subscribe()
    }

    val platformFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val mapLayer = remember(isPlacementMode, userId) {
        movableContentOf { currentBatteries: List<BatteryLocation>, currentFriends: List<UserLocation>, panTo: Pair<Double, Double>? ->
            MapView(
                modifier = Modifier.fillMaxSize(),
                batteries = currentBatteries,
                friends = currentFriends,
                panToLocation = panTo,
                onLocationUpdate = { lat, lon ->
                    userLocation = Pair(lat, lon)
                    scope.launch {
                        try {
                            userId?.let { supabase.postgrest["user_locations"].upsert(UserLocation(id = it, latitude = lat, longitude = lon)) }
                        } catch (e: Exception) { println("Error updating user location: ${e.message}") }
                    }
                },
                onMapClick = if (isPlacementMode) { lat, lon ->
                    scope.launch {
                        try {
                            supabase.postgrest["battery_locations"].insert(
                                BatteryLocation(latitude = lat, longitude = lon, name = "battery_${batteries.size + 1}", userId = userId)
                            )
                        } catch (e: Exception) { println("Error adding battery: ${e.message}") }
                    }
                } else { _, _ -> platformFocusManager.clearFocus() },
                onFriendChatClick = { friendId, friendName ->
                    if (selectedRef.value == NavItem.Map) {
                        navController.navigate(Screen.Map.route) { launchSingleTop = true }
                    }
                    navController.navigate(Screen.Chat.createRoute(friendId, friendName))
                }
            )
        }
    }

    val navHostLayer = remember {
        movableContentOf {
            NavHost(navController = navController, startDestination = Screen.Map.route, modifier = Modifier.fillMaxSize()) {
                composable(Screen.Settings.route)  { HistoryScreen() }
                composable(Screen.Account.route)   { 
                    AccountScreen(
                        onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                        onNavigateToStore = { navController.navigate(Screen.Store.route) }
                    ) 
                }
                composable(Screen.Notifications.route) { 
                    NotificationsScreen(onBackClick = { navController.popBackStack() })
                }
                composable(Screen.StartTrip.route) { Start_Trip() }
                composable(Screen.Friends.route)   { FriendsScreen(navController) }
                composable(Screen.Store.route)     { StoreScreen(onBackClick = { navController.popBackStack() }) }
                composable(Screen.Chat.route) { backStackEntry ->
                    val friendId   = backStackEntry.arguments?.getString("friendId")   ?: ""
                    val friendName = backStackEntry.arguments?.getString("friendName") ?: "Chat"
                    ChatScreen(friendId, friendName, navController)
                }
                composable(Screen.Map.route) { Box(Modifier.fillMaxSize()) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        if (selected == NavItem.Map) {
            mapLayer(batteries, friendsLocations, currentPanLocation)
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp).padding(horizontal = 24.dp)) {
                MapSearchBar(query = mapSearchQuery, onQueryChange = { mapSearchQuery = it }, onFocusChanged = { isSearchFocused = it })
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSearchFocused,
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it / 3 }, animationSpec = tween(300)) +
                            androidx.compose.animation.fadeIn(animationSpec = tween(300)),
                    exit  = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it / 3 }, animationSpec = tween(200)) +
                            androidx.compose.animation.fadeOut(animationSpec = tween(200))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        val allItems = buildList {
                            batteries.forEach { add(it to null as UserLocation?) }
                            friendsLocations.forEach { add(null as BatteryLocation? to it) }
                        }
                        val filtered = if (mapSearchQuery.isBlank()) {
                            batteries.map { it to null as UserLocation? }.sortedBy {
                                userLocation?.let { u -> haversineDistance(it.first.latitude, it.first.longitude, u.first, u.second) } ?: Double.MAX_VALUE
                            }.take(3)
                        } else {
                            val queryLower = mapSearchQuery.lowercase()
                            allItems.filter { item ->
                                val b = item.first; val f = item.second
                                (b != null && b.name.lowercase().contains(queryLower)) ||
                                        (f != null && f.username?.lowercase()?.contains(queryLower) == true)
                            }.sortedBy {
                                val lat = it.first?.latitude ?: it.second?.latitude ?: 0.0
                                val lon = it.first?.longitude ?: it.second?.longitude ?: 0.0
                                userLocation?.let { u -> haversineDistance(lat, lon, u.first, u.second) } ?: Double.MAX_VALUE
                            }
                        }
                        if (filtered.isNotEmpty()) {
                            Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.95f),
                                shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                    if (mapSearchQuery.isBlank()) {
                                        item { Text("Closest Batteries", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray,
                                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)) }
                                    }
                                    items(filtered.size) { index ->
                                        val item = filtered[index]; val b = item.first; val f = item.second
                                        val name = b?.name ?: f?.username ?: "Unknown"
                                        val lat = b?.latitude ?: f?.latitude ?: 0.0
                                        val lon = b?.longitude ?: f?.longitude ?: 0.0
                                        val distKm = userLocation?.let { haversineDistance(lat, lon, it.first, it.second) }
                                        val distText = if (distKm != null) {
                                            if (distKm < 1.0) "${(distKm * 1000).toInt()} m" else "${(distKm * 10.0).roundToInt() / 10.0} km"
                                        } else ""
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                                .clickable { currentPanLocation = Pair(lat, lon); platformFocusManager.clearFocus(); mapSearchQuery = "" }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(imageVector = if (b != null) Icons.Default.BatteryChargingFull else Icons.Default.Person,
                                                contentDescription = null, tint = if (b != null) GreenDark else Color(0xFF43BBF7), modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(name, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                                                if (distText.isNotEmpty()) Text(distText, fontSize = 12.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (!isChatScreen) {
                Box(modifier = Modifier.fillMaxSize().blur(16.dp)) {
                    mapLayer(batteries, friendsLocations, currentPanLocation)
                }
            }
        }

        navHostLayer()

        if (isAdmin && !isChatScreen) {
            LargeFloatingActionButton(
                onClick = { isPlacementMode = !isPlacementMode },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).padding(top = 40.dp),
                containerColor = if (isPlacementMode) Color.Red else GreenBright,
                contentColor = Color.White, shape = CircleShape
            ) { Icon(imageVector = Icons.Default.AddLocation, contentDescription = "Toggle Placement Mode") }
        }

        if (isPlacementMode) {
            Surface(modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp),
                color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp)) {
                Text("Placement Mode: Tap map to add battery", color = Color.White, modifier = Modifier.padding(8.dp), fontSize = 12.sp)
            }
        }

        if (!isChatScreen) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                WhiteNavBar(
                    selected = selected,
                    onItemSelected = { item ->
                        selected = item
                        navController.navigate(item.screen.route) {
                            popUpTo(Screen.Map.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp).size(68.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.15f), spotColor = Color.Black.copy(alpha = 0.15f))
                    .clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(LightningBlue)
                        .clickable {
                            navController.navigate(Screen.StartTrip.route) {
                                popUpTo(Screen.Map.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Filled.FlashOn, contentDescription = "Start Trip",
                        tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
fun FriendsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var friendRequests by remember { mutableStateOf<List<Pair<Friendship, Profile>>>(emptyList()) }
    var sentRequests by remember { mutableStateOf<List<Pair<Friendship, Profile>>>(emptyList()) }
    var currentFriends by remember { mutableStateOf<List<Pair<Friendship, Profile>>>(emptyList()) }
    var suggestedFriends by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var friendshipToRemove by remember { mutableStateOf<Pair<Friendship, Profile>?>(null) }

    fun refreshFriendsData() {
        scope.launch {
            val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            try {
                val allFriendships = supabase.postgrest["friendships"]
                    .select { filter { or { eq("user_id", currentUserId); eq("friend_id", currentUserId) } } }
                    .decodeList<Friendship>()
                val allIds = allFriendships.map { if (it.userId == currentUserId) it.friendId else it.userId }.distinct()
                val profiles = if (allIds.isNotEmpty()) {
                    supabase.postgrest["profiles"]
                        .select { filter { or { allIds.forEach { id -> eq("id", id) } } } }
                        .decodeList<Profile>()
                } else emptyList()

                currentFriends = allFriendships.filter { it.status == "accepted" }.mapNotNull { friendship ->
                    val otherId = if (friendship.userId == currentUserId) friendship.friendId else friendship.userId
                    profiles.find { it.id == otherId }?.let { friendship to it }
                }
                friendRequests = allFriendships.filter { it.status == "pending" && it.friendId == currentUserId }.mapNotNull { friendship ->
                    profiles.find { it.id == friendship.userId }?.let { friendship to it }
                }
                sentRequests = allFriendships.filter { it.status == "pending" && it.userId == currentUserId }.mapNotNull { friendship ->
                    profiles.find { it.id == friendship.friendId }?.let { friendship to it }
                }

                val acceptedFriendIds = currentFriends.map { it.second.id }
                val connectedIds = allIds + currentUserId
                var newSuggested = emptyList<Profile>()
                if (acceptedFriendIds.isNotEmpty()) {
                    val friendsOfFriends = supabase.postgrest["friendships"]
                        .select {
                            filter {
                                or {
                                    acceptedFriendIds.forEach { id -> eq("user_id", id) }
                                    acceptedFriendIds.forEach { id -> eq("friend_id", id) }
                                }
                                eq("status", "accepted")
                            }
                        }.decodeList<Friendship>()
                    val suggestedIdsRaw = friendsOfFriends.map { fof ->
                        if (acceptedFriendIds.contains(fof.userId)) fof.friendId else fof.userId
                    }.filter { !connectedIds.contains(it) }
                    val suggestionsCount = suggestedIdsRaw.groupingBy { it }.eachCount()
                    val topSuggestedIds = suggestionsCount.entries.sortedByDescending { it.value }.take(5).map { it.key }
                    if (topSuggestedIds.isNotEmpty()) {
                        newSuggested = supabase.postgrest["profiles"]
                            .select { filter { or { topSuggestedIds.forEach { id -> eq("id", id) } } } }
                            .decodeList<Profile>().sortedByDescending { suggestionsCount[it.id] ?: 0 }
                    }
                }
                suggestedFriends = newSuggested
            } catch (e: Exception) { println("Error fetching friends data: ${e.message}") }
        }
    }

    LaunchedEffect(Unit) { refreshFriendsData() }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f))
            .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp).padding(bottom = 100.dp)
    ) {
        Text("Friends", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF43BBF7),
            modifier = Modifier.padding(vertical = 16.dp))

        var isSearchFocused by remember { mutableStateOf(false) }
        val searchCornerRadius by animateDpAsState(targetValue = if (isSearchFocused) 16.dp else 27.dp, animationSpec = tween(300))
        val searchElevation by animateDpAsState(targetValue = if (isSearchFocused) 4.dp else 16.dp, animationSpec = tween(300))

        Box(
            modifier = Modifier.fillMaxWidth().height(54.dp)
                .shadow(elevation = searchElevation, shape = RoundedCornerShape(searchCornerRadius),
                    ambientColor = Color.Black.copy(alpha = 0.15f), spotColor = Color.Black.copy(alpha = 0.15f))
                .background(color = Color.White.copy(alpha = 0.9f), shape = RoundedCornerShape(searchCornerRadius))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = searchQuery.isEmpty(),
                        enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)),
                        exit  = androidx.compose.animation.fadeOut(animationSpec = tween(200))
                    ) {
                        Text("Search by email or username", color = Color.Gray.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().onFocusChanged { state -> isSearchFocused = state.isFocused }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF43BBF7), strokeWidth = 2.dp)
                } else if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            }
        }

        LaunchedEffect(searchQuery) {
            if (searchQuery.isBlank()) { searchResult = emptyList(); return@LaunchedEffect }
            kotlinx.coroutines.delay(500)
            isSearching = true
            try {
                searchResult = supabase.postgrest["profiles"]
                    .select { filter { or { ilike("email", "%$searchQuery%"); ilike("username", "%$searchQuery%") } } }
                    .decodeList<Profile>().filter { it.id != supabase.auth.currentUserOrNull()?.id }
            } catch (e: Exception) { println("Search error: ${e.message}") } finally { isSearching = false }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                androidx.compose.animation.AnimatedVisibility(
                    visible = searchResult.isNotEmpty(),
                    enter = androidx.compose.animation.expandVertically(animationSpec = tween(400, easing = LinearOutSlowInEasing)) +
                            androidx.compose.animation.fadeIn(animationSpec = tween(400)),
                    exit  = androidx.compose.animation.shrinkVertically(animationSpec = tween(300)) +
                            androidx.compose.animation.fadeOut(animationSpec = tween(300))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Search Results", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                        searchResult.forEach { profile ->
                            val isPending = sentRequests.any { it.second.id == profile.id } || friendRequests.any { it.second.id == profile.id }
                            val isFriend  = currentFriends.any { it.second.id == profile.id }
                            UserRow(
                                profile = profile,
                                actionLabel = when { isFriend -> "Friends"; isPending -> "Pending"; else -> "Add" },
                                enabled = !isFriend && !isPending,
                                onAction = {
                                    scope.launch {
                                        try {
                                            supabase.postgrest["friendships"].insert(
                                                Friendship(userId = supabase.auth.currentUserOrNull()?.id ?: "", friendId = profile.id)
                                            )
                                            searchQuery = ""; searchResult = emptyList(); refreshFriendsData()
                                        } catch (e: Exception) { println("Error sending request: ${e.message}") }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (suggestedFriends.isNotEmpty()) {
                item { Text("Suggested Friends", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                items(suggestedFriends) { profile ->
                    UserRow(profile = profile, actionLabel = "Add", onAction = {
                        scope.launch {
                            try {
                                supabase.postgrest["friendships"].insert(
                                    Friendship(userId = supabase.auth.currentUserOrNull()?.id ?: "", friendId = profile.id)
                                )
                                refreshFriendsData()
                            } catch (e: Exception) { println("Error: ${e.message}") }
                        }
                    })
                }
            }

            if (friendRequests.isNotEmpty()) {
                item { Text("Received Requests", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                items(friendRequests) { (friendship, profile) ->
                    UserRow(profile, "Accept") {
                        scope.launch {
                            try {
                                supabase.postgrest["friendships"].update({ set("status", "accepted") }) {
                                    filter { eq("id", friendship.id!!) }
                                }
                                refreshFriendsData()
                            } catch (e: Exception) { println("Error accepting: ${e.message}") }
                        }
                    }
                }
            }

            if (sentRequests.isNotEmpty()) {
                item { Text("Sent Requests (Pending)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                items(sentRequests) { (friendship, profile) ->
                    UserRow(profile, null, showOptions = true, onRemove = { friendshipToRemove = Pair(friendship, profile) }) {}
                }
            }

            item { Text("My Friends", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
            if (currentFriends.isEmpty()) {
                item { Text("No friends yet. Start searching!", color = Color.Gray, fontSize = 14.sp) }
            } else {
                items(currentFriends) { (friendship, profile) ->
                    UserRow(
                        profile = profile,
                        actionLabel = "Chat",
                        showOptions = true,
                        onRemove = { friendshipToRemove = Pair(friendship, profile) },
                        // ← FIXED: navigates to ChatScreen
                        onAction = {
                            navController.navigate(Screen.Chat.createRoute(profile.id, profile.username ?: profile.email?.substringBefore("@") ?: "Friend"))
                        }
                    )
                }
            }
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = friendshipToRemove != null,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
        exit  = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f),
        modifier = Modifier.fillMaxSize()
    ) {
        val fRemoval = friendshipToRemove
        if (fRemoval != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable { friendshipToRemove = null },
                contentAlignment = Alignment.Center
            ) {
                Card(modifier = Modifier.fillMaxWidth(0.8f).padding(16.dp), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Confirm Removal", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Are you sure you want to remove ${fRemoval.second.displayName ?: fRemoval.second.username ?: "this user"}?",
                            textAlign = TextAlign.Center, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(onClick = { friendshipToRemove = null },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray), shape = RoundedCornerShape(16.dp)) {
                                Text("Cancel", color = Color.Black)
                            }
                            Button(
                                onClick = {
                                    fRemoval.first.id?.let { id ->
                                        scope.launch {
                                            try {
                                                supabase.postgrest["friendships"].delete { filter { eq("id", id) } }
                                                refreshFriendsData()
                                            } catch (e: Exception) { println("Error removing: ${e.message}") }
                                        }
                                    }
                                    friendshipToRemove = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red), shape = RoundedCornerShape(16.dp)
                            ) { Text("Remove", color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

// ── UserRow ───────────────────────────────────────────────────────────────────
@Composable
fun UserRow(
    profile: Profile,
    actionLabel: String?,
    enabled: Boolean = true,
    showOptions: Boolean = false,
    onRemove: (() -> Unit)? = null,
    onAction: () -> Unit
) {
    // ← FIXED: properly declared as false so menu starts closed
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE1F5FE)),
                contentAlignment = Alignment.Center
            ) {
                Text(profile.username?.take(1)?.uppercase() ?: "?", fontWeight = FontWeight.Bold, color = Color(0xFF43BBF7))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.displayName ?: profile.username ?: "User", fontWeight = FontWeight.SemiBold)
                Text(profile.email ?: "", fontSize = 12.sp, color = Color.Gray)
            }

            // ← FIXED: actually renders the action button
            if (actionLabel != null) {
                val btnColor = when (actionLabel) {
                    "Chat"   -> Color(0xFF43BBF7)
                    "Accept" -> Color(0xFF22C55E)
                    else     -> Color(0xFF43BBF7)
                }
                Button(
                    onClick = onAction,
                    enabled = enabled,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // Show chat icon for Chat button, text for others
                    if (actionLabel == "Chat") {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Chat",
                            modifier = Modifier.size(16.dp), tint = Color.White)
                    } else {
                        Text(actionLabel, fontSize = 13.sp, color = Color.White)
                    }
                }
            }

            if (showOptions) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove", color = Color.Red) },
                            onClick = { showMenu = false; onRemove?.invoke() }
                        )
                    }
                }
            }
        }
    }
}

// ── ChatScreen ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(friendId: String, friendName: String, navController: NavController) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var newMessage by remember { mutableStateOf("") }
    val currentUserId = remember { supabase.auth.currentUserOrNull()?.id ?: "" }
    val listState = rememberLazyListState()
    val voltBlue = Color(0xFF43BBF7)

    LaunchedEffect(friendId) {
        try {
            messages = supabase.postgrest["messages"]
                .select {
                    filter {
                        or {
                            and { eq("sender_id", currentUserId); eq("receiver_id", friendId) }
                            and { eq("sender_id", friendId); eq("receiver_id", currentUserId) }
                        }
                    }
                }.decodeList<Message>().sortedBy { it.createdAt }
        } catch (e: Exception) { println("Error fetching messages: ${e.message}") }

        val channel = supabase.channel("chat_$friendId")
        val json = Json { ignoreUnknownKeys = true }
        channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "messages" }
            .onEach { action ->
                if (action is PostgresAction.Insert) {
                    val msg = json.decodeFromJsonElement<Message>(action.record)
                    if ((msg.senderId == currentUserId && msg.receiverId == friendId) ||
                        (msg.senderId == friendId && msg.receiverId == currentUserId)) {
                        messages = messages + msg
                    }
                }
            }.launchIn(this)
        channel.subscribe()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Text(friendName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(friendName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("VoltLoop Chat", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = voltBlue)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 12.dp, color = Color.White) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f).height(48.dp).background(Color(0xFFF5F5F5), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.CenterStart) {
                        BasicTextField(
                            value = newMessage, onValueChange = { newMessage = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            textStyle = TextStyle(fontSize = 15.sp, color = Color(0xFF1A1A1A)),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (newMessage.isEmpty()) Text("Message...", color = Color.Gray.copy(alpha = 0.7f), fontSize = 15.sp)
                                inner()
                            }
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    FloatingActionButton(
                        onClick = {
                            if (newMessage.isNotBlank()) {
                                scope.launch {
                                    try {
                                        supabase.postgrest["messages"].insert(
                                            Message(senderId = currentUserId, receiverId = friendId, content = newMessage)
                                        )
                                        newMessage = ""
                                    } catch (e: Exception) { println("Error sending: ${e.message}") }
                                }
                            }
                        },
                        containerColor = voltBlue, contentColor = Color.White, shape = CircleShape,
                        modifier = Modifier.size(48.dp), elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF9F9F9)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message ->
                val isMe = message.senderId == currentUserId
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (isMe) voltBlue else Color.White,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp,
                            bottomStart = if (isMe) 20.dp else 4.dp, bottomEnd = if (isMe) 4.dp else 20.dp),
                        shadowElevation = if (isMe) 0.dp else 2.dp,
                        modifier = Modifier.widthIn(min = 80.dp, max = 280.dp)
                    ) {
                        Text(message.content, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = if (isMe) Color.White else Color(0xFF1A1A1A), fontSize = 16.sp, lineHeight = 22.sp)
                    }
                }
            }
        }
    }
}
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
private val GreenMid = Color(0xFF166534)
private val GreenBright = Color(0xFF15803D)
private val GreenAccent = Color(0xFF4ADE80)
private val GreenLight = Color(0xFF86EFAC)
private val GreenStartBtn = Color(0xFF22C55E)
private val GreenStartBtnDark = Color(0xFF16A34A)
private val LabelInactive = Color(0xFFBBF7D0).copy(alpha = 0.60f)
private val IconInactive = Color(0xFFBBF7D0).copy(alpha = 0.65f)

sealed class Screen(val route: String) {
    object Settings : Screen("settings")
    object Map : Screen("map")
    object StartTrip : Screen("start_trip")
    object Friends : Screen("friends")
    object Store : Screen("store")
    object Chat : Screen("chat/{friendId}/{friendName}") {
        fun createRoute(friendId: String, friendName: String) = "chat/$friendId/$friendName"
    }
}

enum class NavItem(val label: String, val icon: ImageVector, val screen: Screen) {
    Settings("Settings", Icons.Filled.Settings, Screen.Settings),
    Friends("Friends", Icons.Filled.People, Screen.Friends),
    Map("Map", Icons.Filled.Map, Screen.Map),
    Store("Store", Icons.Filled.ShoppingCart, Screen.Store),
    StartTrip("Start", Icons.Filled.PlayArrow, Screen.StartTrip),
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
            isStartTrip -> Color.White
            isSelected -> GreenLight
            else -> IconInactive
        },
        animationSpec = tween(200),
        label = "iconColor",
    )

    val labelColor by animateColorAsState(
        targetValue = when {
            isStartTrip -> Color.White.copy(alpha = 0.9f)
            isSelected -> GreenLight
            else -> LabelInactive
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
                .padding(
                    horizontal = if (isStartTrip) 16.dp else 12.dp,
                    vertical = if (isStartTrip) 12.dp else 10.dp
                )
                .widthIn(min = if (isStartTrip) 64.dp else 60.dp),
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
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.05.sp,
            )
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
    var previousSelected by remember { mutableStateOf(NavItem.StartTrip) }
    val scope = rememberCoroutineScope()

    var isAdmin by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf<String?>(null) }
    var isPlacementMode by remember { mutableStateOf(false) }
    var batteries by remember { mutableStateOf(listOf<BatteryLocation>()) }
    var friendsLocations by remember { mutableStateOf(listOf<UserLocation>()) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isChatScreen = currentRoute?.startsWith("chat/") == true

    val selectedRef = remember { mutableStateOf(selected) }
    LaunchedEffect(selected) { selectedRef.value = selected }

    LaunchedEffect(currentRoute) {
        when {
            currentRoute == Screen.Settings.route -> { previousSelected = selected; selected = NavItem.Settings }
            currentRoute == Screen.Friends.route -> { previousSelected = selected; selected = NavItem.Friends }
            currentRoute == Screen.Map.route -> { previousSelected = selected; selected = NavItem.Map }
            currentRoute == Screen.StartTrip.route -> { previousSelected = selected; selected = NavItem.StartTrip }
            currentRoute == Screen.Store.route -> { previousSelected = selected; selected = NavItem.Store }
            currentRoute?.startsWith("chat/") == true -> { previousSelected = selected }
            currentRoute == null -> selected = previousSelected
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
                            or {
                                eq("user_id", currentUserId)
                                eq("friend_id", currentUserId)
                            }
                            eq("status", "accepted")
                        }
                    }
                }
                .decodeList<Friendship>()

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
        } catch (e: Exception) {
            println("Error fetching initial data: ${e.message}")
        }

        val locChannel = supabase.channel("locations_channel")
        val locFlow = locChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "user_locations"
        }
        locFlow.onEach { action: PostgresAction ->
            if (action is PostgresAction.Insert || action is PostgresAction.Update) {
                val updatedLoc = json.decodeFromJsonElement<UserLocation>(action.record)
                if (friendsLocations.none { it.id == updatedLoc.id }) return@onEach
                val existingUsername = friendsLocations.find { it.id == updatedLoc.id }?.username
                val locWithUsername = updatedLoc.copy(username = existingUsername ?: "Friend")
                friendsLocations = if (friendsLocations.any { it.id == updatedLoc.id }) {
                    friendsLocations.map { if (it.id == updatedLoc.id) locWithUsername else it }
                } else {
                    friendsLocations + locWithUsername
                }
            }
        }.launchIn(this)
        locChannel.subscribe()

        val batChannel = supabase.channel("batteries_channel")
        val batFlow = batChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "battery_locations"
        }
        batFlow.onEach { action: PostgresAction ->
            when (action) {
                is PostgresAction.Insert -> batteries = batteries + json.decodeFromJsonElement<BatteryLocation>(action.record)
                is PostgresAction.Update -> {
                    val updated = json.decodeFromJsonElement<BatteryLocation>(action.record)
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

    val mapLayer = remember(isPlacementMode, userId) {
        movableContentOf { currentBatteries: List<BatteryLocation>, currentFriends: List<UserLocation> ->
            MapView(
                modifier = Modifier.fillMaxSize(),
                batteries = currentBatteries,
                friends = currentFriends,
                onLocationUpdate = { lat, lon ->
                    scope.launch {
                        try {
                            userId?.let {
                                val loc = UserLocation(id = it, latitude = lat, longitude = lon)
                                supabase.postgrest["user_locations"].upsert(loc)
                            }
                        } catch (e: Exception) {
                            println("Error updating user location: ${e.message}")
                        }
                    }
                },
                onMapClick = if (isPlacementMode) { lat, lon ->
                    scope.launch {
                        try {
                            val newBattery = BatteryLocation(
                                latitude = lat,
                                longitude = lon,
                                name = "battery_${batteries.size + 1}",
                                userId = userId
                            )
                            supabase.postgrest["battery_locations"].insert(newBattery)
                        } catch (e: Exception) {
                            println("Error adding battery: ${e.message}")
                        }
                    }
                } else null,
                onFriendChatClick = { friendId, friendName ->
                    if (selectedRef.value == NavItem.Map) {
                        navController.navigate(Screen.Map.route) {
                            launchSingleTop = true
                        }
                    }
                    navController.navigate(Screen.Chat.createRoute(friendId, friendName))
                }
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
                composable(Screen.Friends.route) { FriendsScreen(navController) }
                composable(Screen.Store.route) { StoreScreen() }
                composable(Screen.Chat.route) { backStackEntry ->
                    val friendId = backStackEntry.arguments?.getString("friendId") ?: ""
                    val friendName = backStackEntry.arguments?.getString("friendName") ?: "Chat"
                    ChatScreen(friendId, friendName, navController)
                }
                composable(Screen.Map.route) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }

    // ─── THIS IS THE CHANGED PART ───
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {

        val showMap = selected == NavItem.Map || (previousSelected == NavItem.Map && isChatScreen)
        if (showMap) {
            mapLayer(batteries, friendsLocations)
        }

        navHostLayer()

        if (isAdmin && !isChatScreen) {
            LargeFloatingActionButton(
                onClick = { isPlacementMode = !isPlacementMode },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .padding(top = 40.dp),
                containerColor = if (isPlacementMode) Color.Red else GreenBright,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.AddLocation,
                    contentDescription = "Toggle Placement Mode"
                )
            }
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

        if (!isChatScreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .wrapContentSize()
            ) {
                GreenNavBar(
                    selected = selected,
                    onItemSelected = { item ->
                        previousSelected = selected
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
}

@Composable
fun FriendsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var friendRequests by remember { mutableStateOf<List<Pair<Friendship, Profile>>>(emptyList()) }
    var sentRequests by remember { mutableStateOf<List<Pair<Friendship, Profile>>>(emptyList()) }
    var currentFriends by remember { mutableStateOf<List<Pair<Friendship, Profile>>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    fun refreshFriendsData() {
        scope.launch {
            val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            try {
                val allFriendships = supabase.postgrest["friendships"]
                    .select {
                        filter {
                            or {
                                eq("user_id", currentUserId)
                                eq("friend_id", currentUserId)
                            }
                        }
                    }.decodeList<Friendship>()

                val allIds = allFriendships.map { if (it.userId == currentUserId) it.friendId else it.userId }.distinct()
                val profiles = if (allIds.isNotEmpty()) {
                    supabase.postgrest["profiles"]
                        .select { filter { or { allIds.forEach { id -> eq("id", id) } } } }
                        .decodeList<Profile>()
                } else emptyList()

                currentFriends = allFriendships.filter { it.status == "accepted" }.mapNotNull { friendship ->
                    val otherId = if (friendship.userId == currentUserId) friendship.friendId else friendship.userId
                    val profile = profiles.find { it.id == otherId }
                    if (profile != null) friendship to profile else null
                }
                friendRequests = allFriendships.filter { it.status == "pending" && it.friendId == currentUserId }.mapNotNull { friendship ->
                    val profile = profiles.find { it.id == friendship.userId }
                    if (profile != null) friendship to profile else null
                }
                sentRequests = allFriendships.filter { it.status == "pending" && it.userId == currentUserId }.mapNotNull { friendship ->
                    val profile = profiles.find { it.id == friendship.friendId }
                    if (profile != null) friendship to profile else null
                }
            } catch (e: Exception) {
                println("Error fetching friends data: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) { refreshFriendsData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp)
    ) {
        Text(
            "Friends",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = GreenDark,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by email or username") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        isSearching = true
                        scope.launch {
                            try {
                                searchResult = supabase.postgrest["profiles"]
                                    .select {
                                        filter {
                                            or {
                                                ilike("email", "%$searchQuery%")
                                                ilike("username", "%$searchQuery%")
                                            }
                                        }
                                    }.decodeList<Profile>()
                                    .filter { it.id != supabase.auth.currentUserOrNull()?.id }
                            } catch (e: Exception) {
                                println("Search error: ${e.message}")
                            } finally {
                                isSearching = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Add, null, tint = GreenBright)
                    }
                }
            }
        )

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (searchResult.isNotEmpty()) {
                item { Text("Search Results", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                items(searchResult) { profile ->
                    val isPending = sentRequests.any { it.second.id == profile.id } || friendRequests.any { it.second.id == profile.id }
                    val isFriend = currentFriends.any { it.second.id == profile.id }
                    UserRow(
                        profile = profile,
                        actionLabel = when {
                            isFriend -> "Friends"
                            isPending -> "Pending"
                            else -> "Send Request"
                        },
                        enabled = !isFriend && !isPending,
                        onAction = {
                            scope.launch {
                                try {
                                    val friendship = Friendship(
                                        userId = supabase.auth.currentUserOrNull()?.id ?: "",
                                        friendId = profile.id
                                    )
                                    supabase.postgrest["friendships"].insert(friendship)
                                    searchQuery = ""
                                    searchResult = emptyList()
                                    refreshFriendsData()
                                } catch (e: Exception) {
                                    println("Error sending request: ${e.message}")
                                }
                            }
                        }
                    )
                }
            }

            if (friendRequests.isNotEmpty()) {
                item { Text("Received Requests", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                items(friendRequests) { (friendship, profile) ->
                    UserRow(profile, "Accept") {
                        scope.launch {
                            try {
                                supabase.postgrest["friendships"].update({
                                    set("status", "accepted")
                                }) { filter { eq("id", friendship.id!!) } }
                                refreshFriendsData()
                            } catch (e: Exception) {
                                println("Error accepting friend: ${e.message}")
                            }
                        }
                    }
                }
            }

            if (sentRequests.isNotEmpty()) {
                item { Text("Sent Requests (Pending)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                items(sentRequests) { (friendship, profile) ->
                    UserRow(profile, null, showOptions = true, onRemove = {
                        scope.launch {
                            try {
                                supabase.postgrest["friendships"].delete { filter { eq("id", friendship.id!!) } }
                                refreshFriendsData()
                            } catch (e: Exception) {
                                println("Error removing request: ${e.message}")
                            }
                        }
                    }) {}
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
                        onRemove = {
                            scope.launch {
                                try {
                                    supabase.postgrest["friendships"].delete { filter { eq("id", friendship.id!!) } }
                                    refreshFriendsData()
                                } catch (e: Exception) {
                                    println("Error removing friend: ${e.message}")
                                }
                            }
                        },
                        onAction = {
                            navController.navigate(Screen.Chat.createRoute(profile.id, profile.username ?: "Friend"))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UserRow(
    profile: Profile,
    actionLabel: String?,
    enabled: Boolean = true,
    showOptions: Boolean = false,
    onRemove: (() -> Unit)? = null,
    onAction: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(GreenLight),
                contentAlignment = Alignment.Center
            ) {
                Text(profile.username?.take(1)?.uppercase() ?: "?", fontWeight = FontWeight.Bold, color = GreenDark)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.displayName ?: "User", fontWeight = FontWeight.SemiBold)
                Text(profile.email ?: "", fontSize = 12.sp, color = Color.Gray)
            }
            if (actionLabel != null) {
                Button(
                    onClick = onAction,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenBright,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(actionLabel, fontSize = 12.sp)
                }
            }
            if (showOptions) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remove", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                onRemove?.invoke()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(friendId: String, friendName: String, navController: NavController) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var newMessage by remember { mutableStateOf("") }
    val currentUserId = remember { supabase.auth.currentUserOrNull()?.id ?: "" }
    val listState = rememberLazyListState()

    LaunchedEffect(friendId) {
        try {
            val fetchedMessages = supabase.postgrest["messages"]
                .select {
                    filter {
                        or {
                            and {
                                eq("sender_id", currentUserId)
                                eq("receiver_id", friendId)
                            }
                            and {
                                eq("sender_id", friendId)
                                eq("receiver_id", currentUserId)
                            }
                        }
                    }
                }.decodeList<Message>()
            messages = fetchedMessages.sortedBy { it.createdAt }
        } catch (e: Exception) {
            println("Error fetching messages: ${e.message}")
        }

        val channel = supabase.channel("chat_$friendId")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }
        val json = Json { ignoreUnknownKeys = true }
        flow.onEach { action: PostgresAction ->
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
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(friendName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GreenDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (newMessage.isNotBlank()) {
                                scope.launch {
                                    try {
                                        val msg = Message(
                                            senderId = currentUserId,
                                            receiverId = friendId,
                                            content = newMessage
                                        )
                                        supabase.postgrest["messages"].insert(msg)
                                        newMessage = ""
                                    } catch (e: Exception) {
                                        println("Error sending message: ${e.message}")
                                    }
                                }
                            }
                        },
                        containerColor = GreenBright,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F5F5)),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                val isMe = message.senderId == currentUserId
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        color = if (isMe) GreenBright else Color.White,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 0.dp,
                            bottomEnd = if (isMe) 0.dp else 16.dp
                        ),
                        tonalElevation = 1.dp,
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (isMe) Color.White else Color.Black,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

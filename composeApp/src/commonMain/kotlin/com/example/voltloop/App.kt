package com.example.voltloop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App() {
    val sessionStatus by supabase.auth.sessionStatus.collectAsState()

    VoltLoopTheme {
        when (sessionStatus) {
            is SessionStatus.Authenticated -> {
                val user = (sessionStatus as SessionStatus.Authenticated).session.user
                AppState.currentUser.value = user

                var needsUsername by remember { mutableStateOf(false) }
                var isCheckingProfile by remember { mutableStateOf(true) }

                LaunchedEffect(user?.email) {
                    try {
                        val email = user?.email ?: run {
                            isCheckingProfile = false
                            return@LaunchedEffect
                        }

                        // 1. Fetch the current user's profile
                        val profile = supabase.postgrest["profiles"]
                            .select { filter { eq("email", email) } }
                            .decodeSingle<Profile>()

                        // Use intValue since totalPoints is mutableIntStateOf

                        AppState.totalPoints.value = (profile.points ?: 0).toInt()

                        // 2. Check if username is set
                        needsUsername = profile.username.isNullOrBlank()

                        // 3. Fetch friendships
                        val userId = user.id
                        val friendships = supabase.postgrest["friendships"]
                            .select {
                                filter {
                                    or {
                                        eq("user_id", userId)
                                        eq("friend_id", userId)
                                    }
                                }
                            }
                            .decodeList<Friendship>()

                        // 4. Extract friend IDs and fetch their profiles
                        val friendIds = friendships.map { friendship ->
                            if (friendship.userId == userId) friendship.friendId else friendship.userId
                        }

                        if (friendIds.isNotEmpty()) {
                            val friendProfiles = supabase.postgrest["profiles"]
                                .select { filter { isIn("id", friendIds) } }
                                .decodeList<Profile>()
                            
                            // Include the current user's profile in the global list so UI markers/avatars work
                            val allProfiles = (friendProfiles + profile).distinctBy { it.id }
                            AppState.friends.value = allProfiles.sortedByDescending { it.points }
                        } else {
                            // Even if no friends, include the current user's profile
                            AppState.friends.value = listOf(profile)
                        }

                        println("FRIENDS_LIST: ${AppState.friends.value.map { "${it.username} - ${it.points} pts" }}")

                    } catch (e: Exception) {
                        println("FETCH_ERROR: ${e.message}")
                    } finally {
                        // ← FIXED: always set to false so loading screen goes away
                        isCheckingProfile = false
                    }
                }

                // ── Real-time Global Listeners ──────────────────────────
                LaunchedEffect(user?.id) {
                    val currentUserId = user?.id ?: return@LaunchedEffect
                    val json = Json { ignoreUnknownKeys = true }

                    // 1. Listen for new messages
                    val msgChannel = supabase.channel("global_messages")
                    msgChannel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "messages" }
                        .onEach { action ->
                            if (action is PostgresAction.Insert) {
                                try {
                                    val msg = json.decodeFromJsonElement(Message.serializer(), action.record)
                                    if (msg.receiverId != currentUserId) return@onEach

                                    // Try to resolve sender name
                                    val senderName = try {
                                        val profileResource = supabase.postgrest["profiles"]
                                            .select { filter { eq("id", msg.senderId) } }
                                            .decodeSingle<Profile>()
                                        profileResource.username ?: profileResource.email?.substringBefore("@") ?: "Someone"
                                    } catch (e: Exception) { "Someone" }

                                    val notif = AppNotification(
                                        id = msg.id ?: msg.createdAt ?: "${Clock.System.now().toEpochMilliseconds()}",
                                        type = NotificationType.MESSAGE,
                                        title = "New message from $senderName",
                                        body = if (msg.content.length > 60) msg.content.take(60) + "…" else msg.content,
                                        timestamp = msg.createdAt?.replace("T", " ")?.substringBefore(".")
                                    )
                                    showLocalNotification(notif.title, notif.body)
                                    AppState.globalNotifications.value = listOf(notif) + AppState.globalNotifications.value
                                } catch (e: Exception) {
                                    println("GLOBAL_MSG_ERROR: ${e.message}")
                                }
                            }
                        }.launchIn(this)
                    msgChannel.subscribe()

                    // 2. Listen for profile points updates
                    val profileChannel = supabase.channel("global_profile")
                    var lastKnownPoints: Long? = null
                    
                    // Seed the last known points
                    try {
                        val initialProfile = supabase.postgrest["profiles"]
                            .select { filter { eq("id", currentUserId) } }
                            .decodeSingle<Profile>()
                        lastKnownPoints = initialProfile.points
                        AppState.totalPoints.value = initialProfile.points.toInt()
                    } catch (_: Exception) {}

                    profileChannel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "profiles" }
                        .onEach { action ->
                            if (action is PostgresAction.Update) {
                                try {
                                    val profileUpdate = json.decodeFromJsonElement(Profile.serializer(), action.record)
                                    if (profileUpdate.id != currentUserId) return@onEach
                                    
                                    val prev = lastKnownPoints
                                    if (prev != null && profileUpdate.points > prev) {
                                        val gained = profileUpdate.points - prev
                                        val notif = AppNotification(
                                            id = "${Clock.System.now().toEpochMilliseconds()}",
                                            type = NotificationType.POINTS,
                                            title = "You earned $gained points!",
                                            body = "Your total is now ${profileUpdate.points} pts",
                                            timestamp = null
                                        )
                                        showLocalNotification(notif.title, notif.body)
                                        AppState.globalNotifications.value = listOf(notif) + AppState.globalNotifications.value
                                    }
                                    lastKnownPoints = profileUpdate.points
                                    AppState.totalPoints.value = profileUpdate.points.toInt()
                                } catch (e: Exception) {
                                    println("GLOBAL_PROFILE_UPDATE_ERROR: ${e.message}")
                                }
                            }
                        }.launchIn(this)
                    profileChannel.subscribe()
                }

                when {
                    isCheckingProfile -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    needsUsername -> {
                        SetUsernameScreen(onComplete = { needsUsername = false })
                    }
                    else -> {
                        Nav_Bar_ussage()
                    }
                }
            }

            is SessionStatus.Initializing -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                LoginScreen(onLoginSuccess = {})
            }
        }
    }
}

@Composable
fun SetUsernameScreen(onComplete: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val user = AppState.currentUser.value

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Choose your VoltLoop username",
                color = Color(0xFF43BBF7),
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                    placeholder = { Text("username", color = Color.Gray) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor   = Color(0xFFF5F5F5),
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedBorderColor      = Color.Transparent,
                        unfocusedBorderColor    = Color.Transparent
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        if (username.isBlank() || user == null) return@Button
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                supabase.postgrest["profiles"]
                                    .update({
                                        set("username", username)
                                        set("display_name", username)
                                    }) {
                                        filter { eq("id", user.id) }
                                    }
                                onComplete()
                            } catch (e: Exception) {
                                try {
                                    supabase.postgrest["profiles"].upsert(
                                        buildJsonObject {
                                            put("id", user.id)
                                            put("username", username)
                                            put("display_name", username)
                                            put("email", user.email ?: "")
                                        }
                                    )
                                    onComplete()
                                } catch (e2: Exception) {
                                    errorMessage = "Failed to save: ${e2.message?.take(80)}"
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43BBF7)),
                    contentPadding = PaddingValues()
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text("→", color = Color.White, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }

            if (errorMessage != null) {
                Text(errorMessage!!, color = Color.Red, fontSize = 13.sp)
            }
        }
    }
}
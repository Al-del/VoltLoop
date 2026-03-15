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
                user?.email?.let { email ->
                    try {
                        // 1. Fetch the current user's profile
                        val profile = supabase.postgrest["profiles"]
                            .select { filter { eq("email", email) } }
                            .decodeSingle<Profile>()
                        AppState.totalPoints.value = profile.points.toInt()

                        // 2. Fetch all friendships where this user is involved
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

                        // 3. Extract the friend IDs (the other side of each row)
                        val friendIds = friendships.map { friendship ->
                            if (friendship.userId == userId) friendship.friendId
                            else friendship.userId
                        }

                        // 4. Fetch each friend's profile and sort by points descending
                        if (friendIds.isNotEmpty()) {
                            val friendProfiles = supabase.postgrest["profiles"]
                                .select {
                                    filter {
                                        isIn("id", friendIds)
                                    }
                                }
                                .decodeList<Profile>()

                            AppState.friends.value = friendProfiles.sortedByDescending { it.points }
                        }
                        println("FRIENDS_LIST: ${AppState.friends.value.map { "${it.username} - ${it.points} pts" }}")
                    } catch (e: Exception) {
                        println("FETCH_ERROR: ${e.message}")
                    }
                }
            }

            if (isCheckingProfile) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (needsUsername) {
                SetUsernameScreen(
                    onComplete = {
                        needsUsername = false
                    }
                )
            } else {
                Nav_Bar_ussage()
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
    } // end when
    } // end VoltLoopTheme
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
                    placeholder = { androidx.compose.material3.Text("username", color = Color.Gray) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF5F5F5),
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
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
                                // Try UPDATE first (profile likely exists from trigger)
                                supabase.postgrest["profiles"]
                                    .update({
                                        set("username", username)
                                        set("display_name", username)
                                    }) {
                                        filter { eq("id", user.id) }
                                    }
                                onComplete()
                            } catch (e: Exception) {
                                // Fallback: try upsert (if profile doesn't exist yet)
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
                    else androidx.compose.material3.Text("→", color = Color.White, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }

            if (errorMessage != null) {
                Text(
                    errorMessage!!,
                    color = Color.Red,
                    fontSize = 13.sp
                )
            }
        }
    }
}
package com.example.voltloop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

@Composable
fun App() {
    val sessionStatus by supabase.auth.sessionStatus.collectAsState()

    when (sessionStatus) {
        is SessionStatus.Authenticated -> {
            val user = (sessionStatus as SessionStatus.Authenticated).session.user

            AppState.currentUser.value = user

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

            Nav_Bar_ussage()
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
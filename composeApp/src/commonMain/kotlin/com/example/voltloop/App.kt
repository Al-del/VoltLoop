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
                        val profile = supabase.postgrest["profiles"]
                            .select { filter { eq("email", email) } }
                            .decodeSingle<Profile>()
                        AppState.totalPoints.value = profile.points.toInt()
                    } catch (e: Exception) {
                        println("POINTS_FETCH_ERROR: ${e.message}")
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
package com.example.voltloop

import androidx.compose.runtime.*
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth

@Composable
fun App() {
    val sessionStatus by supabase.auth.sessionStatus.collectAsState()

    when (sessionStatus) {
        is SessionStatus.Authenticated -> {
            Nav_Bar_ussage()
        }
        else -> {
            LoginScreen(onLoginSuccess = {
                // Supabase automatically updates sessionStatus
            })
        }
    }
}

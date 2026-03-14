package com.example.voltloop

import androidx.compose.runtime.mutableStateOf
import io.github.jan.supabase.auth.user.UserInfo

object AppState {
    var currentUser = mutableStateOf<UserInfo?>(null)
    var totalPoints = mutableStateOf(0)
}
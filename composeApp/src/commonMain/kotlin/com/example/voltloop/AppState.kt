package com.example.voltloop

import androidx.compose.runtime.mutableStateOf
import io.github.jan.supabase.auth.user.UserInfo

object AppState {
    var currentUser = mutableStateOf<UserInfo?>(null)
    var totalPoints = mutableStateOf(0)
    val friends = mutableStateOf<List<Profile>>(emptyList())
    val globalNotifications = mutableStateOf<List<AppNotification>>(emptyList())
}

data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timestamp: String?
)

enum class NotificationType { POINTS, MESSAGE }
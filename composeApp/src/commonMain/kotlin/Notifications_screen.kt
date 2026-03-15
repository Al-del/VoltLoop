package com.example.voltloop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

// Definitions moved to AppState.kt

@Composable
fun NotificationsScreen(onBackClick: () -> Unit = {}) {
    val backgroundColor = Color(0xFFF4F6F8)
    val textSecondary   = Color(0xFF888888)
    val blueAccent      = Color(0xFF43BBF7)
    val blueSoft        = Color(0xFFE8F6FD)

    val currentUserId = remember { supabase.auth.currentUserOrNull()?.id ?: "" }

    val notifications by AppState.globalNotifications

    // ── Initial load: last 20 received messages ───────────────
    LaunchedEffect(currentUserId) {
        if (currentUserId.isEmpty()) return@LaunchedEffect
        try {
            val json = Json { ignoreUnknownKeys = true }

            // Load recent messages received by this user
            val messages = supabase.postgrest["messages"]
                .select { filter { eq("receiver_id", currentUserId) } }
                .decodeList<Message>()

            // Fetch sender profiles for display names
            val senderIds = messages.map { it.senderId }.distinct()
            val profiles = if (senderIds.isNotEmpty()) {
                supabase.postgrest["profiles"]
                    .select { filter { or { senderIds.forEach { id -> eq("id", id) } } } }
                    .decodeList<Profile>()
            } else emptyList()

            val msgNotifications = messages
                .sortedByDescending { it.createdAt }
                .take(20)
                .map { msg ->
                    val sender = profiles.find { it.id == msg.senderId }
                    val senderName = sender?.username ?: sender?.email?.substringBefore("@") ?: "Someone"
                    AppNotification(
                        id = msg.id ?: msg.createdAt ?: "",
                        type = NotificationType.MESSAGE,
                        title = "New message from $senderName",
                        body = if (msg.content.length > 60) msg.content.take(60) + "…" else msg.content,
                        timestamp = msg.createdAt?.replace("T", " ")?.substringBefore(".")
                    )
                }

            // Load profile for points history via trip history rows
            val email = AppState.currentUser.value?.email
            val tripNotifications = if (email != null) {
                try {
                    val trips = supabase.postgrest["History"]
                        .select { filter { eq("Name", email) } }
                        .decodeList<TripHistory>()
                    trips.sortedByDescending { it.time }.take(10).map { trip ->
                        AppNotification(
                            id = trip.time,
                            type = NotificationType.POINTS,
                            title = "You earned ${trip.points} points!",
                            body = "Trip: ${trip.name}",
                            timestamp = trip.time.replace("T", " ").substringBefore(".")
                        )
                    }
                } catch (e: Exception) { emptyList() }
            } else emptyList()

            // Merge and sort by timestamp descending
            AppState.globalNotifications.value = (msgNotifications + tripNotifications)
                .sortedByDescending { it.timestamp ?: "" }

        } catch (e: Exception) {
            println("NotificationsScreen load error: ${e.message}")
        }
    }

    // ── Real-time listeners moved to App.kt Global Scope ───────────


    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // ── Header ─────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(top = 60.dp, bottom = 20.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.offset(x = (-12).dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1A1A1A)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Notifications",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = blueAccent
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Points earned and messages received",
                        fontSize = 14.sp,
                        color = textSecondary
                    )
                }
            }
        }

        // ── Empty state ────────────────────────────────────────
        if (notifications.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No notifications yet",
                            color = textSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // ── Notification cards ─────────────────────────────────
        items(notifications, key = { it.id }) { notif ->
            val (icon, iconBg) = when (notif.type) {
                NotificationType.POINTS  -> Icons.Default.FlashOn to Color(0xFFE8F6FD)
                NotificationType.MESSAGE -> Icons.Default.Chat to Color(0xFFE8F6FD)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) { 
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = blueAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            notif.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color(0xFF1A1A1A)
                        )
                        if (notif.body.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                notif.body,
                                fontSize = 13.sp,
                                color = textSecondary
                            )
                        }
                        if (notif.timestamp != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                notif.timestamp,
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
package com.example.voltloop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val BlueAccent  = Color(0xFF43BBF7)
private val BlueSoft    = Color(0xFFE8F6FD)
private val GoldColor   = Color(0xFFFFD700)
private val SilverColor = Color(0xFFC0C0C0)
private val BronzeColor = Color(0xFFCD7F32)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecond  = Color(0xFF888888)
private val CardBg      = Color(0xFFFFFFFF)
private val PageBg      = Color(0xFFF4F6F8)

@Serializable
data class TripHistory(
    @SerialName("Name")   val name: String,
    @SerialName("TIme")   val time: String,
    @SerialName("Points") val points: Int
)

// Helper to resolve a display name from a Profile,
// preferring username but falling back to the email local-part.
private fun Profile.displayName(): String =
    username?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore("@")
        ?: "?"

@Composable
fun AccountScreen() {
    var showLeaderboard by remember { mutableStateOf(false) }

    val username = AppState.currentUser.value?.let { user ->
        AppState.friends.value.find { it.id == user.id }?.displayName()
            ?: user.email?.substringBefore("@")
            ?: "Rider"
    } ?: "Rider"

    var history by remember { mutableStateOf<List<TripHistory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val email = AppState.currentUser.value?.email ?: return@LaunchedEffect
            println("TEST EMAIL $email")
            val profile = supabase.postgrest["profiles"]
                .select { filter { eq("email", email) } }
                .decodeSingle<Profile>()
            println("TEST PROFILE $profile")
            val rows = supabase.postgrest["History"]
                .select { filter { eq("Name", email) } }
                .decodeList<TripHistory>()

            println("TEST HISTORY size=${rows.size}")
            rows.forEachIndexed { i, trip ->
                println("TEST HISTORY [$i] name=${trip.name} time=${trip.time} points=${trip.points}")
            }

            history = rows.sortedByDescending { it.time }
        } catch (e: Exception) {
            println("HISTORY_FETCH_ERROR: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    val friends = AppState.friends.value

    // Build a combined leaderboard: friends + current user, sorted by points descending
    val currentUserProfile = AppState.currentUser.value?.let { user ->
        friends.find { it.id == user.id }
            ?: Profile(
                id = user.id,
                email = user.email,
                username = user.email?.substringBefore("@"),
                points = AppState.totalPoints.value.toLong()

            )
    }
    val leaderboard = (friends + listOfNotNull(currentUserProfile))
        .distinctBy { it.id }
        .sortedByDescending { it.points }

    val listFriends = if (leaderboard.size >= 3) leaderboard.drop(3) else leaderboard

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // ── Header ──────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(top = 52.dp, bottom = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(BlueSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (showLeaderboard) "🏆" else username.take(1).uppercase(),
                            fontSize = if (showLeaderboard) 28.sp else 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BlueAccent
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (showLeaderboard) "Leaderboard" else username,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (showLeaderboard) "Friends ranking" else "Trip History",
                        fontSize = 13.sp,
                        color = TextSecond
                    )

                    Spacer(Modifier.height(16.dp))

                    // ── Toggle pill ──────────────────────────────
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(PageBg)
                            .padding(4.dp)
                    ) {
                        Row {
                            ToggleTab(
                                label = "⚡  History",
                                selected = !showLeaderboard,
                                onClick = { showLeaderboard = false }
                            )
                            Spacer(Modifier.width(4.dp))
                            ToggleTab(
                                label = "🏆  Leaderboard",
                                selected = showLeaderboard,
                                onClick = { showLeaderboard = true }
                            )
                        }
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════
        // HISTORY VIEW
        // ════════════════════════════════════════════════════════
        if (!showLeaderboard) {
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = BlueAccent) }
                }
            } else if (history.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No trips yet ⚡\nStart riding to see your history!",
                            color = TextSecond,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "${history.size} TRIPS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecond,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                items(history) { trip -> TripCard(trip = trip) }
            }
        }

        // ════════════════════════════════════════════════════════
        // LEADERBOARD VIEW
        // ════════════════════════════════════════════════════════
        if (showLeaderboard) {
            if (leaderboard.size >= 3) {
                item {
                    Spacer(Modifier.height(20.dp))
                    PodiumRow(friends = leaderboard.take(3))
                    Spacer(Modifier.height(20.dp))
                }
            }

            if (listFriends.isNotEmpty()) {
                item {
                    Text(
                        text = "RANKINGS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecond,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (listFriends.isEmpty() && leaderboard.size < 3) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No friends yet 😢\nAdd some to see rankings!",
                            color = TextSecond,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(listFriends) { index, profile ->
                    val rank = index + (if (leaderboard.size >= 3) 4 else 1)
                    val isCurrentUser = profile.id == AppState.currentUser.value?.id
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        FriendRankCard(rank = rank, profile = profile, highlight = isCurrentUser)
                    }
                }
            }
        }
    }
}

// ── Toggle tab button ────────────────────────────────────────
@Composable
fun ToggleTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) BlueAccent else Color.Transparent,
        animationSpec = tween(200)
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else TextSecond,
        animationSpec = tween(200)
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

// ── Trip card ────────────────────────────────────────────────
@Composable
fun TripCard(trip: TripHistory) {
    val displayTime = try {
        trip.time.replace("T", " ").substringBefore(".")
    } catch (e: Exception) { trip.time }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(BlueSoft),
                contentAlignment = Alignment.Center
            ) { Text("⚡", fontSize = 20.sp) }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(trip.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                Text(displayTime, fontSize = 12.sp, color = TextSecond)
            }

            Surface(shape = RoundedCornerShape(20.dp), color = BlueSoft) {
                Text(
                    text = "+${trip.points} pts",
                    color = BlueAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ── Podium row ───────────────────────────────────────────────
@Composable
fun PodiumRow(friends: List<Profile>) {
    val order   = listOf(1, 0, 2)
    val heights = listOf(72.dp, 100.dp, 56.dp)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        order.forEachIndexed { i, friendIndex ->
            val profile     = friends[friendIndex]
            val rank        = friendIndex + 1
            val medalColor  = when (rank) { 1 -> GoldColor; 2 -> SilverColor; else -> BronzeColor }
            val medal       = when (rank) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }
            val displayName = profile.displayName()
            val isCurrentUser = profile.id == AppState.currentUser.value?.id

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(medal, fontSize = if (rank == 1) 28.sp else 22.sp)
                Spacer(Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .size(if (rank == 1) 60.dp else 48.dp)
                        .shadow(
                            6.dp, CircleShape,
                            ambientColor = if (isCurrentUser) BlueAccent.copy(alpha = 0.5f) else medalColor.copy(alpha = 0.4f),
                            spotColor    = if (isCurrentUser) BlueAccent.copy(alpha = 0.5f) else medalColor.copy(alpha = 0.4f)
                        )
                        .clip(CircleShape)
                        .background(if (isCurrentUser) BlueSoft else Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        color = BlueAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (rank == 1) 24.sp else 18.sp
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isCurrentUser) {
                        Spacer(Modifier.width(3.dp))
                        Text("(you)", color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text("${profile.points} pts", color = medalColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(heights[i])
                        .shadow(4.dp, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(medalColor))
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("#$rank", color = TextSecond, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// ── Friend rank card ─────────────────────────────────────────
@Composable
fun FriendRankCard(rank: Int, profile: Profile, highlight: Boolean = false) {
    val displayName = profile.displayName()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) BlueSoft else CardBg,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#$rank",
                color = if (highlight) BlueAccent else TextSecond,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.width(36.dp)
            )

            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(if (highlight) BlueAccent else BlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    displayName.take(1).uppercase(),
                    color = if (highlight) Color.White else BlueAccent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayName,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (highlight) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = BlueAccent) {
                        Text(
                            "you",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Surface(shape = RoundedCornerShape(20.dp), color = if (highlight) BlueAccent else BlueSoft) {
                Text(
                    "${profile.points} pts",
                    color = if (highlight) Color.White else BlueAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
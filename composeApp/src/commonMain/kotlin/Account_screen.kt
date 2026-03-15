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
import coil3.compose.AsyncImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
internal fun Profile.displayName(): String =
    username?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore("@")
        ?: "?"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onNavigateToNotifications: () -> Unit = {}) {
    val username = AppState.currentUser.value?.let { user ->
        AppState.friends.value.find { it.id == user.id }?.displayName()
            ?: user.email?.substringBefore("@")
            ?: "Rider"
    } ?: "Rider"

    var history by remember { mutableStateOf<List<TripHistory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditProfile by remember { mutableStateOf(false) }
    
    val profileImage = AppState.friends.value.find { it.id == AppState.currentUser.value?.id }?.avatarUrl
    val imagePicker = rememberImagePicker()
    val scope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val email = AppState.currentUser.value?.email ?: return@LaunchedEffect
            println("TEST EMAIL $email")
            val profile = supabase.postgrest["profiles"]
                .select { filter { eq("email", email) } }
                .decodeSingle<Profile>()
            println("TEST PROFILE $profile")
            
            // Sync the fetched profile into AppState so the avatarImage variable works immediately
            val currentFriends = AppState.friends.value.toMutableList()
            val existingIdx = currentFriends.indexOfFirst { it.id == profile.id }
            if (existingIdx != -1) {
                currentFriends[existingIdx] = profile
            } else {
                currentFriends.add(profile)
            }
            AppState.friends.value = currentFriends
            
            val rows = supabase.postgrest["History"]
                .select { filter { eq("Name", email) } }  // use email directly, not profile.username
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

    val friends    = AppState.friends.value
    val listFriends = if (friends.size >= 3) friends.drop(3) else friends

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
        // ── Header ──────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Color.White)
                    .padding(top = 100.dp, bottom = 40.dp), // Increased padding to push down
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(88.dp)
                        ) {
                            if (profileImage != null) {
                                AsyncImage(
                                    model = profileImage,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(BlueSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = username.take(1).uppercase(),
                                        fontSize = 40.sp, // Bigger text
                                        fontWeight = FontWeight.ExtraBold,
                                        color = BlueAccent
                                    )
                                }
                            }
                            
                            // Edit Badge (+)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(28.dp)
                                    .offset(x = 4.dp, y = 4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(BlueAccent)
                                    .clickable {
                                        if (!isUploading) {
                                            imagePicker.launch { bytes ->
                                                if (bytes != null) {
                                                    val uId = AppState.currentUser.value?.id ?: return@launch
                                                    isUploading = true
                                                    scope.launch {
                                                        try {
                                                            println("AVATAR_UPLOAD_START: userId=$uId")
                                                            val url = uploadAvatarAndSave(bytes, uId)
                                                            println("AVATAR_UPLOAD_FINISH: newUrl=$url")
                                                            if (url != null) {
                                                                // Refresh friends list to trigger UI update
                                                                val newList = AppState.friends.value.toMutableList()
                                                                val idx = newList.indexOfFirst { it.id == uId }
                                                                
                                                                if (idx != -1) {
                                                                    newList[idx] = newList[idx].copy(avatarUrl = url)
                                                                } else {
                                                                    // Profile isn't in friends list yet, append a stub so UI refreshes
                                                                    val email = AppState.currentUser.value?.email
                                                                    newList.add(Profile(id = uId, avatarUrl = url, email = email))
                                                                }
                                                                
                                                                AppState.friends.value = newList
                                                            }
                                                        } catch (e: Exception) {
                                                            println("AVATAR_UPLOAD_UI_ERROR: ${e.message}")
                                                        } finally {
                                                            isUploading = false
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                } else {
                                    Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-1).dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text(
                                text = username,
                                fontSize = 28.sp, // Bigger name
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            if (AppState.currentUser.value?.email != null) {
                                Text(
                                    text = AppState.currentUser.value?.email ?: "",
                                    fontSize = 16.sp, // Bigger email
                                    color = TextSecond
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            // Edit Profile Button
                            Button(
                                onClick = { showEditProfile = true },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B8CD1)), // Darker blue
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("Edit Profile ✏️", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // ── Menu Section ──────────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Notifications Menu Button
                Surface(
                    onClick = onNavigateToNotifications,
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔔", fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Notifications",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text("❯", color = TextSecond, fontSize = 16.sp)
                    }
                }
                
            }
        }
    } // End of LazyColumn
    
    // Sign Out Button pinned to the bottom right
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .padding(bottom = 60.dp), // extra padding for bottom nav if needed
        contentAlignment = Alignment.BottomEnd
    ) {
        TextButton(
            onClick = {
                scope.launch {
                    try {
                        supabase.auth.signOut()
                        AppState.currentUser.value = null
                        AppState.friends.value = emptyList()
                        AppState.globalNotifications.value = emptyList()
                        AppState.totalPoints.value = 0
                    } catch (e: Exception) {
                        println("SIGN_OUT_ERROR: ${e.message}")
                    }
                }
            }
        ) {
            Text(
                text = "Sign Out",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF4B4B)
            )
        }
    }
} // End of Column

    if (showEditProfile) {
        ModalBottomSheet(
            onDismissRequest = { showEditProfile = false },
            containerColor = Color.White
        ) {
            EditProfileSheetContent(
                currentUsername = username,
                onDismiss = { showEditProfile = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheetContent(currentUsername: String, onDismiss: () -> Unit) {
    var newUsername by remember { mutableStateOf(currentUsername) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val userId = AppState.currentUser.value?.id

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Edit Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(
            value = newUsername,
            onValueChange = { newUsername = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (newUsername.isNotBlank() && userId != null) {
                    scope.launch {
                        isSaving = true
                        try {
                            supabase.postgrest["profiles"]
                                .update({
                                    set("username", newUsername)
                                    set("display_name", newUsername)
                                }) {
                                    filter { eq("id", userId) }
                                }
                            
                            // Refresh local AppState
                            val updatedProfile = AppState.friends.value.find { it.id == userId }?.copy(username = newUsername, displayName = newUsername)
                            if (updatedProfile != null) {
                                val newList = AppState.friends.value.toMutableList()
                                val idx = newList.indexOfFirst { it.id == userId }
                                if (idx != -1) newList[idx] = updatedProfile
                                AppState.friends.value = newList
                            }
                            onDismiss()
                        } catch (e: Exception) {
                            println("UPDATE_USERNAME_ERROR: ${e.message}")
                        } finally {
                            isSaving = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
            enabled = !isSaving
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
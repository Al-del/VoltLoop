package com.example.voltloop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

private val BlueAccent  = Color(0xFF43BBF7)
private val BlueSoft    = Color(0xFFE8F6FD)
private val AmberAccent = Color(0xFFFFB830)
private val AmberSoft   = Color(0xFFFFF3E0)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecond  = Color(0xFF888888)
private val CardBg      = Color(0xFFFFFFFF)
private val PageBg      = Color(0xFFF4F6F8)

data class BoosterItem(
    val name: String,
    val description: String,
    val multiplier: Int,
    val cost: Long,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(onBackClick: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var userProfile by remember { mutableStateOf<Profile?>(null) }
    val totalPoints by AppState.totalPoints
    
    val boosters = listOf(
        BoosterItem("x2 Multiplier", "Double your points on every trip!", 2, 500, Icons.Default.Bolt),
        BoosterItem("x3 Multiplier", "Triple your points on every trip!", 3, 1200, Icons.Default.Star)
    )

    LaunchedEffect(Unit) {
        val user = supabase.auth.currentUserOrNull()
        if (user != null) {
            try {
                userProfile = supabase.postgrest["profiles"]
                    .select { filter { eq("id", user.id) } }
                    .decodeSingle<Profile>()
            } catch (e: Exception) {
                println("Error fetching profile: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        // ── Header ─────────────────────────────────────────────
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
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "VoltStore",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BlueAccent
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Boost your earnings with multipliers",
                    fontSize = 14.sp,
                    color = TextSecond
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Points Display Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Your Balance", color = TextSecond, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, null, tint = AmberAccent, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("$totalPoints", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                                Text(" pts", color = TextSecond, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                        
                        val multiplier = AppState.currentMultiplier.value
                        if (multiplier > 1) {
                            Surface(
                                color = AmberSoft,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "x$multiplier Active",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = AmberAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "AVAILABLE BOOSTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextSecond,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(boosters) { booster ->
                val currentMult = AppState.currentMultiplier.value
                val isOwned = currentMult >= booster.multiplier
                val canAfford = totalPoints >= booster.cost
                
                BoosterCard(
                    booster = booster, 
                    isOwned = isOwned,
                    enabled = canAfford && !isOwned
                ) {
                    scope.launch {
                        try {
                            val user = supabase.auth.currentUserOrNull() ?: return@launch
                            val profile = supabase.postgrest["profiles"]
                                .select { filter { eq("id", user.id) } }
                                .decodeSingle<Profile>()
                            
                            if (profile.points >= booster.cost) {
                                val updatedPoints = profile.points - booster.cost
                                supabase.postgrest["profiles"].update({
                                    set("points", updatedPoints)
                                    set("points_multiplier", booster.multiplier)
                                }) {
                                    filter { eq("id", user.id) }
                                }
                                
                                // Update local state
                                AppState.totalPoints.value = updatedPoints.toInt()
                                AppState.currentMultiplier.value = booster.multiplier
                            }
                        } catch (e: Exception) {
                            println("Purchase error: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoosterCard(booster: BoosterItem, isOwned: Boolean, enabled: Boolean, onPurchase: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = booster.icon, 
                    contentDescription = null, 
                    tint = BlueAccent,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(booster.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                Text(booster.description, fontSize = 13.sp, color = TextSecond)
            }
            
            if (isOwned) {
                Text(
                    "Active",
                    color = Color(0xFF22C55E),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            } else {
                Button(
                    onClick = onPurchase,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlueAccent,
                        disabledContainerColor = Color(0xFFF0F0F0)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "${booster.cost} Pts", 
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) Color.White else TextSecond
                    )
                }
            }
        }
    }
}
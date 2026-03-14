package com.example.voltloop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

private val GreenDark = Color(0xFF14532D)
private val GreenBright = Color(0xFF15803D)
private val GreenLight = Color(0xFF86EFAC)

data class BoosterItem(
    val name: String,
    val description: String,
    val multiplier: Int,
    val cost: Long,
    val icon: ImageVector
)

@Composable
fun StoreScreen() {
    val scope = rememberCoroutineScope()
    var userProfile by remember { mutableStateOf<Profile?>(null) }
    
    val boosters = listOf(
        BoosterItem("x2 Multiplier", "Double your points on every trip!", 2, 500, Icons.Default.ElectricBolt),
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
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp)
    ) {
        Text(
            "VoltStore",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = GreenDark,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Points Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = GreenDark)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Your Points", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Text("${userProfile?.points ?: 0}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                if ((userProfile?.pointsMultiplier ?: 1) > 1) {
                    Surface(
                        color = Color.Yellow,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "x${userProfile?.pointsMultiplier} Active",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Text(
            "Boosters",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = GreenDark,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(boosters) { booster ->
                val canAfford = (userProfile?.points ?: 0) >= booster.cost
                BoosterCard(booster, canAfford) {
                    scope.launch {
                        try {
                            userProfile?.let { profile ->
                                if (profile.points >= booster.cost) {
                                    val updated = profile.copy(
                                        points = profile.points - booster.cost,
                                        pointsMultiplier = booster.multiplier
                                    )
                                    supabase.postgrest["profiles"].update(updated) {
                                        filter { eq("id", profile.id) }
                                    }
                                    userProfile = updated
                                }
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
fun BoosterCard(booster: BoosterItem, enabled: Boolean, onPurchase: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(GreenLight.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(booster.icon, null, tint = GreenBright)
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(booster.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(booster.description, fontSize = 12.sp, color = Color.Gray)
            }
            
            Button(
                onClick = onPurchase,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenBright,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("${booster.cost} Pts", fontWeight = FontWeight.Bold)
            }
        }
    }
}

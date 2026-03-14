package com.example.voltloop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GreenDark = Color(0xFF14532D)
private val GreenBright = Color(0xFF15803D)
private val GreenAccent = Color(0xFF4ADE80)

@Composable
fun Start_Trip() {
    var isTripActive by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isTripActive) "Trip in Progress" else "Ready to Ride?",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = GreenDark
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = if (isTripActive) "Enjoy your journey" else "Tap below to begin",
                color = Color.Gray,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(48.dp))

            // Large Start/Stop Button
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(
                        Brush.radialGradient(
                            colors = if (isTripActive) listOf(Color.Red.copy(alpha = 0.8f), Color.Red)
                                     else listOf(GreenAccent, GreenBright)
                        ),
                        shape = CircleShape
                    )
                    .padding(4.dp)
            ) {
                Button(
                    onClick = {
                        isTripActive = !isTripActive
                    },
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Icon(
                        imageVector = if (isTripActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

package com.example.voltloop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()

    Scaffold(

    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Settings", modifier = Modifier.padding(bottom = 16.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        try {
                            supabase.auth.signOut()
                        } catch (e: Exception) {
                            // Handle potential error during sign out
                        }
                    }
                }
            ) {
                Text("Sign Out")
            }
        }
    }
}

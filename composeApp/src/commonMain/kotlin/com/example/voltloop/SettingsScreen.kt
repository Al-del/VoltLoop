package com.example.voltloop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isUpdating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }

    // Fetch current user data
    LaunchedEffect(Unit) {
        val user = supabase.auth.currentUserOrNull()
        currentUserId = user?.id
        email = user?.email ?: ""
        if (currentUserId != null) {
            try {
                val profile = supabase.postgrest["profiles"]
                    .select { filter { eq("id", currentUserId!!) } }
                    .decodeSingle<Profile>()
                username = profile.username ?: ""
            } catch (e: Exception) {
                println("Error fetching profile: ${e.message}")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = false // Email is read-only
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isUpdating
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            if (message != null) {
                Text(message!!, color = if (message!!.contains("Error")) Color.Red else Color(0xFF2E7D32))
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (username.isBlank()) return@Button
                    isUpdating = true
                    message = null
                    scope.launch {
                        try {
                            supabase.postgrest["profiles"].update({
                                set("username", username)
                                set("display_name", username)
                            }) {
                                filter { eq("id", currentUserId!!) }
                            }
                            message = "Username updated successfully!"
                        } catch (e: Exception) {
                            message = "Error: ${e.message}"
                        } finally {
                            isUpdating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUpdating && username.isNotBlank()
            ) {
                if (isUpdating) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Update Username")
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        try {
                            supabase.auth.signOut()
                        } catch (e: Exception) {
                            // Handle potential error during sign out
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Sign Out")
            }
        }
    }
}

package com.example.voltloop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("VoltLoop", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        // Username Field (Only visible during Sign Up)
        AnimatedVisibility(visible = isSignUp) {
            Column {
                TextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        if (isSignUp) {
                            if (username.isBlank()) {
                                errorMessage = "Username cannot be empty"
                                return@launch
                            }
                            supabase.auth.signUpWith(Email) {
                                this.email = email
                                this.password = password
                                // Pass username to user_metadata
                                data = buildJsonObject {
                                    put("username", username)
                                    put("display_name", username)
                                }
                            }
                            // Auto-login after signup
                            supabase.auth.signInWith(Email) {
                                this.email = email
                                this.password = password
                            }
                        } else {
                            supabase.auth.signInWith(Email) {
                                this.email = email
                                this.password = password
                            }
                        }
                        onLoginSuccess()
                    } catch (e: Exception) {
                        errorMessage = e.message ?: if (isSignUp) "Sign up failed" else "Login failed"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text(if (isSignUp) "Create Account" else "Login")
            }
        }

        TextButton(
            onClick = { 
                isSignUp = !isSignUp
                errorMessage = null
            },
            enabled = !isLoading
        ) {
            Text(if (isSignUp) "Already have an account? Login" else "Don't have an account? Sign Up")
        }
    }
}

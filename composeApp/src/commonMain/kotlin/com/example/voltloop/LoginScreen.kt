package com.example.voltloop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.painterResource
import voltloop.composeapp.generated.resources.Res
import voltloop.composeapp.generated.resources.google_logo

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

    val voltLoopBlue = Color(0xFF43BBF7)
    val inputBackground = Color(0xFFF5F5F5)
    val buttonDark = Color(0xFF0F141A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "VoltLoop",
            color = voltLoopBlue,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(visible = isSignUp) {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = { Text("Username", color = Color.Gray.copy(alpha=0.7f), fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = inputBackground,
                        unfocusedContainerColor = inputBackground,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = { Text("Email/Username", color = Color.Gray.copy(alpha=0.7f), fontWeight = FontWeight.SemiBold) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = inputBackground,
                unfocusedContainerColor = inputBackground,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("Password", color = Color.Gray.copy(alpha=0.7f), fontWeight = FontWeight.SemiBold) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = inputBackground,
                unfocusedContainerColor = inputBackground,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
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
                                data = buildJsonObject {
                                    put("username", username)
                                    put("display_name", username)
                                }
                            }
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

                        AppState.currentUser.value = supabase.auth.currentSessionOrNull()?.user
                        println("🔒 Locker locked by: $username")

                        onLoginSuccess()
                    } catch (e: Exception) {
                        errorMessage = e.message ?: if (isSignUp) "Sign up failed" else "Login failed"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = voltLoopBlue),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(if (isSignUp) "Create Account" else "Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    try {
                        supabase.auth.signInWith(Google)
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Google Sign-In failed"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonDark),
            enabled = !isLoading,
            contentPadding = PaddingValues()
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sign in with Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Image(
                    painter = painterResource(Res.drawable.google_logo),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isSignUp = !isSignUp
                    errorMessage = null
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSignUp) "Already have an account? " else "Don't have an account? ",
                color = Color.DarkGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isSignUp) "Login" else "Sign Up",
                color = voltLoopBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

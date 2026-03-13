package com.example.voltloop.NetworkStuff

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class User(val name: String, val email: String)

@Serializable
data class ApiResponse(val message: String)

const val BASE_URL = "https://api.instasight.click"  // ← your Flask server IP

// GET request
suspend fun getUsers(): List<User> =
    httpClient.get("$BASE_URL/users").body<List<User>>()
// POST request
suspend fun createUser(user: User): ApiResponse =
    httpClient.post("$BASE_URL/users") {
        contentType(ContentType.Application.Json)
        setBody(user)
    }.body()
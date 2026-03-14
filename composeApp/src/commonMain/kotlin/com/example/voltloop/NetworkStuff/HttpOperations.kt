package com.example.voltloop.NetworkStuff

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class User(val name: String, val email: String)

@Serializable
data class ApiResponse(val message: String)

@Serializable
data class EventResponse(val event: String)

const val BASE_URL = "https://api.instasight.click"

// GET /users
suspend fun getUsers(): List<User> =
    httpClient.get("$BASE_URL/users").body<List<User>>()

// POST /users
suspend fun createUser(user: User): ApiResponse =
    httpClient.post("$BASE_URL/users") {
        contentType(ContentType.Application.Json)
        setBody(user)
    }.body()

suspend fun getEvent(): EventResponse =
    httpClient.get("$BASE_URL/get_event").body<EventResponse>()

@Serializable
data class ProofResponse(
    val status: String,
    val text: String,
    val similarity: Float,
    val accepted: Boolean
)
suspend fun submitProof(base64Image: String, text: String): ProofResponse {
    val response = httpClient.post("$BASE_URL/submit_proof") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("image" to base64Image, "text" to text))
    }
    println("RAW_RESPONSE: ${response.body<String>()}")  // <-- add this
    return response.body()
}
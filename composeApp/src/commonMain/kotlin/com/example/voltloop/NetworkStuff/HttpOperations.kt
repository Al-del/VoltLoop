package com.example.voltloop.NetworkStuff
import com.example.voltloop.AppState
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive
@Serializable
data class User(val name: String, val email: String)

@Serializable
data class ApiResponse(val message: String)

@Serializable
data class EventResponse(val event: String)

@Serializable
data class LockResponse(val success: Boolean, val status: Int? = null, val error: String? = null)

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

suspend fun lockLocker(): LockResponse {
    val email = AppState.currentUser.value?.email ?: "unknown"
    println("Locker user $email")
    return httpClient.get("$BASE_URL/lock") {
        parameter("email", email)
    }.body<LockResponse>()
        .also { println("🔒 Locker locked by: $email") }
}
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
    println("RAW_RESPONSE: ${response.body<String>()}")
    return response.body()
}
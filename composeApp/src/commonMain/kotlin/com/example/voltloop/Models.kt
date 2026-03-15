package com.example.voltloop

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class BatteryLocation(
    val id: String? = null,
    val latitude: Double,
    val longitude: Double,
    val name: String = "Battery Station",
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("created_by")
    val createdBy: String? = null
)

@Serializable
data class Profile(
    val id: String,
    val email: String? = null,
    val username: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val points: Long = 0,
    @SerialName("points_multiplier")
    val pointsMultiplier: Int = 1
)

@Serializable
data class Friendship(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("friend_id")
    val friendId: String,
    val status: String = "pending",
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class Message(
    val id: String? = null,
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("receiver_id")
    val receiverId: String,
    val content: String,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class UserLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    // Helper field for mapping, not in the DB table directly
    val username: String? = null
)

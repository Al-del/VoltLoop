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

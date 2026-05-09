package com.keith.modi.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    CUSTOMER, HOST
}

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val role: String = "CUSTOMER",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_kyc_verified") val isKycVerified: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class Property(
    val id: String? = null,
    @SerialName("host_id") val hostId: String,
    val title: String,
    val description: String? = null,
    @SerialName("price_per_night") val price: Double,
    @SerialName("location_name") val locationName: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("images") val imageUrls: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    
    // UI-only or extended fields
    val rating: Double = 4.5,
    @SerialName("distance_km") val distanceKm: Double = 0.0,
    val category: String = "Nearby",
    val tags: List<String> = emptyList(),
    @SerialName("is_liked") val isLiked: Boolean = false
)

@Serializable
data class Booking(
    val id: String,
    @SerialName("property_id") val propertyId: String,
    @SerialName("guest_id") val guestId: String,
    val status: String, // PENDING, CONFIRMED, CANCELLED
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("fee_paid") val feePaid: Double
)

@Serializable
data class Favorite(
    @SerialName("user_id") val userId: String,
    @SerialName("property_id") val propertyId: String,
    @SerialName("created_at") val createdAt: String? = null
)

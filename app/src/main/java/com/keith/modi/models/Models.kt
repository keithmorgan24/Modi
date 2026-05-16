package com.keith.modi.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

@Serializable
data class LatLng(val latitude: Double, val longitude: Double)

@Serializable
enum class UserRole {
    CUSTOMER, HOST
}

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    val role: String = "CUSTOMER",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_kyc_verified") val isKycVerified: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class Category(
    val id: Int? = null,
    val name: String,
    val icon: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = true
)

@Serializable
data class Property(
    val id: String? = null,
    @SerialName("host_id") val hostId: String = "",
    val title: String = "Premium Space",
    val description: String? = null,
    @SerialName("price_per_night") val price: Double = 0.0,
    @SerialName("location_name") val locationName: String = "Nairobi, Kenya",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("images") val imageUrls: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("total_rooms") val totalRooms: Int = 1,
    @SerialName("occupied_rooms") val occupiedRooms: Int = 0,
    val category: String = "Nearby",
    val tags: List<String> = emptyList(),
    
    // PENDO: These fields remain transient/calculated
    @kotlinx.serialization.Transient val rating: Double = 4.5,
    @kotlinx.serialization.Transient val distanceKm: Double = 0.0,
    @kotlinx.serialization.Transient val isLiked: Boolean = false
) {
    val isFull: Boolean get() = occupiedRooms >= totalRooms
    val vacantRooms: Int get() = (totalRooms - occupiedRooms).coerceAtLeast(0)

    /**
     * PENDO: Generates a fuzzy location within a 500m radius of the actual location.
     * Protects host privacy for unbooked properties while maintaining UI context.
     */
    fun getFuzzyLocation(): LatLng {
        val lat = latitude ?: 0.0
        val lon = longitude ?: 0.0
        val random = Random(id.hashCode())
        val radiusInDegrees = 500.0 / 111320.0 // Approx 500m in degrees
        
        val u = random.nextDouble()
        val v = random.nextDouble()
        val w = radiusInDegrees * kotlin.math.sqrt(u)
        val t = 2 * kotlin.math.PI * v
        val x = w * kotlin.math.cos(t)
        val y = w * kotlin.math.sin(t)
        
        // Adjust y for latitude shrinkage
        val newX = x / kotlin.math.cos(Math.toRadians(lat))
        
        return LatLng(lat + y, lon + newX)
    }
}

@Serializable
data class Booking(
    val id: String? = null,
    @SerialName("property_id") val propertyId: String = "",
    @SerialName("guest_id") val guestId: String = "",
    val status: String = "PENDING",
    @SerialName("expires_at") val expiresAt: String = "",
    @SerialName("fee_paid") val feePaid: Double = 0.0,
    @SerialName("properties") val property: Property? = null,
    @SerialName("profiles") val guestProfile: Profile? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Favorite(
    @SerialName("user_id") val userId: String,
    @SerialName("property_id") val propertyId: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Review(
    val id: String? = null,
    @SerialName("booking_id") val bookingId: String,
    @SerialName("user_id") val userId: String,
    val rating: Int,
    val comment: String,
    val photos: List<String> = emptyList(),
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("property_id") val propertyId: String? = null
)

@Serializable
data class Notification(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val title: String,
    val message: String,
    val type: String = "ACTIVITY",
    @SerialName("is_read") val isRead: Boolean = false,
    val metadata: JsonObject? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AppRelease(
    val id: String? = null,
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("apk_path") val apkPath: String,
    @SerialName("release_notes") val releaseNotes: String? = null,
    @SerialName("is_critical") val isCritical: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

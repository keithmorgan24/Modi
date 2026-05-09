package com.keith.modi.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keith.modi.Supabase
import com.keith.modi.utils.ErrorUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class PropertyState {
    object Loading : PropertyState()
    data class Success(
        val properties: List<Property>,
        val favorites: List<String> = emptyList(),
        val activeBooking: Booking? = null,
        val userBookings: List<Booking> = emptyList(),
        val reviews: Map<String, List<Review>> = emptyMap() // Map of propertyId to its reviews
    ) : PropertyState()
    data class Error(val message: String) : PropertyState()
}

class PropertyViewModel : ViewModel() {
    private val _propertyState = MutableStateFlow<PropertyState>(PropertyState.Loading)
    val propertyState: StateFlow<PropertyState> = _propertyState.asStateFlow()

    init {
        fetchProperties()
    }

    fun fetchProperties() {
        viewModelScope.launch {
            _propertyState.value = PropertyState.Loading
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id
                
                // Fetch properties, favorites, and bookings in parallel
                val propertiesDeferred = async {
                    Supabase.client.postgrest["properties"]
                        .select(columns = Columns.ALL)
                        .decodeList<Property>()
                }

                val favoritesDeferred = async {
                    if (userId != null) {
                        try {
                            Supabase.client.postgrest["favorites"]
                                .select(columns = Columns.list("property_id", "user_id")) {
                                    filter { eq("user_id", userId) }
                                }
                                .decodeList<Favorite>()
                        } catch (e: Exception) {
                            emptyList<Favorite>()
                        }
                    } else {
                        emptyList<Favorite>()
                    }
                }

                val bookingsDeferred = async {
                    if (userId != null) {
                        try {
                            Supabase.client.postgrest["bookings"]
                                .select(columns = Columns.raw("*, properties(*)")) {
                                    filter { eq("guest_id", userId) }
                                }
                                .decodeList<Booking>()
                        } catch (e: Exception) {
                            emptyList<Booking>()
                        }
                    } else {
                        emptyList<Booking>()
                    }
                }

                val reviewsDeferred = async {
                    try {
                        Supabase.client.postgrest["reviews"]
                            .select()
                            .decodeList<Review>()
                    } catch (e: Exception) {
                        emptyList<Review>()
                    }
                }

                val properties = propertiesDeferred.await()
                val favorites = favoritesDeferred.await()
                val bookings = bookingsDeferred.await()
                val allReviews = reviewsDeferred.await()

                val reviewsByProperty = allReviews.groupBy { review ->
                    review.propertyId ?: ""
                }.filterKeys { it.isNotEmpty() }

                val updatedProperties = properties.map { property ->
                    property.copy(isLiked = favorites.any { it.propertyId == property.id })
                }

                _propertyState.value = PropertyState.Success(
                    properties = updatedProperties, 
                    favorites = favorites.map { it.propertyId },
                    userBookings = bookings,
                    reviews = reviewsByProperty
                )
            } catch (e: Exception) {
                _propertyState.value = PropertyState.Error(ErrorUtils.sanitizeError(e))
                
                delay(2000)
                val mockProperties = listOf(
                    Property(
                        id = "1",
                        hostId = "h1",
                        title = "Ocean Breeze Villa",
                        description = "Beautiful beachfront villa with private pool and chef.",
                        price = 15000.0,
                        locationName = "Diani, Kenya",
                        latitude = -4.2,
                        longitude = 39.5,
                        imageUrls = listOf("https://images.unsplash.com/photo-1499793983690-e29da59ef1c2"),
                        rating = 4.8,
                        distanceKm = 2.5,
                        tags = listOf("Beachfront", "Pool", "Luxury")
                    ),
                    Property(
                        id = "2",
                        hostId = "h2",
                        title = "City Center Studio",
                        description = "Modern studio in the heart of Nairobi. Fast Wi-Fi.",
                        price = 5000.0,
                        locationName = "Nairobi, Kenya",
                        latitude = -1.2,
                        longitude = 36.8,
                        imageUrls = listOf("https://images.unsplash.com/photo-1502672260266-1c1ef2d93688"),
                        distanceKm = 0.8,
                        tags = listOf("Modern", "WiFi", "Central")
                    )
                )
                if (_propertyState.value is PropertyState.Error) {
                    _propertyState.value = PropertyState.Success(mockProperties)
                }
            }
        }
    }

    fun toggleLike(propertyId: String) {
        viewModelScope.launch {
            val currentState = _propertyState.value
            if (currentState is PropertyState.Success) {
                val property = currentState.properties.find { it.id == propertyId } ?: return@launch
                val isCurrentlyLiked = property.isLiked
                
                // 1. Optimistic UI update
                val updatedList = currentState.properties.map {
                    if (it.id == propertyId) it.copy(isLiked = !isCurrentlyLiked) else it
                }
                _propertyState.value = currentState.copy(properties = updatedList)

                try {
                    val userId = Supabase.client.auth.currentUserOrNull()?.id
                    if (userId != null) {
                        if (isCurrentlyLiked) {
                            Supabase.client.postgrest["favorites"].delete {
                                filter {
                                    eq("user_id", userId)
                                    eq("property_id", propertyId)
                                }
                            }
                        } else {
                            Supabase.client.postgrest["favorites"].insert(
                                Favorite(
                                    userId,
                                    propertyId
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    _propertyState.value = currentState
                }
            }
        }
    }

    private var lockTimerJob: Job? = null

    fun createPendingBooking(property: Property) {
        val propertyId = property.id ?: return
        viewModelScope.launch {
            val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return@launch
            val currentState = _propertyState.value
            if (currentState is PropertyState.Success) {
                try {
                    // Compatible timestamp for minSdk 24
                    val expiryTime = System.currentTimeMillis() + (5 * 60 * 1000)
                    val expiresAt = expiryTime.toString() 
                    
                    val bookingId = UUID.randomUUID().toString()
                    val newBooking = Booking(
                        id = bookingId,
                        propertyId = propertyId,
                        guestId = userId,
                        status = "PENDING",
                        expiresAt = expiresAt,
                        feePaid = property.price * 0.1
                    )

                    // Sync with Supabase (Soft-Lock trigger)
                    Supabase.client.postgrest["bookings"].insert(newBooking)
                    
                    _propertyState.value = currentState.copy(activeBooking = newBooking)

                    // Auto-cancel lock if it expires
                    lockTimerJob?.cancel()
                    lockTimerJob = launch {
                        delay(300_000) // 5 minutes
                        cancelBooking(bookingId)
                    }
                } catch (e: Exception) {
                    // Log error: Failed to establish soft-lock
                }
            }
        }
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            val currentState = _propertyState.value
            if (currentState is PropertyState.Success && currentState.activeBooking?.id == bookingId) {
                try {
                    Supabase.client.postgrest["bookings"].update({
                        set("status", "CANCELLED")
                    }) {
                        filter { eq("id", bookingId) }
                    }
                    _propertyState.value = currentState.copy(activeBooking = null)
                    lockTimerJob?.cancel()
                } catch (e: Exception) {
                    // Log error
                }
            }
        }
    }
}

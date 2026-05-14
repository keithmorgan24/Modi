package com.keith.modi.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keith.modi.Supabase
import com.keith.modi.utils.ErrorUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.UUID

import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.collectLatest

sealed class PropertyState {
    object Loading : PropertyState()
    data class Success(
        val properties: List<Property>,
        val favorites: List<String> = emptyList(),
        val activeBooking: Booking? = null,
        val userBookings: List<Booking> = emptyList(),
        val reviews: Map<String, List<Review>> = emptyMap(),
        val categories: List<Category> = emptyList(),
        val isBookingLoading: Boolean = false,
        val bookingError: String? = null
    ) : PropertyState()
    data class Error(val message: String) : PropertyState()
}

class PropertyViewModel : ViewModel() {
    private val _propertyState = MutableStateFlow<PropertyState>(PropertyState.Loading)
    val propertyState: StateFlow<PropertyState> = _propertyState.asStateFlow()

    init {
        fetchProperties()
        observeSessionForRealtime()
    }

    private fun observeSessionForRealtime() {
        viewModelScope.launch {
            Supabase.client.auth.sessionStatus.collectLatest { status ->
                if (status is SessionStatus.Authenticated) {
                    setupRealtime()
                }
            }
        }
    }

    private fun setupRealtime() {
        viewModelScope.launch {
            try {
                Supabase.client.realtime.connect()
                val channel = Supabase.client.channel("properties-live")
                val propertyFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "properties" }
                propertyFlow.onEach { action ->
                    try {
                        val currentState = _propertyState.value
                        if (currentState is PropertyState.Success) {
                            when (action) {
                                is PostgresAction.Insert -> {
                                    val newProperty = action.decodeRecord<Property>()
                                    val updatedList = (listOf(newProperty) + currentState.properties).distinctBy { it.id }
                                    _propertyState.value = currentState.copy(properties = updatedList)
                                }
                                is PostgresAction.Update -> {
                                    val updatedProperty = action.decodeRecord<Property>()
                                    val updatedList = currentState.properties.map { if (it.id == updatedProperty.id) updatedProperty.copy(isLiked = it.isLiked) else it }
                                    _propertyState.value = currentState.copy(properties = updatedList)
                                }
                                is PostgresAction.Delete -> {
                                    val deletedId = action.oldRecord["id"]?.jsonPrimitive?.contentOrNull
                                    if (deletedId != null) {
                                        val updatedList = currentState.properties.filter { it.id != deletedId }
                                        _propertyState.value = currentState.copy(properties = updatedList)
                                    }
                                }
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {}
                }.launchIn(viewModelScope)
                channel.subscribe()
            } catch (e: Exception) {}
        }
    }

    fun fetchProperties() {
        viewModelScope.launch {
            _propertyState.value = PropertyState.Loading
            try {
                // PENDO: Using supervisorScope prevents one failed child from cancelling the whole fetch
                supervisorScope {
                    val userId = Supabase.client.auth.currentUserOrNull()?.id
                    
                    val propertiesDeferred = async { 
                        Supabase.client.postgrest["properties"].select().decodeList<Property>() 
                    }
                    
                    val favoritesDeferred = async {
                        try {
                            if (userId != null) Supabase.client.postgrest["favorites"].select { filter { eq("user_id", userId) } }.decodeList<Favorite>()
                            else emptyList<Favorite>()
                        } catch (e: Exception) { emptyList<Favorite>() }
                    }
                    
                    val bookingsDeferred = async {
                        try {
                            if (userId != null) Supabase.client.postgrest["bookings"].select(columns = Columns.raw("*, properties(*)")) { filter { eq("guest_id", userId) } }.decodeList<Booking>()
                            else emptyList<Booking>()
                        } catch (e: Exception) { emptyList<Booking>() }
                    }
                    
                    val reviewsDeferred = async {
                        try {
                            Supabase.client.postgrest["reviews"].select().decodeList<Review>()
                        } catch (e: Exception) { emptyList<Review>() }
                    }

                    val categoriesDeferred = async {
                        try {
                            Supabase.client.postgrest["categories"].select().decodeList<Category>()
                        } catch (e: Exception) {
                            listOf(
                                Category(name = "Nearby"),
                                Category(name = "Beachfront"),
                                Category(name = "Pool"),
                                Category(name = "Luxury"),
                                Category(name = "Modern"),
                                Category(name = "WiFi"),
                                Category(name = "Central"),
                                Category(name = "Cabins")
                            )
                        }
                    }

                    // Await all results with fallback
                    val properties = propertiesDeferred.await()
                    val favorites = favoritesDeferred.await()
                    val bookings = bookingsDeferred.await()
                    val allReviews = reviewsDeferred.await()
                    val categories = categoriesDeferred.await()

                    val reviewsByProperty = allReviews.groupBy { it.propertyId ?: "" }.filterKeys { it.isNotEmpty() }
                    val updatedProperties = properties.map { p -> p.copy(isLiked = favorites.any { it.propertyId == p.id }) }

                    _propertyState.value = PropertyState.Success(
                        properties = updatedProperties, 
                        favorites = favorites.map { it.propertyId }, 
                        userBookings = bookings, 
                        reviews = reviewsByProperty,
                        categories = categories
                    )
                }
            } catch (e: Exception) {
                // ErrorUtils will now rethrow CancellationException automatically
                _propertyState.value = PropertyState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun toggleLike(propertyId: String) {
        viewModelScope.launch {
            val currentState = _propertyState.value
            if (currentState is PropertyState.Success) {
                val property = currentState.properties.find { it.id == propertyId } ?: return@launch
                val isCurrentlyLiked = property.isLiked
                val updatedList = currentState.properties.map { if (it.id == propertyId) it.copy(isLiked = !isCurrentlyLiked) else it }
                _propertyState.value = currentState.copy(properties = updatedList)
                try {
                    val userId = Supabase.client.auth.currentUserOrNull()?.id
                    if (userId != null) {
                        if (isCurrentlyLiked) Supabase.client.postgrest["favorites"].delete { filter { eq("user_id", userId); eq("property_id", propertyId) } }
                        else Supabase.client.postgrest["favorites"].insert(Favorite(userId, propertyId))
                    }
                } catch (e: Exception) { _propertyState.value = currentState }
            }
        }
    }

    fun createPendingBooking(property: Property) {
        val propertyId = property.id ?: return
        viewModelScope.launch {
            val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return@launch
            val currentState = _propertyState.value
            if (currentState is PropertyState.Success) {
                _propertyState.value = currentState.copy(isBookingLoading = true, bookingError = null)
                try {
                    val bookingId = UUID.randomUUID().toString()
                    val expiresAtStr = (System.currentTimeMillis() + 3600000).toString()
                    
                    // PENDO: Data Integrity - Use buildJsonObject to avoid 'Any' serialization issues
                    val bookingJson = buildJsonObject {
                        put("id", bookingId)
                        put("property_id", propertyId)
                        put("guest_id", userId)
                        put("status", "PENDING")
                        put("expires_at", expiresAtStr)
                        put("fee_paid", 0.0)
                    }

                    Supabase.client.postgrest["bookings"].insert(bookingJson)
                    
                    val newBooking = Booking(
                        id = bookingId,
                        propertyId = propertyId,
                        guestId = userId,
                        status = "PENDING",
                        expiresAt = expiresAtStr,
                        feePaid = 0.0
                    )
                    _propertyState.value = currentState.copy(activeBooking = newBooking, isBookingLoading = false)
                } catch (e: Exception) {
                    _propertyState.value = currentState.copy(isBookingLoading = false, bookingError = ErrorUtils.sanitizeError(e))
                }
            }
        }
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            val currentState = _propertyState.value
            if (currentState is PropertyState.Success) {
                try {
                    Supabase.client.postgrest["bookings"].delete { filter { eq("id", bookingId) } }
                    _propertyState.value = currentState.copy(activeBooking = null)
                } catch (e: Exception) {}
            }
        }
    }

    fun confirmBooking(bookingId: String) {
        viewModelScope.launch {
            val currentState = _propertyState.value
            if (currentState is PropertyState.Success) {
                try {
                    Supabase.client.postgrest["bookings"].update({ set("status", "CONFIRMED") }) { filter { eq("id", bookingId) } }
                    fetchProperties() // Refresh all data
                } catch (e: Exception) {}
            }
        }
    }
}

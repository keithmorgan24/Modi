package com.keith.modi.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keith.modi.Supabase
import com.keith.modi.utils.ErrorUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.*
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
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
import java.util.UUID

sealed class PropertyState {
    object Loading : PropertyState()
    data class Success(
        val properties: List<Property>,
        val favorites: List<String> = emptyList(),
        val activeBooking: Booking? = null,
        val userBookings: List<Booking> = emptyList(),
        val reviews: Map<String, List<Review>> = emptyMap(),
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
        setupRealtime()
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
                val userId = Supabase.client.auth.currentUserOrNull()?.id
                val propertiesDeferred = async { Supabase.client.postgrest["properties"].select().decodeList<Property>() }
                val favoritesDeferred = async {
                    if (userId != null) Supabase.client.postgrest["favorites"].select { filter { eq("user_id", userId) } }.decodeList<Favorite>()
                    else emptyList<Favorite>()
                }
                val bookingsDeferred = async {
                    if (userId != null) Supabase.client.postgrest["bookings"].select(columns = Columns.raw("*, properties(*)")) { filter { eq("guest_id", userId) } }.decodeList<Booking>()
                    else emptyList<Booking>()
                }
                val reviewsDeferred = async { Supabase.client.postgrest["reviews"].select().decodeList<Review>() }

                val properties = propertiesDeferred.await()
                val favorites = favoritesDeferred.await()
                val bookings = bookingsDeferred.await()
                val allReviews = reviewsDeferred.await()

                val reviewsByProperty = allReviews.groupBy { it.propertyId ?: "" }.filterKeys { it.isNotEmpty() }
                val updatedProperties = properties.map { p -> p.copy(isLiked = favorites.any { it.propertyId == p.id }) }

                _propertyState.value = PropertyState.Success(
                    properties = updatedProperties, 
                    favorites = favorites.map { it.propertyId }, 
                    userBookings = bookings, 
                    reviews = reviewsByProperty
                )
            } catch (e: Exception) {
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

    private var lockTimerJob: Job? = null

    fun createPendingBooking(property: Property) {
        val propertyId = property.id ?: return
        viewModelScope.launch {
            val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return@launch
            val currentState = _propertyState.value
            if (currentState is PropertyState.Success) {
                _propertyState.value = currentState.copy(isBookingLoading = true, bookingError = null)
                try {
                    val expiryTime = System.currentTimeMillis() + (5 * 60 * 1000)
                    val bookingId = UUID.randomUUID().toString()
                    val newBooking = Booking(bookingId, propertyId, userId, "PENDING", expiryTime.toString(), property.price * 0.1)
                    Supabase.client.postgrest["bookings"].insert(newBooking)
                    _propertyState.value = currentState.copy(activeBooking = newBooking, isBookingLoading = false)
                    lockTimerJob?.cancel()
                    lockTimerJob = launch { delay(300_000); cancelBooking(bookingId) }
                } catch (e: Exception) {
                    _propertyState.value = currentState.copy(isBookingLoading = false, bookingError = "Unable to lock room.")
                }
            }
        }
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            val currentState = _propertyState.value
            if (currentState is PropertyState.Success) {
                try {
                    Supabase.client.postgrest["bookings"].update({ set("status", "CANCELLED") }) { filter { eq("id", bookingId) } }
                    _propertyState.value = currentState.copy(activeBooking = null)
                    lockTimerJob?.cancel()
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
                    val updatedBookings = currentState.userBookings.map { if (it.id == bookingId) it.copy(status = "CONFIRMED") else it }
                    _propertyState.value = currentState.copy(activeBooking = null, userBookings = updatedBookings)
                    lockTimerJob?.cancel()
                } catch (e: Exception) {}
            }
        }
    }

    fun payWithMpesa(phoneNumber: String, amount: Double, bookingId: String, onTriggered: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val body = buildJsonObject {
                    put("phone", phoneNumber)
                    put("amount", amount.toInt())
                    put("booking_id", bookingId)
                }
                
                // PENDO: Robust STK Push trigger with explicit status handling
                val response = Supabase.client.functions.invoke("mpesa-stk-push", body)
                val responseText = response.bodyAsText()
                
                if (response.status.value >= 400) {
                    onTriggered(false, "Server Error (${response.status.value}). Ensure functions are deployed.")
                } else {
                    val json = Json.parseToJsonElement(responseText).jsonObject
                    if (json["ResponseCode"]?.jsonPrimitive?.content == "0") {
                        onTriggered(true, "Prompt Sent! Check your phone 📲")
                    } else {
                        val error = json["CustomerMessage"]?.jsonPrimitive?.content ?: "Safaricom rejected request"
                        onTriggered(false, error)
                    }
                }
            } catch (e: Exception) {
                onTriggered(false, ErrorUtils.sanitizeError(e))
            }
        }
    }
}

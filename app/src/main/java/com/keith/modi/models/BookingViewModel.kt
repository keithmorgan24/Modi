package com.keith.modi.models

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keith.modi.CloudinaryHelper
import com.keith.modi.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.collectLatest

sealed class BookingState {
    object Loading : BookingState()
    data class Success(val bookings: List<Booking>) : BookingState()
    data class Error(val message: String) : BookingState()
}

class BookingViewModel : ViewModel() {
    private val _bookingState = MutableStateFlow<BookingState>(BookingState.Loading)
    val bookingState: StateFlow<BookingState> = _bookingState.asStateFlow()

    init {
        fetchUserBookings()
        observeSessionForRealtime()
    }

    private fun observeSessionForRealtime() {
        viewModelScope.launch {
            Supabase.client.auth.sessionStatus.collectLatest { status ->
                if (status is SessionStatus.Authenticated) {
                    fetchUserBookings() 
                    val userId = status.session.user?.id
                    if (userId != null) {
                        setupRealtime(userId)
                    }
                }
            }
        }
    }

    private fun setupRealtime(userId: String) {
        viewModelScope.launch {
            try {
                val channel = Supabase.client.channel("bookings-live")
                val bookingFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "bookings"
                }

                bookingFlow.onEach { action ->
                    val currentState = _bookingState.value
                    if (currentState is BookingState.Success) {
                        when (action) {
                            is PostgresAction.Insert -> {
                                val newBooking = action.decodeRecord<Booking>()
                                if (newBooking.guestId == userId) {
                                    fetchUserBookings() 
                                }
                            }
                            is PostgresAction.Update -> {
                                val updatedBooking = action.decodeRecord<Booking>()
                                if (updatedBooking.guestId == userId) {
                                    val updatedList = currentState.bookings.map {
                                        if (it.id == updatedBooking.id) {
                                            updatedBooking.copy(property = it.property)
                                        } else it
                                    }
                                    _bookingState.value = BookingState.Success(updatedList)
                                }
                            }
                            else -> {}
                        }
                    }
                }.launchIn(viewModelScope)

                channel.subscribe()
            } catch (_: Exception) {}
        }
    }

    fun fetchUserBookings() {
        viewModelScope.launch {
            _bookingState.value = BookingState.Loading
            try {
                val user = Supabase.client.auth.currentUserOrNull()
                if (user != null) {
                    // PENDO: Data Accuracy Check - Enhanced with manual join fallback
                    val bookings = try {
                        Supabase.client.postgrest["bookings"]
                            .select(columns = Columns.raw("*, properties(*)")) {
                                filter { eq("guest_id", user.id) }
                            }
                            .decodeList<Booking>()
                    } catch (e: Exception) {
                        // Fallback to manual join to prevent "hardcoded" placeholders in UI
                        val simpleBookings = Supabase.client.postgrest["bookings"]
                            .select { filter { eq("guest_id", user.id) } }
                            .decodeList<Booking>()
                        
                        if (simpleBookings.isEmpty()) {
                            simpleBookings
                        } else {
                            val allProperties = Supabase.client.postgrest["properties"].select().decodeList<Property>()
                            simpleBookings.map { booking ->
                                booking.copy(property = allProperties.find { it.id == booking.propertyId })
                            }
                        }
                    }
                    _bookingState.value = BookingState.Success(bookings)
                } else {
                    _bookingState.value = BookingState.Error("Please sign in to view your trips.")
                }
            } catch (e: Exception) {
                // PENDO SECURITY: Mask tokens but provide technical context for debugging
                val rawMsg = e.message ?: "Unknown Connection Error"
                val cleanedMsg = rawMsg.replace(Regex("Bearer\\s+[a-zA-Z0-9\\-_\\.]+"), "[TOKEN MASKED]")
                _bookingState.value = BookingState.Error(cleanedMsg)
            }
        }
    }

    fun clearTripHistory() {
        viewModelScope.launch {
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return@launch
                // PENDO: Optimized Clear - Remove all records associated with user to ensure "it works"
                Supabase.client.postgrest["bookings"].delete {
                    filter {
                        eq("guest_id", userId)
                    }
                }
                fetchUserBookings()
            } catch (e: Exception) {
                println("[ERROR] Failed to clear trip history: ${e.message}")
            }
        }
    }

    fun submitReview(context: Context, booking: Booking, rating: Int, comment: String, imageUris: List<Uri>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return@launch
                val bookingId = booking.id ?: return@launch

                val uploadJobs = imageUris.map { uri ->
                    async {
                        val result = CloudinaryHelper.uploadImage(context, uri, "reviews")
                        result["secure_url"] as String
                    }
                }
                val imageUrls = uploadJobs.awaitAll()

                val review = Review(
                    bookingId = bookingId,
                    propertyId = booking.propertyId,
                    userId = userId,
                    rating = rating,
                    comment = comment.trim(),
                    photos = imageUrls,
                    isVerified = (imageUrls.size >= 2),
                    createdAt = kotlinx.datetime.Clock.System.now().toString()
                )

                Supabase.client.postgrest["reviews"].insert(review)
                fetchUserBookings()
                onSuccess()
            } catch (_: Exception) {}
        }
    }
}

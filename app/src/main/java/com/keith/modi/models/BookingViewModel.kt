package com.keith.modi.models

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keith.modi.CloudinaryHelper
import com.keith.modi.Supabase
import com.keith.modi.utils.ErrorUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
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
            } catch (e: Exception) {
                // Silent failure
            }
        }
    }

    fun fetchUserBookings() {
        viewModelScope.launch {
            _bookingState.value = BookingState.Loading
            try {
                val user = Supabase.client.auth.currentUserOrNull()
                if (user != null) {
                    val bookings = try {
                        Supabase.client.postgrest["bookings"]
                            .select(columns = Columns.raw("*, properties(*)")) {
                                filter {
                                    eq("guest_id", user.id)
                                }
                            }
                            .decodeList<Booking>()
                    } catch (e: Exception) {
                        // Fallback: If join fails, fetch bookings only to avoid crash
                        Supabase.client.postgrest["bookings"]
                            .select {
                                filter {
                                    eq("guest_id", user.id)
                                }
                            }
                            .decodeList<Booking>()
                    }
                    
                    _bookingState.value = BookingState.Success(bookings)
                } else {
                    _bookingState.value = BookingState.Error("Please sign in to view your trips.")
                }
            } catch (e: Exception) {
                // ErrorUtils will now rethrow CancellationException automatically
                _bookingState.value = BookingState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun submitReview(
        context: Context,
        booking: Booking,
        rating: Int,
        comment: String,
        imageUris: List<Uri>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return@launch
                val bookingId = booking.id ?: return@launch

                // PENDO: Secure Concurrent Uploads
                // We upload to Cloudinary first to get the public URLs
                val uploadJobs = imageUris.map { uri ->
                    async {
                        val result = CloudinaryHelper.uploadImage(context, uri, "reviews")
                        result["secure_url"] as String
                    }
                }
                
                val imageUrls = uploadJobs.awaitAll()

                // PENDO: Data Integrity - Using formal Review model for type-safe database insertion
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
                
                fetchUserBookings() // Refresh UI state
                onSuccess()
            } catch (e: Exception) {
                // Pendo: Log errors securely, don't crash
                e.printStackTrace()
            }
        }
    }
}

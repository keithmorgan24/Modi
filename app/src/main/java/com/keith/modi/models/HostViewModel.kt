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
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresListDataFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.collections.get
import kotlin.reflect.KProperty1

sealed class HostListingState {
    object Idle : HostListingState()
    object Loading : HostListingState()
    object Success : HostListingState()
    data class Error(val message: String) : HostListingState()
}

class HostViewModel : ViewModel() {
    private val _listingState = MutableStateFlow<HostListingState>(HostListingState.Idle)
    val listingState: StateFlow<HostListingState> = _listingState.asStateFlow()

    private val _myProperties = MutableStateFlow<List<Property>>(emptyList())
    val myProperties: StateFlow<List<Property>> = _myProperties.asStateFlow()

    private val _pendingBookings = MutableStateFlow<List<Booking>>(emptyList())
    val pendingBookings: StateFlow<List<Booking>> = _pendingBookings.asStateFlow()

    private val _pendingBookingsCount = MutableStateFlow(0)
    val pendingBookingsCount: StateFlow<Int> = _pendingBookingsCount.asStateFlow()

    private val _totalEarnings = MutableStateFlow(0.0)
    val totalEarnings: StateFlow<Double> = _totalEarnings.asStateFlow()

    init {
        fetchMyProperties()
        fetchStats()
        setupRealtime()
    }

    private fun setupRealtime() {
        viewModelScope.launch {
            try {
                val channel = Supabase.client.realtime.channel("bookings-changes")
                channel.postgresListDataFlow<Booking, String?>(
                    schema = "public",
                    table = "bookings",
                    primaryKey = Booking::id
                ).collectLatest { _ ->
                    fetchStats()
                }
                channel.subscribe()
            } catch (e: Exception) {
                // Realtime failed
            }
        }
    }

    fun fetchStats() {
        viewModelScope.launch {
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    // Fetch bookings for properties owned by this host using a join
                    val bookings = Supabase.client.postgrest["bookings"]
                        .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("*, properties!inner(*)")) {
                            filter {
                                eq("properties.host_id", userId)
                            }
                        }.decodeList<Booking>()
                    
                    val myPendingBookings = bookings.filter { it.status == "PENDING" }
                    _pendingBookings.value = myPendingBookings
                    _pendingBookingsCount.value = myPendingBookings.size

                    // Calculate earnings (sum of feePaid for CONFIRMED bookings)
                    val confirmedBookings = bookings.filter { it.status == "CONFIRMED" }
                    _totalEarnings.value = confirmedBookings.sumOf { it.feePaid }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun fetchMyProperties() {
        viewModelScope.launch {
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    val properties = Supabase.client.postgrest["properties"]
                        .select {
                            filter {
                                eq("host_id", userId)
                            }
                        }.decodeList<Property>()
                    _myProperties.value = properties
                }
            } catch (e: Exception) {
                // Silently fail or log for now in fetch
            }
        }
    }

    fun createListing(
        context: Context,
        name: String,
        description: String,
        price: Double,
        location: String,
        categories: List<String>,
        imageUris: List<Uri>,
        latitude: Double = -1.286389,
        longitude: Double = 36.817223,
        totalRooms: Int = 1
    ) {
        viewModelScope.launch {
            _listingState.value = HostListingState.Loading
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id
                    ?: throw Exception("User not authenticated")

                val tags = mutableSetOf<String>()
                // Add host-selected categories to tags for high-precision search
                tags.addAll(categories)
                
                val imageUrls = imageUris.map { uri ->
                    val result = CloudinaryHelper.uploadImage(context, uri, "properties")
                    
                    // Extract AI tags from Cloudinary response
                    val info = result["info"] as? Map<*, *>
                    val categorization = info?.get("categorization") as? Map<*, *>
                    val googleTagging = categorization?.get("google_tagging") as? Map<*, *>
                    val detectedTags = googleTagging?.get("data") as? List<*>
                    
                    detectedTags?.forEach { tag ->
                        val tagMap = tag as? Map<*, *>
                        val tagName = tagMap?.get("tag") as? String
                        val confidence = (tagMap?.get("confidence") as? Number)?.toDouble() ?: 0.0
                        if (tagName != null && confidence > 0.7) {
                            tags.add(tagName)
                        }
                    }

                    result["secure_url"] as String
                }

                // Append AI tags to description for better searchability
                val enrichedDescription = if (tags.isNotEmpty()) {
                    "$description\n\n#${tags.joinToString(" #")}"
                } else {
                    description
                }

                val newProperty = Property(
                    id = UUID.randomUUID().toString(),
                    hostId = userId,
                    title = name,
                    description = enrichedDescription,
                    price = price,
                    locationName = location,
                    distanceKm = 0.0,
                    imageUrls = imageUrls,
                    latitude = latitude,
                    longitude = longitude,
                    category = categories.firstOrNull() ?: "Nearby",
                    tags = tags.toList(),
                    totalRooms = totalRooms,
                    occupiedRooms = 0
                )

                Supabase.client.postgrest["properties"].insert(newProperty)
                
                _myProperties.value += newProperty
                _listingState.value = HostListingState.Success
            } catch (e: Exception) {
                _listingState.value = HostListingState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun approveBooking(bookingId: String) {
        viewModelScope.launch {
            try {
                Supabase.client.postgrest["bookings"].update(
                    mapOf("status" to "CONFIRMED")
                ) {
                    filter {
                        eq("id", bookingId)
                    }
                }
                fetchStats() // Refresh
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun rejectBooking(bookingId: String) {
        viewModelScope.launch {
            try {
                Supabase.client.postgrest["bookings"].update(
                    mapOf("status" to "CANCELLED")
                ) {
                    filter {
                        eq("id", bookingId)
                    }
                }
                fetchStats() // Refresh
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteProperty(propertyId: String) {
        viewModelScope.launch {
            try {
                Supabase.client.postgrest["properties"].delete {
                    filter {
                        eq("id", propertyId)
                    }
                }
                _myProperties.value = _myProperties.value.filter { it.id != propertyId }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateProperty(
        id: String,
        name: String,
        description: String,
        price: Double,
        location: String,
        category: String
    ) {
        viewModelScope.launch {
            try {
                val updates = mapOf(
                    "title" to name,
                    "description" to description,
                    "price_per_night" to price,
                    "location_name" to location,
                    "category" to category
                )
                Supabase.client.postgrest["properties"].update(updates) {
                    filter { eq("id", id) }
                }
                _myProperties.value = _myProperties.value.map {
                    if (it.id == id) it.copy(
                        title = name,
                        description = description,
                        price = price,
                        locationName = location,
                        category = category
                    ) else it
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updatePropertyOccupancy(id: String, occupiedCount: Int) {
        viewModelScope.launch {
            try {
                Supabase.client.postgrest["properties"].update(
                    mapOf("occupied_rooms" to occupiedCount)
                ) {
                    filter { eq("id", id) }
                }
                _myProperties.value = _myProperties.value.map {
                    if (it.id == id) it.copy(occupiedRooms = occupiedCount) else it
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun resetListingState() {
        _listingState.value = HostListingState.Idle
    }
}

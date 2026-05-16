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
import kotlinx.datetime.Clock
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.collections.get

sealed class HostListingState {
    object Idle : HostListingState()
    object Loading : HostListingState()
    data class Success(val message: String? = null) : HostListingState()
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

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    init {
        fetchMyProperties()
        fetchStats()
        setupRealtime()
        fetchCategories()
    }

    private fun setupRealtime() {
        viewModelScope.launch {
            try {
                // PENDO: Listen for ANY changes in the bookings table
                val channel = Supabase.client.realtime.channel("host-bookings-realtime")
                channel.postgresListDataFlow<Booking, String?>(
                    schema = "public",
                    table = "bookings",
                    primaryKey = Booking::id
                ).collectLatest { _ ->
                    // Whenever any booking changes (status update), refresh the stats
                    fetchStats()
                }
                channel.subscribe()
            } catch (e: Exception) {
                println("[REALTIME] Booking listener failed: ${e.message}")
            }
        }
    }

    fun fetchCategories() {
        viewModelScope.launch {
            try {
                val cats = Supabase.client.postgrest["categories"]
                    .select()
                    .decodeList<Category>()
                _categories.value = cats
            } catch (e: Exception) {
                // Fallback if table doesn't exist yet or error
                _categories.value = listOf(
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
    }

    fun addCategory(name: String) {
        val sanitized = name.trim().replaceFirstChar { it.uppercase() }
        if (sanitized.isBlank()) return
        
        viewModelScope.launch {
            try {
                // Check if exists
                val exists = _categories.value.any { it.name.equals(sanitized, ignoreCase = true) }
                if (!exists) {
                    val newCat = Category(name = sanitized, isVerified = false)
                    Supabase.client.postgrest["categories"].insert(newCat)
                    fetchCategories() // Refresh
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun fetchStats() {
        viewModelScope.launch {
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return@launch
                
                // 1. Fetch Host properties first to filter bookings securely
                val myProps = Supabase.client.postgrest["properties"]
                    .select { filter { eq("host_id", userId) } }
                    .decodeList<Property>()
                
                val myPropIds = myProps.mapNotNull { it.id }
                if (myPropIds.isEmpty()) {
                    _pendingBookings.value = emptyList()
                    _pendingBookingsCount.value = 0
                    _totalEarnings.value = 0.0
                    return@launch
                }

                // 2. Fetch only bookings related to this host's properties
                val hostBookingsRaw = Supabase.client.postgrest["bookings"]
                    .select { 
                        filter { isIn("property_id", myPropIds) }
                        order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }
                    .decodeList<Booking>()

                // 3. Fetch Guest profiles only for these specific bookings
                val guestIds = hostBookingsRaw.map { it.guestId }.distinct()
                val profiles = if (guestIds.isNotEmpty()) {
                    Supabase.client.postgrest["profiles"]
                        .select { filter { isIn("id", guestIds) } }
                        .decodeList<Profile>()
                } else emptyList()

                // 4. Intelligence Mapping
                val hostBookings = hostBookingsRaw.map { booking ->
                    val linkedProp = myProps.find { it.id == booking.propertyId }
                    val guestProfile = profiles.find { it.id == booking.guestId }
                    booking.copy(property = linkedProp, guestProfile = guestProfile)
                }

                println("[DEBUG] Mapped ${hostBookings.size} bookings with profiles")
                
                _pendingBookings.value = hostBookings
                _pendingBookingsCount.value = hostBookings.count { it.status == "PENDING" }

                val confirmedOrArrived = hostBookings.filter { it.status == "CONFIRMED" || it.status == "ARRIVED" }
                _totalEarnings.value = confirmedOrArrived.sumOf { it.feePaid ?: 0.0 }
                
            } catch (e: Exception) {
                println("[DEBUG] fetchStats error: ${e.message}")
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
                // PENDO: Structured concurrency ensures that if one upload fails, everything is caught and handled.
                val userId = Supabase.client.auth.currentUserOrNull()?.id
                    ?: throw Exception("User not authenticated")

                val tags = mutableSetOf<String>()
                tags.addAll(categories.map { it.trim() })
                
                // PENDO: Wrap async operations in a coroutineScope to catch child exceptions properly
                val imageUrls = kotlinx.coroutines.coroutineScope {
                    imageUris.map { uri ->
                        async {
                            val result = CloudinaryHelper.uploadImage(context, uri, "properties")
                            
                            // Safe tag extraction
                            try {
                                val info = result["info"] as? Map<*, *>
                                val categorization = info?.get("categorization") as? Map<*, *>
                                val googleTagging = categorization?.get("google_tagging") as? Map<*, *>
                                val detectedTags = googleTagging?.get("data") as? List<*>
                                
                                detectedTags?.forEach { tag ->
                                    val tagMap = tag as? Map<*, *>
                                    val tagName = tagMap?.get("tag") as? String
                                    val confidence = (tagMap?.get("confidence") as? Number)?.toDouble() ?: 0.0
                                    if (tagName != null && confidence > 0.7) {
                                        synchronized(tags) {
                                            tags.add(tagName.trim())
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // PENDO: Don't fail the whole upload if just tagging fails
                            }

                            (result["secure_url"] as? String) ?: throw Exception("Failed to retrieve secure URL from Cloudinary")
                        }
                    }.awaitAll()
                }

                val sanitizedName = name.trim()
                val sanitizedDescription = description.trim()
                val sanitizedLocation = location.trim()

                val enrichedDescription = if (tags.isNotEmpty()) {
                    "$sanitizedDescription\n\n#${tags.joinToString(" #")}"
                } else {
                    sanitizedDescription
                }

                val newProperty = Property(
                    id = java.util.UUID.randomUUID().toString(),
                    hostId = userId,
                    title = sanitizedName,
                    description = enrichedDescription,
                    price = price,
                    locationName = sanitizedLocation,
                    imageUrls = imageUrls,
                    latitude = latitude,
                    longitude = longitude,
                    category = categories.firstOrNull()?.trim() ?: "Nearby",
                    tags = tags.toList(),
                    totalRooms = totalRooms,
                    occupiedRooms = 0
                )

                Supabase.client.postgrest["properties"].insert(newProperty)
                
                _myProperties.value += newProperty
                _listingState.value = HostListingState.Success("Listing '$sanitizedName' created successfully!")
            } catch (e: Throwable) {
                // PENDO: Explicitly handle CancellationException to avoid overriding it
                if (e is kotlinx.coroutines.CancellationException) throw e

                _listingState.value = HostListingState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun approveBooking(booking: Booking) {
        val bookingId = booking.id ?: return
        val guestName = booking.guestProfile?.fullName ?: "Guest"
        viewModelScope.launch {
            _listingState.value = HostListingState.Loading
            try {
                Supabase.client.postgrest["bookings"].update(
                    buildJsonObject { put("status", "CONFIRMED") }
                ) {
                    filter { eq("id", bookingId) }
                }
                // The Realtime listener will trigger fetchStats automatically, 
                // but we call it here for instant feedback.
                fetchStats()
                _listingState.value = HostListingState.Success("Approved $guestName successfully!")
            } catch (e: Exception) {
                _listingState.value = HostListingState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun markAsArrived(booking: Booking) {
        val bookingId = booking.id ?: return
        val guestName = booking.guestProfile?.fullName ?: "Guest"
        viewModelScope.launch {
            _listingState.value = HostListingState.Loading
            try {
                Supabase.client.postgrest["bookings"].update(
                    buildJsonObject { 
                        put("status", "ARRIVED") 
                        put("checked_in_at", Clock.System.now().toString())
                    }
                ) {
                    filter { eq("id", bookingId) }
                }
                fetchStats()
                _listingState.value = HostListingState.Success("$guestName has arrived! Check-in confirmed.")
            } catch (e: Exception) {
                _listingState.value = HostListingState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun rejectBooking(booking: Booking) {
        val bookingId = booking.id ?: return
        val guestName = booking.guestProfile?.fullName ?: "Guest"
        viewModelScope.launch {
            _listingState.value = HostListingState.Loading
            try {
                Supabase.client.postgrest["bookings"].update(
                    buildJsonObject { put("status", "CANCELLED") }
                ) {
                    filter { eq("id", bookingId) }
                }
                fetchStats()
                _listingState.value = HostListingState.Success("Rejected $guestName's request.")
            } catch (e: Exception) {
                _listingState.value = HostListingState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun clearBookingsByCategory(status: String) {
        viewModelScope.launch {
            _listingState.value = HostListingState.Loading
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return@launch
                
                // 1. Get properties owned by this host to ensure they only clear THEIR data
                val myProps = Supabase.client.postgrest["properties"]
                    .select { filter { eq("host_id", userId) } }
                    .decodeList<Property>()
                
                val myPropIds = myProps.mapNotNull { it.id }
                if (myPropIds.isEmpty()) return@launch

                // 2. Delete bookings for those properties with the specific status
                Supabase.client.postgrest["bookings"].delete {
                    filter {
                        isIn("property_id", myPropIds)
                        eq("status", status)
                    }
                }
                
                fetchStats()
                _listingState.value = HostListingState.Success("Category $status cleared successfully.")
            } catch (e: Exception) {
                _listingState.value = HostListingState.Error(ErrorUtils.sanitizeError(e))
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
            }
        }
    }

    fun updateProperty(
        id: String,
        name: String,
        description: String,
        price: Double,
        location: String,
        category: String,
        totalRooms: Int,
        tags: List<String>
    ) {
        viewModelScope.launch {
            _listingState.value = HostListingState.Loading
            try {
                val sanitizedName = name.trim()
                val sanitizedDescription = description.trim()
                val sanitizedLocation = location.trim()
                val sanitizedTags = tags.map { it.trim() }

                val updates = buildJsonObject {
                    put("title", sanitizedName)
                    put("description", sanitizedDescription)
                    put("price_per_night", price)
                    put("location_name", sanitizedLocation)
                    put("category", category.trim())
                    put("total_rooms", totalRooms)
                    putJsonArray("tags") {
                        sanitizedTags.forEach { tag -> add(tag) }
                    }
                }

                Supabase.client.postgrest["properties"].update(updates) {
                    filter { eq("id", id) }
                }
                _myProperties.value = _myProperties.value.map {
                    if (it.id == id) it.copy(
                        title = sanitizedName,
                        description = sanitizedDescription,
                        price = price,
                        locationName = sanitizedLocation,
                        category = category.trim(),
                        totalRooms = totalRooms,
                        tags = sanitizedTags
                    ) else it
                }
                _listingState.value = HostListingState.Success("Property updated successfully!")
            } catch (e: Exception) {
                _listingState.value = HostListingState.Error(ErrorUtils.sanitizeError(e))
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
            }
        }
    }

    fun resetListingState() {
        _listingState.value = HostListingState.Idle
    }
}

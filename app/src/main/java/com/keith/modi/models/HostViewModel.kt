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
                val userId = Supabase.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    val bookings = Supabase.client.postgrest["bookings"]
                        .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("*, properties!inner(*)")) {
                            filter {
                                eq("properties.host_id", userId)
                            }
                        }.decodeList<Booking>()
                    
                    val myPendingBookings = bookings.filter { it.status == "PENDING" }
                    _pendingBookings.value = myPendingBookings
                    _pendingBookingsCount.value = myPendingBookings.size

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
                // PENDO: Structured concurrency ensures that if one upload fails, everything is caught and handled.
                val userId = Supabase.client.auth.currentUserOrNull()?.id
                    ?: throw Exception("User not authenticated")

                val tags = mutableSetOf<String>()
                tags.addAll(categories)
                
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
                                            tags.add(tagName)
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

                val enrichedDescription = if (tags.isNotEmpty()) {
                    "$description\n\n#${tags.joinToString(" #")}"
                } else {
                    description
                }

                val newProperty = Property(
                    id = java.util.UUID.randomUUID().toString(),
                    hostId = userId,
                    title = name,
                    description = enrichedDescription,
                    price = price,
                    locationName = location,
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
            } catch (e: Throwable) {
                // PENDO: Explicitly handle CancellationException to avoid overriding it
                if (e is kotlinx.coroutines.CancellationException) throw e

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
                fetchStats()
            } catch (e: Exception) {
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
                fetchStats()
            } catch (e: Exception) {
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
                val updates = buildJsonObject {
                    put("title", name)
                    put("description", description)
                    put("price_per_night", price)
                    put("location_name", location)
                    put("category", category)
                    put("total_rooms", totalRooms)
                    putJsonArray("tags") {
                        tags.forEach { tag -> add(tag) }
                    }
                }

                Supabase.client.postgrest["properties"].update(updates) {
                    filter { eq("id", id) }
                }
                _myProperties.value = _myProperties.value.map {
                    if (it.id == id) it.copy(
                        title = name,
                        description = description,
                        price = price,
                        locationName = location,
                        category = category,
                        totalRooms = totalRooms,
                        tags = tags
                    ) else it
                }
                _listingState.value = HostListingState.Success
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

package com.keith.modi.screens.customer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.keith.modi.models.Property
import com.keith.modi.models.PropertyState
import com.keith.modi.models.PropertyViewModel
import com.keith.modi.models.Review
import com.keith.modi.ui.theme.ModiTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(viewModel: PropertyViewModel = viewModel()) {
    val propertyState by viewModel.propertyState.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val categories = listOf("All", "Beachfront", "Pool", "Luxury", "Modern", "WiFi", "Central", "Cabins")
    var selectedCategory by remember { mutableStateOf("All") }
    
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedProperty by remember { mutableStateOf<Property?>(null) }
    var showBookingSheet by remember { mutableStateOf(false) }
    var isMapView by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isMapView && propertyState is PropertyState.Success) {
            MapScreen(
                properties = (propertyState as PropertyState.Success).properties,
                onBack = { isMapView = false },
                onPropertyClick = {
                    selectedProperty = it
                    showBookingSheet = true
                }
            )
        } else {
            Scaffold(
                topBar = {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Find your next stay",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { isMapView = true }) {
                                Icon(Icons.Default.Map, contentDescription = "Map View", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search Airbnbs...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = { 
                                IconButton(onClick = { showFilterSheet = true }) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filter")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }
            ) { innerPadding ->
                when (propertyState) {
                    is PropertyState.Loading -> {
                        Column(modifier = Modifier.padding(innerPadding)) {
                            repeat(3) { ShimmerAirbnbCard() }
                        }
                    }
                    is PropertyState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline, 
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp), 
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text((propertyState as PropertyState.Error).message)
                            }
                        }
                    }
                    is PropertyState.Success -> {
                        val allProperties = (propertyState as PropertyState.Success).properties
                        val filteredProperties = if (selectedCategory == "All") {
                            allProperties
                        } else {
                            allProperties.filter { property ->
                                property.tags.contains(selectedCategory) || property.category == selectedCategory
                            }
                        }

                        if (filteredProperties.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                                    Text("No properties found in this category", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                item {
                                    LazyRow(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(categories) { category ->
                                            FilterChip(
                                                selected = selectedCategory == category,
                                                onClick = { selectedCategory = category },
                                                label = { Text(category) },
                                                leadingIcon = if (selectedCategory == category) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
                                    }
                                }

                                items(filteredProperties) { property ->
                                    val reviews = (propertyState as? PropertyState.Success)?.reviews?.get(property.id) ?: emptyList()
                                    AirbnbCard(
                                        property = property,
                                        reviews = reviews,
                                        onClick = {
                                            selectedProperty = property
                                            showBookingSheet = true
                                            viewModel.createPendingBooking(property)
                                        },
                                        onLikeClick = { property.id?.let { viewModel.toggleLike(it) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Common Sheets (Accessible from both List and Map)
        if (showFilterSheet) {
            ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
                FilterSheetContent { showFilterSheet = false }
            }
        }

        if (showBookingSheet && selectedProperty != null) {
            val reviews = (propertyState as? PropertyState.Success)?.reviews?.get(selectedProperty!!.id) ?: emptyList()
            ModalBottomSheet(
                onDismissRequest = { 
                    showBookingSheet = false
                    val activeBooking = (propertyState as? PropertyState.Success)?.activeBooking
                    activeBooking?.id?.let { viewModel.cancelBooking(it) }
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                BookingSheetContent(
                    property = selectedProperty!!,
                    reviews = reviews,
                    viewModel = viewModel,
                    onClose = { 
                        showBookingSheet = false 
                        val activeBooking = (propertyState as? PropertyState.Success)?.activeBooking
                        activeBooking?.id?.let { viewModel.cancelBooking(it) }
                    }
                )
            }
        }
    }
}

@Composable
fun AirbnbCard(
    property: Property,
    reviews: List<Review> = emptyList(),
    onClick: () -> Unit,
    onLikeClick: () -> Unit = {}
) {
    val heartColor by animateColorAsState(
        targetValue = if (property.isLiked) Color.Red else Color.Gray,
        animationSpec = spring(dampingRatio = 0.5f)
    )
    
    val verifiedCount = reviews.count { it.isVerified }
    val averageRating = if (reviews.isNotEmpty()) reviews.map { it.rating.toDouble() }.average() else property.rating
    val reviewCount = reviews.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                AsyncImage(
                    model = property.imageUrls.firstOrNull(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // PENDO: Verified Badge - Enhanced with Trust Signals
                if (verifiedCount > 0) {
                    Surface(
                        modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
                        color = Color(0xFFE8F5E9).copy(alpha = 0.95f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Verified, 
                                contentDescription = null, 
                                tint = Color(0xFF2E7D32), 
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "MODI VERIFIED", 
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp), 
                                fontWeight = FontWeight.ExtraBold, 
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onLikeClick() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = if (property.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = heartColor
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = property.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                        Text(
                            text = " ${String.format("%.1f", averageRating)} ($reviewCount)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(text = property.locationName, color = MaterialTheme.colorScheme.secondary)
                Text(text = "${property.distanceKm} km away", style = MaterialTheme.typography.bodySmall)
                
                if (property.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        property.tags.take(3).forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "Ksh ${property.price}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = " / night", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

@Composable
fun FilterSheetContent(onApply: () -> Unit) {
    var priceRange by remember { mutableStateOf(0f..50000f) }
    
    Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
        Text("Filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Price Range (Ksh)", fontWeight = FontWeight.Bold)
        RangeSlider(
            value = priceRange,
            onValueChange = { priceRange = it },
            valueRange = 0f..50000f,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Min: ${priceRange.start.toInt()}")
            Text("Max: ${priceRange.endInclusive.toInt()}")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Sort By", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(onClick = {}, label = { Text("Rating") })
            SuggestionChip(onClick = {}, label = { Text("Distance") })
            SuggestionChip(onClick = {}, label = { Text("Newest") })
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
            Text("Apply Filters")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BookingSheetContent(
    property: Property,
    reviews: List<Review>,
    viewModel: PropertyViewModel,
    onClose: () -> Unit
) {
    var timeLeft by remember { mutableIntStateOf(300) } // 5 minutes in seconds
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Review Booking", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = property.title, style = MaterialTheme.typography.titleLarge)
        Text(text = property.locationName, color = MaterialTheme.colorScheme.secondary)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Room Price")
                    Text("Ksh ${property.price}")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Booking Fee (10%)", fontWeight = FontWeight.Bold)
                    Text("Ksh ${property.price * 0.1}", fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Due Now", fontWeight = FontWeight.ExtraBold)
                    Text("Ksh ${property.price * 0.1}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // PENDO: Social Proof Gallery
        val guestPhotos = reviews.flatMap { it.photos }.distinct()
        if (guestPhotos.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Guest Experiences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (reviews.any { it.isVerified }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verified", style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                items(guestPhotos) { photoUrl ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(100.dp)
                    ) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Guest photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Notice: This room is temporarily locked for you. Please pay the booking fee within 5 minutes to confirm.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { /* Handle Payment */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Pay Booking Fee", fontWeight = FontWeight.Bold)
        }
        
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel and Unlock Room")
        }
    }
}

@Composable
fun ShimmerAirbnbCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(1000),
            repeatMode = RepeatMode.Reverse
        ), 
        label = "alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.LightGray.copy(alpha = alpha)))
            Column(modifier = Modifier.padding(16.dp)) {
                Box(modifier = Modifier.width(150.dp).height(20.dp).background(Color.LightGray.copy(alpha = alpha)))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.width(100.dp).height(15.dp).background(Color.LightGray.copy(alpha = alpha)))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ExploreScreenPreview() {
    ModiTheme {
        ExploreScreen()
    }
}

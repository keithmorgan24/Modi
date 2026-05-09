package com.keith.modi.screens.customer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.keith.modi.models.Property
import com.keith.modi.models.PropertyState
import com.keith.modi.models.PropertyViewModel
import com.keith.modi.models.Review
import com.keith.modi.screens.customer.MpesaPaymentDialog
import com.keith.modi.ui.theme.ModiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(viewModel: PropertyViewModel = viewModel()) {
    val propertyState by viewModel.propertyState.collectAsState()
    val scope = rememberCoroutineScope()
    
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
                            Text(text = "Find your next stay", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { isMapView = true }) {
                                Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search Airbnbs...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { 
                                IconButton(onClick = { showFilterSheet = true }) {
                                    Icon(Icons.Default.FilterList, null)
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
                        Column(modifier = Modifier.padding(innerPadding)) { repeat(3) { ShimmerAirbnbCard() } }
                    }
                    is PropertyState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text((propertyState as PropertyState.Error).message)
                            }
                        }
                    }
                    is PropertyState.Success -> {
                        val successState = propertyState as PropertyState.Success
                        val filteredProperties = if (selectedCategory == "All") successState.properties else successState.properties.filter { it.tags.contains(selectedCategory) || it.category == selectedCategory }

                        var isRefreshing by remember { mutableStateOf(false) }
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                isRefreshing = true
                                scope.launch { viewModel.fetchProperties(); delay(1000); isRefreshing = false }
                            },
                            modifier = Modifier.fillMaxSize().padding(innerPadding)
                        ) {
                            if (filteredProperties.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No properties found", style = MaterialTheme.typography.bodyLarge)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                                    item {
                                        LazyRow(modifier = Modifier.padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(categories) { category ->
                                                FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text(category) })
                                            }
                                        }
                                    }
                                    items(filteredProperties) { property ->
                                        AirbnbCard(
                                            property = property,
                                            reviews = successState.reviews[property.id] ?: emptyList(),
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
        }

        if (showBookingSheet && selectedProperty != null) {
            val successState = propertyState as? PropertyState.Success
            ModalBottomSheet(
                onDismissRequest = { 
                    showBookingSheet = false
                    successState?.activeBooking?.id?.let { viewModel.cancelBooking(it) }
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                BookingSheetContent(
                    property = selectedProperty!!,
                    reviews = successState?.reviews?.get(selectedProperty!!.id) ?: emptyList(),
                    viewModel = viewModel,
                    onClose = { 
                        showBookingSheet = false 
                        successState?.activeBooking?.id?.let { viewModel.cancelBooking(it) }
                    }
                )
            }
        }
    }
}

@Composable
fun BookingSheetContent(
    property: Property,
    reviews: List<Review>,
    viewModel: PropertyViewModel,
    onClose: () -> Unit
) {
    val propertyState by viewModel.propertyState.collectAsState()
    val successState = propertyState as? PropertyState.Success
    val activeBooking = successState?.activeBooking
    val isBookingLoading = successState?.isBookingLoading ?: false
    val bookingError = successState?.bookingError
    
    var timeLeft by remember { mutableIntStateOf(300) }
    var showMpesaPrompt by remember { mutableStateOf(false) }
    var paymentErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) { delay(1000); timeLeft-- }
    }

    if (showMpesaPrompt && activeBooking != null) {
        MpesaPaymentDialog(
            amount = property.price * 0.1,
            errorMessage = paymentErrorMessage,
            onPayClicked = { phone ->
                viewModel.payWithMpesa(phone, property.price * 0.1, activeBooking.id!!) { success, message ->
                    if (!success) { paymentErrorMessage = message } 
                    else { showMpesaPrompt = false }
                }
            },
            onCancel = { showMpesaPrompt = false; paymentErrorMessage = null }
        )
    }

    Column(modifier = Modifier.fillMaxHeight(0.9f).padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Review Booking", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                Text(String.format("%02d:%02d", timeLeft / 60, timeLeft % 60), modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(Modifier.height(24.dp))
        Text(property.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(property.locationName, color = MaterialTheme.colorScheme.secondary)

        Spacer(Modifier.height(16.dp))
        Text("About this space", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(property.description ?: "A serene Modi space.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { 
                    Text("Stay Price")
                    Text("Ksh ${property.price}", fontWeight = FontWeight.Bold) 
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { 
                    Text("Modi Fee (10%)")
                    Text("Ksh ${property.price * 0.1}", fontWeight = FontWeight.Bold) 
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { 
                    Text("Total Deposit", fontWeight = FontWeight.ExtraBold)
                    Text("Ksh ${property.price * 0.1}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp) 
                }
            }
        }

        if (bookingError != null) {
            Text(bookingError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(Modifier.height(32.dp))
        Text("Guest Reviews (${reviews.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        reviews.take(3).forEach { review ->
            Text("Guest: ${review.comment}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(40.dp))
        Button(
            onClick = { if (activeBooking != null) showMpesaPrompt = true else viewModel.createPendingBooking(property) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isBookingLoading
        ) {
            if (isBookingLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            else Text(if (activeBooking == null) "Secure My Room 🔒" else "Pay Booking Fee 💰", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
fun AirbnbCard(property: Property, reviews: List<Review>, onClick: () -> Unit, onLikeClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { onClick() }, shape = RoundedCornerShape(24.dp)) {
        Column {
            AsyncImage(model = property.imageUrls.firstOrNull(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
            Column(Modifier.padding(16.dp)) {
                Text(property.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(property.locationName, color = MaterialTheme.colorScheme.secondary)
                Text("Ksh ${property.price} / night", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
fun FilterSheetContent(onApply: () -> Unit) {
    Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Apply Filters") }
}

@Composable
fun ShimmerAirbnbCard() {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(24.dp)) {
        Box(Modifier.fillMaxWidth().height(200.dp).background(Color.LightGray.copy(alpha = 0.5f)))
    }
}

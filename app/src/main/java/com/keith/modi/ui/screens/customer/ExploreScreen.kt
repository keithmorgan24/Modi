package com.keith.modi.ui.screens.customer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.keith.modi.models.Property
import com.keith.modi.models.PropertyState
import com.keith.modi.models.PropertyViewModel
import com.keith.modi.models.MainViewModel
import com.keith.modi.models.Review
import com.keith.modi.ui.theme.ModiTheme
import com.keith.modi.ui.screens.customer.AirbnbCard
import com.keith.modi.ui.screens.customer.ShimmerAirbnbCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: PropertyViewModel = viewModel(),
    mainViewModel: MainViewModel = viewModel(),
    onMapToggle: (Boolean) -> Unit = {}
) {
    val propertyState by viewModel.propertyState.collectAsState()
    val isGuest by mainViewModel.isGuest.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedProperty by remember { mutableStateOf<Property?>(null) }
    var showBookingSheet by remember { mutableStateOf(false) }
    var isMapView by remember { mutableStateOf(false) }

    // Guest Prompt State
    var showGuestPrompt by remember { mutableStateOf(false) }
    var guestPromptMessage by remember { mutableStateOf("") }
    var guestPromptAction by remember { mutableStateOf({}) }

    // PENDO: Sync Map State with Parent (e.g., to disable Pager swiping)
    LaunchedEffect(isMapView) {
        onMapToggle(isMapView)
    }

    // PENDO: Intelligent Back Navigation - Intercept back press when Map is open
    BackHandler(enabled = isMapView) {
        isMapView = false
    }

    // PENDO: Calculate filtered properties once to be shared by List and Map
    val filteredProperties = remember(propertyState, searchQuery, selectedCategory) {
        val success = propertyState as? PropertyState.Success ?: return@remember emptyList()
        success.properties.filter { property ->
            val matchesCategory = if (selectedCategory == "All") true 
                else property.category.equals(selectedCategory, ignoreCase = true) || 
                     property.tags.any { it.equals(selectedCategory, ignoreCase = true) }
            
            val matchesSearch = if (searchQuery.isBlank()) true
                else property.title.contains(searchQuery, ignoreCase = true) || 
                     property.locationName.contains(searchQuery, ignoreCase = true) ||
                     (property.description ?: "").contains(searchQuery, ignoreCase = true)
            
            matchesCategory && matchesSearch
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isMapView) {
            when (propertyState) {
                is PropertyState.Success -> {
                    MapScreen(
                        properties = filteredProperties,
                        onBack = { isMapView = false },
                        onPropertyClick = {
                            selectedProperty = it
                            showBookingSheet = true
                        }
                    )
                }
                is PropertyState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading Map View...", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                is PropertyState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Map, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                            Text("Unable to load map")
                            Button(onClick = { isMapView = false }) { Text("Go Back") }
                        }
                    }
                }
            }
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
                                Icon(Icons.Default.Map, "Switch to Map", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by title, location or vibes...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { 
                                Row {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, null)
                                        }
                                    }
                                    IconButton(onClick = { showFilterSheet = true }) {
                                        Icon(Icons.Default.FilterList, null)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
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
                        val categories = listOf("All") + successState.categories.map { it.name }
                        
                        var isRefreshing by remember { mutableStateOf(false) }
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                isRefreshing = true
                                scope.launch { viewModel.fetchProperties(); delay(1000); isRefreshing = false }
                            },
                            modifier = Modifier.fillMaxSize().padding(innerPadding)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                LazyRow(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(categories) { category ->
                                        FilterChip(
                                            selected = selectedCategory == category,
                                            onClick = { selectedCategory = category },
                                            label = { Text(category) }
                                        )
                                    }
                                }

                                if (filteredProperties.isEmpty()) {
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Default.SearchOff,
                                                null,
                                                modifier = Modifier.size(64.dp),
                                                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "No matches found",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            TextButton(onClick = { selectedCategory = "All"; searchQuery = "" }) {
                                                Text("Reset Filters")
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 16.dp)
                                    ) {
                                        items(filteredProperties) { property ->
                                            AirbnbCard(
                                                property = property,
                                                reviews = successState.reviews[property.id] ?: emptyList(),
                                                onClick = {
                                                    selectedProperty = property
                                                    showBookingSheet = true
                                                },
                                                onLikeClick = { 
                                                    property.id?.let { viewModel.toggleLike(it) } 
                                                }
                                            )
                                        }
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
                    isGuest = isGuest,
                    onClose = { 
                        showBookingSheet = false 
                    },
                    onRegisterPrompt = {
                        guestPromptMessage = "To save your data and to be able to post reviews and enjoy other features of Modi, kindly register/sign in."
                        guestPromptAction = {
                            mainViewModel.setGuestMode(false)
                        }
                        showGuestPrompt = true
                    }
                )
            }
        }

        if (showGuestPrompt) {
            AlertDialog(
                onDismissRequest = { showGuestPrompt = false },
                title = { Text("Registration Required", fontWeight = FontWeight.Bold) },
                text = { Text(guestPromptMessage) },
                confirmButton = {
                    Button(onClick = { 
                        showGuestPrompt = false
                        guestPromptAction()
                    }) {
                        Text("Sign In / Register")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showGuestPrompt = false
                        // PENDO: If it was a booking prompt, allow them to proceed as guest
                        if (guestPromptMessage.contains("save your data")) {
                            selectedProperty?.let { viewModel.createPendingBooking(it) }
                        }
                    }) {
                        Text("Later")
                    }
                }
            )
        }
    }
}

@Composable
fun BookingSheetContent(
    property: Property,
    reviews: List<Review>,
    viewModel: PropertyViewModel,
    isGuest: Boolean,
    onClose: () -> Unit,
    onRegisterPrompt: () -> Unit
) {
    val propertyState by viewModel.propertyState.collectAsState()
    val successState = propertyState as? PropertyState.Success
    val activeBooking = successState?.activeBooking
    val isBookingLoading = successState?.isBookingLoading ?: false
    val bookingError = successState?.bookingError
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxHeight(0.8f).padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Text("Confirm Your Stay", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(16.dp))
        
        // PENDO: Intelligent Image Gallery - Support multiple property photos
        if (property.imageUrls.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(property.imageUrls) { imageUrl ->
                    Surface(
                        modifier = Modifier.width(300.dp).fillMaxHeight(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Property Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Text(property.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(property.locationName, color = MaterialTheme.colorScheme.secondary)

        Spacer(Modifier.height(16.dp))
        Text("About this space", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(property.description ?: "Experience the best of Modi living in this high-end, secure space.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // PENDO: Feature Tags - Show property highlights
        if (property.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                property.tags.forEach { tag ->
                    Text(
                        text = "#$tag",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { 
                    Text("Price per night")
                    Text("Ksh ${property.price}", fontWeight = FontWeight.Bold) 
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { 
                    Text("Total Estimate", fontWeight = FontWeight.ExtraBold)
                    Text("Ksh ${property.price}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp) 
                }
            }
        }

        if (bookingError != null) {
            Text(bookingError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(Modifier.height(32.dp))
        Text("Guest Reviews (${reviews.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (reviews.isEmpty()) {
            Text("No reviews yet. Be the first to experience this masterpiece! ✨", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                reviews.take(3).forEach { review ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(12.dp))
                            Text("Guest", fontWeight = FontWeight.Bold)
                        }
                        Text(review.comment, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { 
                if (activeBooking == null) {
                    viewModel.createPendingBooking(property)
                } else {
                    viewModel.confirmBooking(activeBooking.id!!)
                    
                    // PENDO: Trigger Google Maps Navigation on success
                    try {
                        val uri = Uri.parse("google.navigation:q=${property.latitude},${property.longitude}")
                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        context.startActivity(mapIntent)
                    } catch (e: Exception) {
                        // Fallback: Just open any map app if Google Maps is missing
                        val uri = Uri.parse("geo:${property.latitude},${property.longitude}?q=${Uri.encode(property.title)}")
                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(mapIntent)
                    }
                    
                    onClose()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isBookingLoading && !property.isFull
        ) {
            if (isBookingLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            else if (property.isFull) Text("FULLY BOOKED 🔴", fontWeight = FontWeight.Bold)
            else Text(if (activeBooking == null) (if (isGuest) "Book as Guest 👤" else "Secure My Room 🔒") else "Confirm Booking 🚀", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Go Back", color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
fun FilterSheetContent(onApply: () -> Unit) {
    Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Apply Filters") }
}

@Preview(showBackground = true)
@Composable
fun ExploreScreenPreview() {
    ModiTheme {
        ExploreScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun AirbnbCardPreview() {
    ModiTheme {
        AirbnbCard(
            property = Property(
                title = "Luxury Penthouse",
                locationName = "Kilimani, Nairobi",
                price = 15000.0,
                hostId = "123",
                imageUrls = listOf("https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80")
            ),
            reviews = emptyList(),
            onClick = {},
            onLikeClick = {}
        )
    }
}

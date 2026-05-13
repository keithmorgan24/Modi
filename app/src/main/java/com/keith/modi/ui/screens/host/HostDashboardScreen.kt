package com.keith.modi.ui.screens.host

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keith.modi.models.HostListingState
import com.keith.modi.models.HostViewModel
import com.keith.modi.ui.theme.ModiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostDashboardScreen(hostViewModel: HostViewModel = viewModel()) {
    var showListWizard by remember { mutableStateOf(false) }
    var showBookingRequests by remember { mutableStateOf(false) }
    val myProperties by hostViewModel.myProperties.collectAsState()
    val pendingCount by hostViewModel.pendingBookingsCount.collectAsState()
    val pendingBookings by hostViewModel.pendingBookings.collectAsState()
    val earnings by hostViewModel.totalEarnings.collectAsState()
    val listingState by hostViewModel.listingState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // PENDO: Intelligent Back Navigation - Handle sub-screens before closing tab
    BackHandler(enabled = showListWizard || showBookingRequests) {
        if (showListWizard) showListWizard = false
        else if (showBookingRequests) showBookingRequests = false
    }

    LaunchedEffect(listingState) {
        if (listingState is HostListingState.Success) {
            snackbarHostState.showSnackbar("Action completed successfully! ✨")
            hostViewModel.resetListingState()
        } else if (listingState is HostListingState.Error) {
            snackbarHostState.showSnackbar((listingState as HostListingState.Error).message)
            hostViewModel.resetListingState()
        }
    }

    if (showListWizard) {
        ListAirbnbScreen(
            onBack = { showListWizard = false },
            hostViewModel = hostViewModel
        )
    } else if (showBookingRequests) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Booking Requests", fontWeight = FontWeight.ExtraBold) },
                    navigationIcon = {
                        IconButton(onClick = { showBookingRequests = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                if (pendingBookings.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Notifications, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("No pending requests", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                } else {
                    items(pendingBookings, key = { it.id ?: "" }) { booking ->
                        val property = booking.property
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        modifier = Modifier.size(70.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        if (property?.imageUrls?.isNotEmpty() == true) {
                                            coil.compose.AsyncImage(
                                                model = property.imageUrls.first(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Notifications,
                                                contentDescription = null,
                                                modifier = Modifier.padding(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = property?.title ?: "Unknown Property",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Guest ID: ${booking.guestId.take(8)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            "PENDING",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val isLoading = listingState is HostListingState.Loading
                                    
                                    Button(
                                        onClick = { hostViewModel.approveBooking(booking.id!!) },
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        enabled = !isLoading,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Approve", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    
                                    OutlinedButton(
                                        onClick = { hostViewModel.rejectBooking(booking.id!!) },
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        enabled = !isLoading,
                                        shape = RoundedCornerShape(14.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Reject", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Host Dashboard", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { hostViewModel.fetchStats(); hostViewModel.fetchMyProperties() }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Refresh")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showListWizard = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("List Airbnb") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Text(
                        text = "Welcome back, Host!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Bento Grid - Row 1
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BentoCard(
                            title = "Total Earnings",
                            value = "Ksh ${String.format("%,.0f", earnings)}",
                            icon = Icons.Default.AttachMoney,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                        BentoCard(
                            title = "Active Listings",
                            value = "${myProperties.size}",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                }

                // Bento Grid - Row 2 (Large Card)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text("Pending Bookings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (pendingCount == 1) "1 Guest waiting for approval" 
                                else "$pendingCount Guests waiting for approval", 
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showBookingRequests = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                enabled = pendingCount > 0
                            ) {
                                Text("Review Requests")
                            }
                        }
                    }
                }

                item {
                    Text("Your Properties", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                if (myProperties.isEmpty()) {
                    item {
                        Text("No properties listed yet. Tap 'List Airbnb' to start.", color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    items(myProperties, key = { it.id ?: "" }) { property ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(property.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(property.locationName, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("Ksh ${property.price}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                }
                                
                                if (property.tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        property.tags.take(3).forEach { tag ->
                                            SuggestionChip(
                                                onClick = { },
                                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                        if (property.tags.size > 3) {
                                            Text("+${property.tags.size - 3}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterVertically))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BentoCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier.height(160.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Column {
                Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = title, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HostDashboardPreview() {
    ModiTheme {
        HostDashboardScreen()
    }
}

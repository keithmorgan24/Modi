package com.keith.modi.screens.host

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

    if (showListWizard) {
        ListAirbnbScreen(
            onBack = { showListWizard = false },
            hostViewModel = hostViewModel
        )
    } else if (showBookingRequests) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Booking Requests") },
                    navigationIcon = {
                        IconButton(onClick = { showBookingRequests = false }) {

                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (pendingBookings.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No pending requests")
                        }
                    }
                } else {
                    items(pendingBookings, key = { it.id }) { booking ->
                        val property = myProperties.find { it.id == booking.propertyId }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = property?.title ?: "Unknown Property",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Guest ID: ${booking.guestId.take(8)}...", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { hostViewModel.approveBooking(booking.id) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Approve")
                                    }
                                    OutlinedButton(
                                        onClick = { hostViewModel.rejectBooking(booking.id) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Reject")
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

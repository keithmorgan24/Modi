package com.keith.modi.ui.screens.host

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keith.modi.models.HostListingState
import com.keith.modi.models.HostViewModel
import com.keith.modi.models.Property
import com.keith.modi.models.Booking
import com.keith.modi.ui.theme.ModiTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostDashboardScreen(hostViewModel: HostViewModel = viewModel()) {
    var showListWizard by remember { mutableStateOf(false) }
    var showStayManagement by remember { mutableStateOf(false) }
    
    var propertyToDelete by remember { mutableStateOf<String?>(null) }
    var propertyToEdit by remember { mutableStateOf<Property?>(null) }
    
    val myProperties by hostViewModel.myProperties.collectAsState()
    val allBookings by hostViewModel.pendingBookings.collectAsState()
    val earnings by hostViewModel.totalEarnings.collectAsState()
    val listingState by hostViewModel.listingState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = showListWizard || showStayManagement || propertyToEdit != null) {
        if (showListWizard) showListWizard = false
        else if (showStayManagement) showStayManagement = false
        else if (propertyToEdit != null) propertyToEdit = null
    }

    LaunchedEffect(listingState) {
        when (listingState) {
            is HostListingState.Success -> {
                val msg = (listingState as HostListingState.Success).message ?: "Success! ✨"
                snackbarHostState.showSnackbar(msg)
                hostViewModel.resetListingState()
            }
            is HostListingState.Error -> {
                snackbarHostState.showSnackbar((listingState as HostListingState.Error).message)
                hostViewModel.resetListingState()
            }
            else -> {}
        }
    }

    if (propertyToEdit != null) {
        ListAirbnbScreen(onBack = { propertyToEdit = null }, hostViewModel = hostViewModel, initialProperty = propertyToEdit)
    } else if (showListWizard) {
        ListAirbnbScreen(onBack = { showListWizard = false }, hostViewModel = hostViewModel)
    } else if (showStayManagement) {
        StayManagementScreen(
            bookings = allBookings,
            onBack = { showStayManagement = false },
            onApprove = { hostViewModel.approveBooking(it) },
            onReject = { hostViewModel.rejectBooking(it) },
            onArrived = { hostViewModel.markAsArrived(it) },
            onClearCategory = { hostViewModel.clearBookingsByCategory(it) },
            isLoading = listingState is HostListingState.Loading,
            snackbarHostState = snackbarHostState
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Host Dashboard", fontWeight = FontWeight.Black) },
                    actions = {
                        IconButton(onClick = { hostViewModel.fetchStats(); hostViewModel.fetchMyProperties() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showListWizard = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("List Airbnb") }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Text(text = "Welcome back!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BentoCard("Earnings", "Ksh ${String.format("%,.0f", earnings)}", Icons.Default.AttachMoney, Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer)
                        BentoCard("Listings", "${myProperties.size}", Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f), MaterialTheme.colorScheme.secondaryContainer)
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        onClick = { showStayManagement = true }
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ManageAccounts, null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(8.dp))
                                Text("Guest Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Manage arrivals and approvals here.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { showStayManagement = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                                Text("Open Manager")
                            }
                        }
                    }
                }

                item { Text("Your Properties", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

                items(myProperties, key = { it.id ?: "" }) { property ->
                    PropertyManagementCard(property, { propertyToEdit = property }, { propertyToDelete = property.id })
                }
            }
        }
    }

    if (propertyToDelete != null) {
        AlertDialog(
            onDismissRequest = { propertyToDelete = null },
            title = { Text("Delete Listing?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { hostViewModel.deleteProperty(propertyToDelete!!); propertyToDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { propertyToDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StayManagementScreen(
    bookings: List<Booking>,
    onBack: () -> Unit,
    onApprove: (Booking) -> Unit,
    onReject: (Booking) -> Unit,
    onArrived: (Booking) -> Unit,
    onClearCategory: (String) -> Unit,
    isLoading: Boolean,
    snackbarHostState: SnackbarHostState
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Pending", "Confirmed", "Arrived", "History")
    var showClearWarning by remember { mutableStateOf(false) }
    
    // PENDO: Collapsible state - Store which properties are expanded
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    val statusToClear = when (selectedTab) {
        0 -> "PENDING"
        1 -> "CONFIRMED"
        2 -> "ARRIVED"
        else -> "CANCELLED"
    }

    if (showClearWarning) {
        AlertDialog(
            onDismissRequest = { showClearWarning = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text("Security & Audit Risk", color = MaterialTheme.colorScheme.error)
                }
            },
            text = { 
                Text("Clearing this data will permanently remove guest records and history from this category. This may affect your earnings reports and tax audits. Proceed with extreme caution.") 
            },
            confirmButton = {
                Button(
                    onClick = { onClearCategory(statusToClear); showClearWarning = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Confirm Clear", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showClearWarning = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Stays & Arrivals", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    actions = {
                        val hasData = bookings.any { it.status == statusToClear }
                        if (hasData) {
                            IconButton(onClick = { showClearWarning = true }) {
                                Icon(Icons.Default.DeleteSweep, "Clear Category", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val filteredBookings = when (selectedTab) {
            0 -> bookings.filter { it.status == "PENDING" }
            1 -> bookings.filter { it.status == "CONFIRMED" }
            2 -> bookings.filter { it.status == "ARRIVED" }
            else -> bookings.filter { it.status == "CANCELLED" }
        }

        val groupedBookings = remember(filteredBookings) {
            filteredBookings.groupBy { it.property?.title ?: "Unknown Property" }.toSortedMap()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (filteredBookings.isEmpty()) {
                item { 
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No bookings in this category.", color = MaterialTheme.colorScheme.outline) 
                    }
                }
            }

            groupedBookings.forEach { (propertyName, propertyBookings) ->
                val isExpanded = expandedStates[propertyName] ?: true
                
                item(key = "header_$propertyName") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 8.dp, start = 8.dp)
                            .clickable { expandedStates[propertyName] = !isExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = propertyName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${propertyBookings.size}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                
                items(propertyBookings, key = { it.id ?: "" }) { booking ->
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        BookingManagementCard(booking, onApprove, onReject, onArrived, isLoading)
                    }
                }
            }
        }
    }
}

@Composable
fun BookingManagementCard(
    booking: Booking,
    onApprove: (Booking) -> Unit,
    onReject: (Booking) -> Unit,
    onArrived: (Booking) -> Unit,
    isLoading: Boolean
) {
    val guest = booking.guestProfile
    val property = booking.property
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Customer Info Box
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Text(guest?.fullName?.take(1) ?: "?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(guest?.fullName ?: "Unknown Guest", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(guest?.email ?: "No email provided", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Status: ${booking.status}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(formatBookingTime(booking.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (booking.status) {
                    "PENDING" -> {
                        Button(
                            onClick = { onApprove(booking) }, 
                            modifier = Modifier.weight(1f), 
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp)
                        ) { 
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Approve") 
                            }
                        }
                        OutlinedButton(
                            onClick = { onReject(booking) }, 
                            modifier = Modifier.weight(1f), 
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp)
                        ) { 
                            Text("Reject") 
                        }
                    }
                    "CONFIRMED" -> {
                        Button(
                            onClick = { onArrived(booking) }, 
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) { 
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.HowToReg, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Confirm Arrival") 
                            }
                        }
                    }
                    "ARRIVED" -> {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Guest has checked in", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatBookingTime(time: String?): String {
    if (time == null) return ""
    return try {
        val instant = Instant.parse(time.replace(" ", "T"))
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${dateTime.dayOfMonth}/${dateTime.monthNumber} ${dateTime.hour}:${dateTime.minute}"
    } catch (e: Exception) { "" }
}

@Composable
fun PropertyManagementCard(property: Property, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(property.title, fontWeight = FontWeight.Bold)
                    Text(property.locationName, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
fun BentoCard(title: String, value: String, icon: ImageVector, modifier: Modifier, containerColor: Color) {
    Card(modifier = modifier.height(120.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, modifier = Modifier.size(24.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

package com.keith.modi.ui.screens.host

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.keith.modi.ui.theme.ModiTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.keith.modi.models.HostListingState
import com.keith.modi.models.HostViewModel
import com.keith.modi.models.Property
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPropertiesScreen(hostViewModel: HostViewModel = viewModel()) {
    val myProperties by hostViewModel.myProperties.collectAsState()
    val listingState by hostViewModel.listingState.collectAsState()
    var propertyToDelete by remember { mutableStateOf<Property?>(null) }
    var propertyToEdit by remember { mutableStateOf<Property?>(null) }
    var showAddWizard by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var isRefreshing by remember { mutableStateOf(false) }

    if (showAddWizard) {
        ListAirbnbScreen(
            onBack = { showAddWizard = false },
            hostViewModel = hostViewModel
        )
    } else if (propertyToEdit != null) {
        ListAirbnbScreen(
            onBack = { propertyToEdit = null },
            hostViewModel = hostViewModel,
            initialProperty = propertyToEdit
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("My Listings", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                            Text("${myProperties.size} total properties", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        hostViewModel.fetchMyProperties()
                        isRefreshing = false
                    }
                },
                modifier = Modifier.padding(padding)
            ) {
                if (myProperties.isEmpty() && listingState !is HostListingState.Loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Icon(
                                    Icons.Default.Warning, 
                                    contentDescription = null, 
                                    modifier = Modifier.padding(20.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("You haven't listed any properties yet.", color = MaterialTheme.colorScheme.secondary)
                            TextButton(onClick = { showAddWizard = true }) {
                                Text("Create your first airbnb")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(myProperties, key = { it.id ?: "" }) { property ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                ManagedPropertyCard(
                                    property = property,
                                    hostViewModel = hostViewModel,
                                    onEdit = { propertyToEdit = property },
                                    onDelete = { propertyToDelete = property }
                                )
                            }
                        }
                    }
                }
            }

            if (propertyToDelete != null) {
                AlertDialog(
                    onDismissRequest = { propertyToDelete = null },
                    icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Delete Listing") },
                    text = { Text("Are you sure you want to delete '${propertyToDelete?.title}'? This action cannot be undone and will remove all associated data.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                propertyToDelete?.id?.let { hostViewModel.deleteProperty(it) }
                                scope.launch {
                                    snackbarHostState.showSnackbar("Listing deleted successfully")
                                }
                                propertyToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { propertyToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ManagedPropertyCard(
    property: Property,
    hostViewModel: HostViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                AsyncImage(
                    model = property.imageUrls.firstOrNull(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Gradient Overlay for readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                                startY = 300f
                            )
                        )
                )

                Surface(
                    modifier = Modifier.padding(16.dp).align(Alignment.TopEnd),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                ) {
                    Text(
                        "Ksh ${property.price}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                
                if (property.isFull) {
                    Surface(
                        modifier = Modifier.padding(16.dp).align(Alignment.BottomStart),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            "NO VACANCY",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        property.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MoreVert, 
                            contentDescription = null, 
                            modifier = Modifier.size(14.dp).padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            property.locationName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row {
                    FilledTonalIconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // PENDO: Modern Inventory Management UI
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Inventory Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            if (property.isFull) "Fully Occupied" else "${property.vacantRooms} Rooms Available",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(4.dp)
                    ) {
                        IconButton(
                            onClick = { 
                                val newOccupied = (property.occupiedRooms - 1).coerceAtLeast(0)
                                hostViewModel.updatePropertyOccupancy(property.id!!, newOccupied)
                            },
                            enabled = property.occupiedRooms > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, null, modifier = Modifier.size(18.dp))
                        }
                        
                        Text(
                            "${property.occupiedRooms}", 
                            modifier = Modifier.padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.ExtraBold
                        )
                        
                        IconButton(
                            onClick = { 
                                val newOccupied = (property.occupiedRooms + 1).coerceAtMost(property.totalRooms)
                                hostViewModel.updatePropertyOccupancy(property.id!!, newOccupied)
                            },
                            enabled = property.occupiedRooms < property.totalRooms,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MyPropertiesScreenPreview() {
    ModiTheme {
        MyPropertiesScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun ManagedPropertyCardPreview() {
    ModiTheme {
        ManagedPropertyCard(
            property = Property(
                id = "1",
                title = "Modern Loft",
                locationName = "Nairobi",
                price = 5000.0,
                hostId = "host1",
                imageUrls = listOf("https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80")
            ),
            hostViewModel = viewModel(),
            onEdit = {},
            onDelete = {}
        )
    }
}

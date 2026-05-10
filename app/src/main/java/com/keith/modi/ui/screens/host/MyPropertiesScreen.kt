package com.keith.modi.ui.screens.host

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.keith.modi.models.HostViewModel
import com.keith.modi.models.Property

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPropertiesScreen(hostViewModel: HostViewModel = viewModel()) {
    val myProperties by hostViewModel.myProperties.collectAsState()
    var propertyToDelete by remember { mutableStateOf<Property?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("My Listings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (myProperties.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("You haven't listed any properties yet.", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(myProperties) { property ->
                    ManagedPropertyCard(
                        property = property,
                        hostViewModel = hostViewModel,
                        onDelete = { propertyToDelete = property }
                    )
                }
            }
        }

        if (propertyToDelete != null) {
            AlertDialog(
                onDismissRequest = { propertyToDelete = null },
                title = { Text("Delete Listing") },
                text = { Text("Are you sure you want to delete '${propertyToDelete?.title}'? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            propertyToDelete?.id?.let { hostViewModel.deleteProperty(it) }
                            propertyToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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

@Composable
fun ManagedPropertyCard(
    property: Property,
    hostViewModel: HostViewModel,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                AsyncImage(
                    model = property.imageUrls.firstOrNull(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier.padding(12.dp).align(Alignment.TopEnd),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "Ksh ${property.price}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        property.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        property.locationName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row {
                    IconButton(onClick = { /* Edit */ }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // PENDO: Modern Inventory Management UI
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Occupancy Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(
                        if (property.isFull) "FULL - No Vacancy 🔴" else "${property.vacantRooms} of ${property.totalRooms} Rooms Available 🟢",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { 
                            val newOccupied = (property.occupiedRooms - 1).coerceAtLeast(0)
                            hostViewModel.updatePropertyOccupancy(property.id!!, newOccupied)
                        },
                        enabled = property.occupiedRooms > 0
                    ) {
                        Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Text("${property.occupiedRooms}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    IconButton(
                        onClick = { 
                            val newOccupied = (property.occupiedRooms + 1).coerceAtMost(property.totalRooms)
                            hostViewModel.updatePropertyOccupancy(property.id!!, newOccupied)
                        },
                        enabled = property.occupiedRooms < property.totalRooms
                    ) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

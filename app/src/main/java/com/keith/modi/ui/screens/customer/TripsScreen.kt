package com.keith.modi.screens.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keith.modi.models.Property
import com.keith.modi.ui.theme.ModiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen() {
    var showReviewSheet by remember { mutableStateOf(false) }
    var selectedPropertyForReview by remember { mutableStateOf<Property?>(null) }

    // Mock History Data
    val pastTrips = listOf(
        Property(
            id = "2",
            hostId = "h2",
            title = "City Center Studio",
            description = "Modern studio in Nairobi",
            price = 5000.0,
            locationName = "Nairobi, Kenya",
            latitude = -1.2,
            longitude = 36.8,
            imageUrls = listOf(""),
            distanceKm = 0.8,
            rating = 4.5
        ),
        Property(
            id = "3",
            hostId = "h3",
            title = "Mountain Retreat",
            description = "Cozy cabin in Aberdares",
            price = 8000.0,
            locationName = "Nyeri, Kenya",
            latitude = -0.4,
            longitude = 36.9,
            imageUrls = listOf(""),
            distanceKm = 45.0,
            rating = 4.9
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("My Trips", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        if (pastTrips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("No past trips yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(pastTrips) { property ->
                    TripCard(
                        property = property,
                        onReviewClick = {
                            selectedPropertyForReview = property
                            showReviewSheet = true
                        }
                    )
                }
            }
        }

        if (showReviewSheet && selectedPropertyForReview != null) {
            ModalBottomSheet(
                onDismissRequest = { showReviewSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ReviewSheetContent(
                    property = selectedPropertyForReview!!,
                    onReviewSubmitted = {
                        showReviewSheet = false
                    }
                )
            }
        }
    }
}

@Composable
fun TripCard(property: Property, onReviewClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder for Airbnb Image
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = property.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = property.locationName, style = MaterialTheme.typography.bodySmall)
                Text(text = "Completed", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = onReviewClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Review", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ReviewSheetContent(property: Property, onReviewSubmitted: () -> Unit) {
    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var photosCount by remember { mutableIntStateOf(0) }
    var errorMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Review your stay at", style = MaterialTheme.typography.bodyMedium)
        Text(text = property.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(24.dp))

        // Star Rating
        Row {
            repeat(5) { index ->
                IconButton(onClick = { rating = index + 1 }) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (index < rating) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("Share your experience...") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Photo Upload Logic (Simplified for UI)
        Text(
            text = "Integrity Check: Add 2 photos of the Airbnb",
            style = MaterialTheme.typography.labelMedium,
            color = if (photosCount < 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .clickable { photosCount++ },
                    contentAlignment = Alignment.Center
                ) {
                    if (index < photosCount) {
                        Text("Photo ${index + 1}", fontSize = 10.sp)
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (errorMsg.isNotEmpty()) {
            Text(text = errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (photosCount < 2) {
                    errorMsg = "Kindly add at least 2 photos to enable integrity of your review"
                } else {
                    onReviewSubmitted()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text("Submit Review", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun TripsScreenPreview() {
    ModiTheme {
        TripsScreen()
    }
}

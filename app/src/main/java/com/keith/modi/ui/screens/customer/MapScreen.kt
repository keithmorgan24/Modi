package com.keith.modi.ui.screens.customer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.keith.modi.models.Property
import kotlin.random.Random

import androidx.compose.ui.tooling.preview.Preview
import com.keith.modi.ui.theme.ModiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    properties: List<Property>,
    onBack: () -> Unit,
    onPropertyClick: (Property) -> Unit
) {
    val nairobi = LatLng(-1.286389, 36.817223)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(nairobi, 12f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explore Map", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            properties.forEach { property ->
                // Move calculation outside or use a key with remember
                val fuzzyLocation = remember(property.id) {
                    property.getFuzzyLocation()
                }
                
                Marker(
                    state = rememberMarkerState(position = fuzzyLocation),
                    title = property.title,
                    snippet = "Ksh ${property.price} / night",
                    onClick = {
                        onPropertyClick(property)
                        false
                    }
                )

                Circle(
                    center = fuzzyLocation,
                    radius = 500.0,
                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    strokeWidth = 2f
                )
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun MapScreenPreview() {
    ModiTheme {
        MapScreen(properties = emptyList(), onBack = {}, onPropertyClick = {})
    }
}

/**
 * Generates a fuzzy location within a 500m radius of the actual location.
 * Uses a simple approximation for latitude/longitude offset.
 */
fun Property.getFuzzyLocation(): LatLng {
    val lat = latitude ?: 0.0
    val lon = longitude ?: 0.0
    val random = Random(id.hashCode())
    val radiusInDegrees = 500.0 / 111320.0 // Approx 500m in degrees
    
    val u = random.nextDouble()
    val v = random.nextDouble()
    val w = radiusInDegrees * kotlin.math.sqrt(u)
    val t = 2 * kotlin.math.PI * v
    val x = w * kotlin.math.cos(t)
    val y = w * kotlin.math.sin(t)
    
    // Adjust y for latitude shrinkage
    val newX = x / kotlin.math.cos(Math.toRadians(lat))
    
    return LatLng(lat + y, lon + newX)
}

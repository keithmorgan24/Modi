package com.keith.modi.screens.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keith.modi.models.PropertyState
import com.keith.modi.models.PropertyViewModel
import com.keith.modi.ui.theme.ModiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedScreen(viewModel: PropertyViewModel = viewModel()) {
    val propertyState by viewModel.propertyState.collectAsState()

    val likedProperties = if (propertyState is PropertyState.Success) {
        (propertyState as PropertyState.Success).properties.filter { it.isLiked }
    } else {
        emptyList()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Liked Airbnbs", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        when (propertyState) {
            is PropertyState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PropertyState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text((propertyState as PropertyState.Error).message)
                }
            }
            is PropertyState.Success -> {
                val successState = propertyState as PropertyState.Success
                if (likedProperties.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No favorites yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(likedProperties) { property ->
                            AirbnbCard(
                                property = property,
                                reviews = successState.reviews[property.id] ?: emptyList(),
                                onClick = { /* TODO: Navigate to details */ },
                                onLikeClick = { property.id?.let { viewModel.toggleLike(it) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LikedScreenPreview() {
    ModiTheme {
        LikedScreen()
    }
}

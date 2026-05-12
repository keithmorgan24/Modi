package com.keith.modi.ui.screens.customer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.keith.modi.models.Property
import com.keith.modi.models.Review

import java.util.Locale

import androidx.compose.ui.graphics.Brush

import android.content.Intent
import androidx.compose.ui.platform.LocalContext

@Composable
fun AirbnbCard(
    property: Property,
    reviews: List<Review>,
    onClick: () -> Unit,
    onLikeClick: () -> Unit
) {
    val context = LocalContext.current
    val heartColor by animateColorAsState(
        targetValue = if (property.isLiked) Color.Red else Color.White.copy(alpha = 0.9f),
        animationSpec = tween(durationMillis = 300),
        label = "heartColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                AsyncImage(
                    model = property.imageUrls.firstOrNull(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Subtle Gradient for contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent, Color.Transparent),
                                startY = 0f,
                                endY = 200f
                            )
                        )
                )

                // Action Buttons Row
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Share Property Button
                    Surface(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                val shareText = "Check out this amazing stay on Modi: ${property.title} in ${property.locationName} for only Ksh ${property.price}/night! \n\nDownload Modi to book: https://modiapp.com/download"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Property"))
                        },
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Like Button
                    Surface(
                        onClick = onLikeClick,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.3f),
                        contentColor = heartColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (property.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
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

            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            property.title, 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            property.locationName, 
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    if (reviews.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                String.format(Locale.getDefault(), "%.1f", property.rating),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    "Ksh ${property.price} / night", 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.primary, 
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
fun ShimmerAirbnbCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color.LightGray.copy(alpha = 0.3f))
            )
            Column(Modifier.padding(16.dp)) {
                Box(Modifier.width(200.dp).height(24.dp).background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(120.dp).height(16.dp).background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(16.dp))
                Box(Modifier.width(100.dp).height(28.dp).background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
            }
        }
    }
}

package com.keith.modi.screens.customer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.keith.modi.models.Booking
import com.keith.modi.models.BookingState
import com.keith.modi.models.BookingViewModel
import com.keith.modi.models.Property
import com.keith.modi.ui.theme.ModiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(bookingViewModel: BookingViewModel = viewModel()) {
    var showReviewSheet by remember { mutableStateOf(false) }
    var selectedBooking by remember { mutableStateOf<Booking?>(null) }
    
    val bookingState by bookingViewModel.bookingState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("My Trips", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp) }
            )
        }
    ) { innerPadding ->
        when (val state = bookingState) {
            is BookingState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                }
            }
            is BookingState.Error -> {
                // PENDO SECURITY: Shield the user from technical leaks (Tokens/URLs) seen in the screenshot
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("!", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Connection issue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "We couldn't load your trips. This is usually due to a weak network signal.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { bookingViewModel.fetchUserBookings() },
                            modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Retry Connection")
                        }
                    }
                }
            }
            is BookingState.Success -> {
                if (state.bookings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No past trips yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.bookings) { booking ->
                            booking.property?.let { property ->
                                TripCard(
                                    property = property,
                                    status = booking.status,
                                    onReviewClick = {
                                        selectedBooking = booking
                                        showReviewSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showReviewSheet && selectedBooking != null) {
            ModalBottomSheet(
                onDismissRequest = { showReviewSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ReviewSheetContent(
                    booking = selectedBooking!!,
                    viewModel = bookingViewModel,
                    onReviewSubmitted = {
                        showReviewSheet = false
                    }
                )
            }
        }
    }
}

@Composable
fun TripCard(property: Property, status: String, onReviewClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(90.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                AsyncImage(
                    model = property.imageUrls.firstOrNull(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = property.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = property.locationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val (statusColor, statusBg) = when(status.uppercase()) {
                    "CONFIRMED" -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
                    "PENDING" -> Color(0xFFEF6C00) to Color(0xFFFFF3E0)
                    else -> Color(0xFFC62828) to Color(0xFFFFEBEE)
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = statusColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            if (status.uppercase() == "CONFIRMED") {
                IconButton(
                    onClick = onReviewClick,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Icon(Icons.Default.Star, contentDescription = "Review", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheetContent(
    booking: Booking, 
    viewModel: BookingViewModel, 
    onReviewSubmitted: () -> Unit
) {
    val property = booking.property ?: return
    val context = LocalContext.current
    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var selectedImageUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 2),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                selectedImageUris = uris
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.width(40.dp).height(4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = CircleShape
        ) {}
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Review your stay at", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = property.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        
        Spacer(modifier = Modifier.height(24.dp))

        // PENDO: Modern Star Rating with Haptic feedback
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { index ->
                val isSelected = index < rating
                IconButton(
                    onClick = { 
                        rating = index + 1
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    enabled = !isSubmitting
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = if (isSelected) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            placeholder = { Text("How was the neighborhood? Any tips for future guests?") },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isSubmitting,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // PENDO: Professional Integrity Check UI
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Integrity Check",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Upload 2 photos for a verified badge",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            val photosCount = selectedImageUris.size
            Text(
                text = "$photosCount/2",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (photosCount >= 2) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { index ->
                val uri = selectedImageUris.getOrNull(index)
                Surface(
                    modifier = Modifier
                        .size(100.dp)
                        .weight(1f)
                        .clickable(enabled = !isSubmitting) { 
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (uri != null) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (uri != null) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (errorMsg.isNotEmpty()) {
            Text(
                text = errorMsg, 
                color = MaterialTheme.colorScheme.error, 
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (rating == 0) {
                    errorMsg = "Please select a star rating"
                } else if (selectedImageUris.size < 2) {
                    errorMsg = "Integrity check: Add 2 photos to verify your review"
                } else {
                    isSubmitting = true
                    errorMsg = ""
                    viewModel.submitReview(
                        context = context,
                        booking = booking,
                        rating = rating,
                        comment = comment,
                        imageUris = selectedImageUris,
                        onSuccess = {
                            isSubmitting = false
                            onReviewSubmitted()
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Submit Verified Review", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TripsScreenPreview() {
    ModiTheme {
        TripsScreen()
    }
}

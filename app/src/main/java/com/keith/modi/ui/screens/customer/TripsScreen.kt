package com.keith.modi.ui.screens.customer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
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
import coil.request.ImageRequest
import com.keith.modi.models.Booking
import com.keith.modi.models.BookingState
import com.keith.modi.models.BookingViewModel
import com.keith.modi.models.MainViewModel
import com.keith.modi.ui.theme.ModiTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    bookingViewModel: BookingViewModel = viewModel()
) {
    var showReviewSheet by remember { mutableStateOf(false) }
    var selectedBooking by remember { mutableStateOf<Booking?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    
    val bookingState by bookingViewModel.bookingState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text("My Trips", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp) 
                },
                actions = {
                    val hasTrips = (bookingState as? BookingState.Success)?.bookings?.isNotEmpty() ?: false
                    if (hasTrips) {
                        IconButton(onClick = { showClearHistoryDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Clear All", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (showClearHistoryDialog) {
                AlertDialog(
                    onDismissRequest = { showClearHistoryDialog = false },
                    title = { Text("Clear Trip History?") },
                    text = { Text("This will permanently remove your past trip records. Active bookings are kept for security.") },
                    confirmButton = {
                        Button(
                            onClick = { bookingViewModel.clearTripHistory(); showClearHistoryDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Clear History", color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
                    },
                    shape = RoundedCornerShape(24.dp)
                )
            }

            when (val state = bookingState) {
                is BookingState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is BookingState.Error -> {
                    SyncErrorView(message = state.message) { bookingViewModel.fetchUserBookings() }
                }
                is BookingState.Success -> {
                    if (state.bookings.isEmpty()) {
                        EmptyTripsView()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(state.bookings) { booking ->
                                TripCard(
                                    booking = booking,
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
                    onReviewSubmitted = { showReviewSheet = false }
                )
            }
        }
    }
}

@Composable
fun SyncErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Sync issue detected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Surface(
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
        ) {
            // SECURITY: Only show first 100 chars to avoid leaking sensitive header data in screenshot
            Text(
                text = message.take(150) + if(message.length > 150) "..." else "",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        
        Button(onClick = onRetry, modifier = Modifier.padding(top = 32.dp)) {
            Text("Retry Connection")
        }
    }
}

@Composable
fun TripCard(booking: Booking, onReviewClick: () -> Unit) {
    val property = booking.property
    val context = LocalContext.current
    
    val formattedDate = remember(booking.expiresAt) {
        try {
            val dateStr = booking.expiresAt ?: return@remember "Date Unverified"
            val instant = if (dateStr.all { it.isDigit() }) {
                Instant.fromEpochMilliseconds(dateStr.toLong())
            } else {
                Instant.parse(dateStr.replace(" ", "T"))
            }
            val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            val month = dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }
            "${dateTime.dayOfMonth} $month, ${dateTime.year}"
        } catch (e: Exception) { "Date Unverified" }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(110.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (property?.imageUrls?.isNotEmpty() == true) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(property.imageUrls.first())
                                .crossfade(true)
                                .build(),
                            contentDescription = property.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.HomeWork, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = property?.title ?: "Verified Property",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 26.sp
                    )
                    
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = property?.locationName ?: "Nairobi, Kenya",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Investment", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = "Ksh ${String.format("%,.0f", booking.feePaid ?: 0.0)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val (color, bg) = when(booking.status?.uppercase()) {
                    "CONFIRMED" -> Color(0xFF2E7D32) to Color(0xFFE8F5E9).copy(alpha = 0.15f)
                    "PENDING" -> Color(0xFFEF6C00) to Color(0xFFFFF3E0).copy(alpha = 0.15f)
                    else -> Color(0xFFC62828) to Color(0xFFFFEBEE).copy(alpha = 0.15f)
                }
                
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = bg,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = booking.status ?: "PENDING",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = color,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            if (booking.status?.uppercase() == "CONFIRMED") {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onReviewClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify & Review", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EmptyTripsView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.History, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Your Travel Log is Empty", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(
            "Book a premium stay to build your travel collection.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheetContent(booking: Booking, viewModel: BookingViewModel, onReviewSubmitted: () -> Unit) {
    val property = booking.property
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(modifier = Modifier.width(40.dp).height(4.dp), color = MaterialTheme.colorScheme.outlineVariant, shape = CircleShape) {}
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Review your stay at", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = property?.title ?: "Verified Property", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { index ->
                IconButton(onClick = { rating = index + 1; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }, enabled = !isSubmitting) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(40.dp), tint = if (index < rating) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = comment, onValueChange = { comment = it }, placeholder = { Text("How was your stay? Be honest and helpful!") },
            modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(16.dp), enabled = !isSubmitting
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { index ->
                val uri = selectedImageUris.getOrNull(index)
                Surface(
                    modifier = Modifier.size(100.dp).weight(1f).clickable(enabled = !isSubmitting) { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (uri != null) AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.AddAPhoto, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        if (errorMsg.isNotEmpty()) Text(text = errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Button(
            onClick = {
                if (rating == 0) errorMsg = "Please select a star rating"
                else if (selectedImageUris.size < 2) errorMsg = "Integrity Check: 2 photos required"
                else {
                    isSubmitting = true; errorMsg = ""
                    viewModel.submitReview(context, booking, rating, comment, selectedImageUris) { isSubmitting = false; onReviewSubmitted() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !isSubmitting, shape = RoundedCornerShape(16.dp)
        ) {
            if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text("Submit Verified Review", fontWeight = FontWeight.ExtraBold)
        }
    }
}

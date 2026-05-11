package com.keith.modi.ui.screens.host

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.keith.modi.models.HostListingState
import com.keith.modi.models.HostViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListAirbnbScreen(
    onBack: () -> Unit,
    hostViewModel: HostViewModel = viewModel()
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 9
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Form State
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var totalRooms by remember { mutableStateOf("1") }
    var locationName by remember { mutableStateOf("") }
    var selectedCategories by remember { mutableStateOf(setOf<String>()) }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var latitude by remember { mutableDoubleStateOf(-1.286389) }
    var longitude by remember { mutableDoubleStateOf(36.817223) }
    var isLocationCaptured by remember { mutableStateOf(false) }
    
    val listingState by hostViewModel.listingState.collectAsState()
    var showSuccessDialog by remember { mutableStateOf(false) }
    val categories = listOf("Nearby", "Beachfront", "Pool", "Luxury", "Modern", "WiFi", "Central", "Cabins")

    LaunchedEffect(listingState) {
        when (listingState) {
            is HostListingState.Success -> {
                showSuccessDialog = true
            }
            is HostListingState.Error -> {
                snackbarHostState.showSnackbar((listingState as HostListingState.Error).message)
            }
            else -> {}
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { /* Force explicit action */ },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFF4CAF50)) },
            title = { Text("Property Published! 🚀", fontWeight = FontWeight.ExtraBold) },
            text = { Text("Congratulations! Your Airbnb '$name' is now live and visible to guests worldwide on Modi. Get ready for bookings! ✨", textAlign = TextAlign.Center) },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        hostViewModel.resetListingState()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Awesome! Take me to Dashboard", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    if (currentStep > 0) {
                        Text("Step $currentStep of $totalSteps", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text("List Your Property", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (currentStep > 0) currentStep-- else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Step Progress Bar
            if (currentStep > 0) {
                LinearProgressIndicator(
                    progress = { currentStep.toFloat() / totalSteps.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                        } else {
                            slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                        }.using(SizeTransform(clip = false))
                    },
                    label = "stepTransition"
                ) { step ->
                    when (step) {
                        0 -> WelcomeStep { currentStep = 1 }
                        1 -> SimpleInputStep(
                            title = "What's the name of your beautiful space? 🏠",
                            description = "Guests usually look for catchy titles like 'Serene Garden Cottage'.",
                            value = name,
                            onValueChange = { name = it },
                            placeholder = "Enter property title..."
                        )
                        2 -> MultiCategoryStep(
                            selectedCategories = selectedCategories,
                            onCategoryToggle = { cat ->
                                selectedCategories = if (selectedCategories.contains(cat)) {
                                    selectedCategories - cat
                                } else {
                                    selectedCategories + cat
                                }
                            },
                            categories = categories
                        )
                        3 -> LocationCaptureStep(
                            isCaptured = isLocationCaptured,
                            onLocationCaptured = { lat, lon ->
                                latitude = lat
                                longitude = lon
                                isLocationCaptured = true
                                scope.launch {
                                    snackbarHostState.showSnackbar("Location verified! 📍")
                                }
                            },
                            onReset = { isLocationCaptured = false }
                        )
                        4 -> SimpleInputStep(
                            title = "Where exactly is this gem located? 🏙️",
                            description = "Specify the area name (e.g., Westlands, Nairobi).",
                            value = locationName,
                            onValueChange = { locationName = it },
                            placeholder = "Area or Neighborhood..."
                        )
                        5 -> SimpleInputStep(
                            title = "How many rooms are in this space? 🛌",
                            description = "This helps manage occupancy and prevent double-booking.",
                            value = totalRooms,
                            onValueChange = { totalRooms = it },
                            placeholder = "Number of rooms",
                            keyboardType = KeyboardType.Number
                        )
                        6 -> SimpleInputStep(
                            title = "How much should guests pay per night? 💰",
                            description = "Competitive prices attract more bookings!",
                            value = price,
                            onValueChange = { price = it },
                            placeholder = "Price in Ksh",
                            keyboardType = KeyboardType.Number
                        )
                        7 -> ListingPhotosStep(
                            selectedImages = selectedImages,
                            onImagesSelected = { selectedImages = selectedImages + it },
                            onRemoveImage = { selectedImages = selectedImages - it }
                        )
                        8 -> SimpleInputStep(
                            title = "Tell us more about your space ✨",
                            description = "Mention unique features like 'Private terrace with sunset view'.",
                            value = description,
                            onValueChange = { description = it },
                            placeholder = "Describe your place...",
                            isSingleLine = false
                        )
                        9 -> ListingReviewStep(
                            name = name,
                            location = locationName,
                            price = price,
                            description = description,
                            images = selectedImages,
                            listingState = listingState
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentStep > 0) {
                Button(
                    onClick = {
                        val validationError = getValidationError(currentStep, name, locationName, price, totalRooms, selectedImages, description, selectedCategories, isLocationCaptured)
                        if (validationError == null) {
                            if (currentStep < totalSteps) {
                                currentStep++
                            } else {
                                val priceVal = price.toDoubleOrNull() ?: 0.0
                                val roomsVal = totalRooms.toIntOrNull() ?: 1
                                hostViewModel.createListing(context, name, description, priceVal, locationName, selectedCategories.toList(), selectedImages, latitude, longitude, roomsVal)
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(validationError)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = listingState !is HostListingState.Loading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (getValidationError(currentStep, name, locationName, price, totalRooms, selectedImages, description, selectedCategories, isLocationCaptured) == null)
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.outline
                    )
                ) {
                    if (listingState is HostListingState.Loading) {
                        var loadingMsg by remember { mutableStateOf("Publishing...") }
                        LaunchedEffect(Unit) {
                            val messages = listOf("Uploading photos...", "Analyzing AI tags...", "Securing details...", "Almost there...")
                            var i = 0
                            while(true) {
                                loadingMsg = messages[i % messages.size]
                                delay(1500)
                                i++
                            }
                        }
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(loadingMsg, fontWeight = FontWeight.Bold)
                    } else {
                        Text(if (currentStep < totalSteps) "Continue ➡️" else "Publish My Airbnb 🚀", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

private fun getValidationError(step: Int, name: String, location: String, price: String, totalRooms: String, images: List<Uri>, desc: String, categories: Set<String>, locationCaptured: Boolean): String? {
    return when(step) {
        1 -> if (name.isBlank()) "Kindly fill out the property name ✨" else null
        2 -> if (categories.isEmpty()) "Please pick at least one category 🌟" else null
        3 -> if (!locationCaptured) "We recommend capturing your location for better visibility 📍" else null
        4 -> if (location.isBlank()) "Kindly specify the location area 🏙️" else null
        5 -> if (totalRooms.isBlank() || (totalRooms.toIntOrNull() ?: 0) < 1) "Please enter a valid number of rooms 🛌" else null
        6 -> if (price.isBlank()) "Please set a price for your space 💰" else null
        7 -> if (images.isEmpty()) "At least one photo is required 📸" else null
        8 -> if (desc.isBlank()) "Tell us a bit about your space ✨" else null
        else -> null
    }
}

@Composable
fun WelcomeStep(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ready to become a host? 🌟", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "We'll guide you through listing your space in a few simple steps. It's easy, and we're here to help!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Let's Get Started! 🚀", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SimpleInputStep(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isSingleLine: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth().then(if(!isSingleLine) Modifier.height(200.dp) else Modifier),
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = isSingleLine,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
fun MultiCategoryStep(
    selectedCategories: Set<String>,
    onCategoryToggle: (String) -> Unit,
    categories: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text("What categories best describe your place? 🌟", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("You can select multiple! This helps us show your place to the right guests.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)

        Spacer(modifier = Modifier.height(32.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.chunked(2).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { cat ->
                        val isSelected = selectedCategories.contains(cat)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .clickable { onCategoryToggle(cat) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(cat, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        }
                    }
                    if(rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun LocationCaptureStep(
    isCaptured: Boolean,
    onLocationCaptured: (Double, Double) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var isCapturing by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var capturedAddress by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Internal function to perform actual fetch
    val performFetch = {
        isCapturing = true
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            withContext(Dispatchers.Main) {
                                capturedAddress = addresses?.firstOrNull()?.getAddressLine(0) ?: "Lat: ${location.latitude}, Lon: ${location.longitude}"
                                onLocationCaptured(location.latitude, location.longitude)
                                isCapturing = false
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                capturedAddress = "Lat: ${location.latitude}, Lon: ${location.longitude}"
                                onLocationCaptured(location.latitude, location.longitude)
                                isCapturing = false
                            }
                        }
                    }
                } else {
                    // Fallback to last location
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        isCapturing = false
                        if (lastLoc != null) {
                            onLocationCaptured(lastLoc.latitude, lastLoc.longitude)
                        } else {
                            Toast.makeText(context, "Unable to find location. Please ensure GPS is ON.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .addOnFailureListener {
                isCapturing = false
                Toast.makeText(context, "Location capture failed. Check permissions.", Toast.LENGTH_LONG).show()
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) performFetch()
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Location Permission 📍") },
            text = { Text("Modi needs location access to pinpoint your Airbnb for guests. We protect your data and only use it for this listing.") },
            confirmButton = {
                Button(onClick = {
                    showRationale = false
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }) { Text("Allow") }
            },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Pinpoint your place! 📍", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(40.dp))

        if (isCaptured) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Captured!", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 18.sp)
                    Text(text = capturedAddress ?: "Location Secured", textAlign = TextAlign.Center, color = Color(0xFF2E7D32).copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onReset) { Text("Retake 🔄") }
                }
            }
        } else {
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        performFetch()
                    } else {
                        showRationale = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isCapturing
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Locating... 🛰️")
                } else {
                    Text("Capture My Location 📍", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ListingPhotosStep(
    selectedImages: List<Uri>,
    onImagesSelected: (List<Uri>) -> Unit,
    onRemoveImage: (Uri) -> Unit
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> onImagesSelected(uris) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Show off your space! 📸", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Photos are the first thing guests see. Bright, clear photos get more bookings! ✨", color = MaterialTheme.colorScheme.secondary)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { imagePickerLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Tap to add your best photos", fontWeight = FontWeight.Bold)
            }
        }

        if (selectedImages.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                items(selectedImages) { uri ->
                    Box(modifier = Modifier.size(120.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onRemoveImage(uri) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListingReviewStep(
    name: String,
    location: String,
    price: String,
    description: String,
    images: List<Uri>,
    listingState: HostListingState
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Almost there! Review your masterpiece 🚀", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(location, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Ksh $price / night", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Text("Summary ✨", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Gallery 📸", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(images) { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.size(150.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (listingState is HostListingState.Loading) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Pendo AI is analyzing your listing and generating SEO tags for maximum visibility...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

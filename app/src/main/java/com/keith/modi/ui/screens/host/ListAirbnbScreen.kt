package com.keith.modi.screens.host

import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.keith.modi.models.HostListingState
import com.keith.modi.models.HostViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListAirbnbScreen(
    onBack: () -> Unit,
    hostViewModel: HostViewModel = viewModel()
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    
    val listingState by hostViewModel.listingState.collectAsState()
    val context = LocalContext.current

    var category by remember { mutableStateOf("Nearby") }
    val categories = listOf("Nearby", "Beachfront", "Pool", "Luxury", "Modern", "WiFi", "Central", "Cabins")

    LaunchedEffect(listingState) {
        if (listingState is HostListingState.Success) {
            hostViewModel.resetListingState()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("List Your Property", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (currentStep > 1) currentStep-- else onBack() }) {
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
            // Step Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { index ->
                    val step = index + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (step <= currentStep) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

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
                        1 -> ListingDetailsStep(
                            name = name, onNameChange = { name = it },
                            location = location, onLocationChange = { location = it },
                            price = price, onPriceChange = { price = it },
                            description = description, onDescriptionChange = { description = it },
                            category = category, onCategoryChange = { category = it },
                            categories = categories
                        )
                        2 -> ListingPhotosStep(
                            selectedImages = selectedImages,
                            onImagesSelected = { selectedImages = selectedImages + it },
                            onRemoveImage = { selectedImages = selectedImages - it }
                        )
                        3 -> ListingReviewStep(
                            name = name,
                            location = location,
                            price = price,
                            description = description,
                            images = selectedImages,
                            listingState = listingState
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (listingState is HostListingState.Error) {
                Text(
                    text = (listingState as HostListingState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (currentStep < 3) {
                        currentStep++
                    } else {
                        val priceVal = price.toDoubleOrNull() ?: 0.0
                        hostViewModel.createListing(context, name, description, priceVal, location, category, selectedImages)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = when(currentStep) {
                    1 -> name.isNotBlank() && location.isNotBlank() && price.isNotBlank()
                    2 -> selectedImages.isNotEmpty()
                    else -> listingState !is HostListingState.Loading
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (listingState is HostListingState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("AI Processing...")
                } else {
                    Text(if (currentStep < 3) "Next" else "Publish Listing", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ListingDetailsStep(
    name: String, onNameChange: (String) -> Unit,
    location: String, onLocationChange: (String) -> Unit,
    price: String, onPriceChange: (String) -> Unit,
    description: String, onDescriptionChange: (String) -> Unit,
    category: String, onCategoryChange: (String) -> Unit,
    categories: List<String>
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Property Details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Tell us about your space. High-end descriptions attract premium guests.", color = MaterialTheme.colorScheme.secondary)

        Text("Category", fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { onCategoryChange(cat) },
                    label = { Text(cat) }
                )
            }
        }

        OutlinedTextField(
            value = name, onValueChange = onNameChange,
            label = { Text("Property Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = location, onValueChange = onLocationChange,
            label = { Text("Location (e.g. Westlands, Nairobi)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = price, onValueChange = onPriceChange,
            label = { Text("Price per night (Ksh)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = description, onValueChange = onDescriptionChange,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            shape = RoundedCornerShape(12.dp)
        )
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
        Text("Photos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Upload at least one high-quality photo. Our AI will automatically tag your property based on these images.", color = MaterialTheme.colorScheme.secondary)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { imagePickerLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tap to add photos", fontWeight = FontWeight.Medium)
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(selectedImages) { uri ->
                Box(modifier = Modifier.size(100.dp)) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { onRemoveImage(uri) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
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
        Text("Review Listing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(location, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ksh $price / night", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Text("Description Preview", fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium)

        Text("Images (${images.size})", fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(images) { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (listingState is HostListingState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Our AI is currently analyzing your images to generate smart tags...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

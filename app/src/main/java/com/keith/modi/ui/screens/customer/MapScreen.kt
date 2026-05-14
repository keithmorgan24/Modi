package com.keith.modi.ui.screens.customer

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.keith.modi.models.Property
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    properties: List<Property>,
    onBack: () -> Unit,
    onPropertyClick: (Property) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // PENDO: Persistent Centering Control
    var isMapInitialized by remember { mutableStateOf(false) }
    var lastPropertyCount by remember { mutableIntStateOf(0) }
    var isFollowingUser by remember { mutableStateOf(false) }

    // PENDO: High-Security Map Configuration - Fixed Early Initialization
    // We initialize the config BEFORE creating the MapView to prevent glitches
    remember {
        with(Configuration.getInstance()) {
            userAgentValue = "${context.packageName} (Modi-Secure-OSM-v3)"
            load(context, context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
            
            val cacheDir = File(context.cacheDir, "osm_tiles_secure")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            osmdroidTileCache = cacheDir
            tileDownloadThreads = 8.toShort()
            tileFileSystemCacheMaxBytes = 750L * 1024 * 1024
        }
        true
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    // Modern State Management
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            
            // PENDO: Optimized Touch Routing for Compose
            // This ensures the map handles touches before any parent Pagers or Listeners
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            setScrollableAreaLimitDouble(null)
            
            val rotationGestureOverlay = RotationGestureOverlay(this)
            rotationGestureOverlay.isEnabled = true
            overlays.add(rotationGestureOverlay)
        }
    }

    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            setDrawAccuracyEnabled(true)
        }
    }

    // PENDO: Intelligent Follow-Mode Management
    // Stops following the user if they manually pan the map
    LaunchedEffect(mapView) {
        mapView.setOnTouchListener { _, _ ->
            if (isFollowingUser) {
                locationOverlay.disableFollowLocation()
                isFollowingUser = false
            }
            false
        }
    }

    // PENDO: Reactive Permission Handling
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            if (!mapView.overlays.contains(locationOverlay)) {
                mapView.overlays.add(locationOverlay)
            }
            locationOverlay.enableMyLocation()
        } else {
            mapView.overlays.remove(locationOverlay)
            locationOverlay.disableMyLocation()
        }
        mapView.invalidate()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                }
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { 
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    val mapProperties = remember(properties) {
        properties.filter { it.latitude != null && it.longitude != null && (it.latitude != 0.0 || it.longitude != 0.0) }
    }

    // PENDO: Intelligent Auto-Centering Logic
    LaunchedEffect(mapProperties) {
        if (mapProperties.isNotEmpty()) {
            // Only auto-center if it's the first initialization OR the property set has changed significantly
            if (!isMapInitialized || mapProperties.size != lastPropertyCount) {
                delay(500)
                if (mapProperties.size == 1) {
                    val first = mapProperties.first()
                    mapView.controller.animateTo(GeoPoint(first.latitude!!, first.longitude!!), 15.0, 1000L)
                } else {
                    // Fit all markers in view
                    val points = mapProperties.map { p -> 
                        val fuzzy = p.getFuzzyLocation()
                        GeoPoint(fuzzy.latitude, fuzzy.longitude) 
                    }
                    val bbox = BoundingBox.fromGeoPoints(points)
                    mapView.zoomToBoundingBox(bbox.increaseByScale(1.2f), true)
                }
                isMapInitialized = true
                lastPropertyCount = mapProperties.size
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Explore Map", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        Text("${mapProperties.size} premium spaces nearby", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView },
                update = { view ->
                    // Performance: Smart Diffing for Markers using relatedObject
                    val currentMarkers = view.overlays.filterIsInstance<Marker>()
                    val currentMarkerIds = currentMarkers.mapNotNull { it.relatedObject as? String }.toSet()
                    val dataPropertyIds = mapProperties.mapNotNull { it.id }.toSet()

                    if (currentMarkerIds != dataPropertyIds) {
                        view.overlays.removeAll { it is Marker }
                        
                        mapProperties.forEach { property ->
                            val fuzzy = property.getFuzzyLocation()
                            val marker = Marker(view)
                            marker.relatedObject = property.id // PENDO: Safe ID storage
                            marker.position = GeoPoint(fuzzy.latitude, fuzzy.longitude)
                            marker.title = property.title
                            marker.snippet = "Ksh ${property.price}"
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            
                            marker.setOnMarkerClickListener { m, _ ->
                                onPropertyClick(property)
                                m.showInfoWindow()
                                true
                            }
                            view.overlays.add(marker)
                        }
                        view.invalidate()
                    }
                }
            )

    // MODERN FLOATING UI CONTROLS
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Locate Me Button - Improved Fix for "Not working as expected"
        FloatingActionButton(
            onClick = {
                if (hasLocationPermission) {
                    val myLoc = locationOverlay.myLocation
                    if (myLoc != null) {
                        mapView.controller.animateTo(myLoc, 17.5, 1000L)
                        locationOverlay.enableFollowLocation()
                        isFollowingUser = true
                    } else {
                        // Intelligent Lock: Enable and wait for first fix
                        val success = locationOverlay.enableMyLocation()
                        if (success) {
                            Toast.makeText(context, "Acquiring GPS fix... 📡", Toast.LENGTH_SHORT).show()
                            locationOverlay.runOnFirstFix {
                                mapView.post {
                                    val loc = locationOverlay.myLocation
                                    if (loc != null) {
                                        mapView.controller.animateTo(loc, 17.5, 1000L)
                                        locationOverlay.enableFollowLocation()
                                        isFollowingUser = true
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, "Location services are disabled", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            containerColor = if (isFollowingUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (hasLocationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            shape = CircleShape,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Locate Me",
                tint = if (isFollowingUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

                // Custom Zoom Controls
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column {
                        IconButton(onClick = { mapView.controller.zoomIn() }) {
                            Icon(Icons.Default.Add, "Zoom In", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(modifier = Modifier.width(24.dp).align(Alignment.CenterHorizontally), thickness = 0.5.dp)
                        IconButton(onClick = { mapView.controller.zoomOut() }) {
                            Icon(Icons.Default.Remove, "Zoom Out", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            if (mapProperties.isEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "No properties found in this area",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

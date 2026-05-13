package com.keith.modi.ui.screens.customer

import android.Manifest
import android.content.pm.PackageManager
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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

    // PENDO: Cyber Security & Performance Config
    LaunchedEffect(Unit) {
        with(Configuration.getInstance()) {
            userAgentValue = "${context.packageName} (Modi-Secure-OSM-v2)"
            val cacheDir = File(context.cacheDir, "osm_tiles_secure")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            osmdroidTileCache = cacheDir
            tileDownloadThreads = 8.toShort()
            tileFileSystemCacheMaxBytes = 750L * 1024 * 1024
        }
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
            controller.setZoom(15.0)
            
            val rotationGestureOverlay = RotationGestureOverlay(this)
            rotationGestureOverlay.isEnabled = true
            overlays.add(rotationGestureOverlay)
            
            setHasTransientState(true)
        }
    }

    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            setDrawAccuracyEnabled(true)
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

    LaunchedEffect(mapProperties) {
        if (mapProperties.isNotEmpty()) {
            val first = mapProperties.first()
            mapView.controller.animateTo(GeoPoint(first.latitude!!, first.longitude!!), 15.0, 1500L)
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
                    // Performance: Only update markers if data changed
                    val currentMarkers = view.overlays.filterIsInstance<Marker>()
                    if (currentMarkers.size != mapProperties.size) {
                        view.overlays.removeAll { it is Marker }
                        
                        mapProperties.forEach { property ->
                            val fuzzy = property.getFuzzyLocation()
                            val marker = Marker(view)
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
                // Locate Me Button - Now with intelligent permission triggering
                FloatingActionButton(
                    onClick = {
                        if (hasLocationPermission) {
                            val myLoc = locationOverlay.myLocation
                            if (myLoc != null) {
                                mapView.controller.animateTo(myLoc, 16.5, 1000L)
                            } else {
                                // Provide haptic feedback or toast if GPS is still locking
                                mapView.controller.animateTo(GeoPoint(-1.286389, 36.817223), 15.0, 1000L)
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = if (hasLocationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.MyLocation, "Locate Me")
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

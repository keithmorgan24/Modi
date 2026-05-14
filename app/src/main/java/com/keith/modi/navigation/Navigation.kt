package com.keith.modi.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.keith.modi.models.AuthViewModel
import com.keith.modi.models.MainViewModel
import com.keith.modi.models.UserRole
import com.keith.modi.ui.screens.common.ProfileScreen
import com.keith.modi.ui.screens.customer.ExploreScreen
import com.keith.modi.ui.screens.customer.LikedScreen
import com.keith.modi.ui.screens.customer.TripsScreen
import com.keith.modi.ui.screens.host.HostDashboardScreen
import com.keith.modi.ui.screens.host.MyPropertiesScreen
import kotlinx.coroutines.launch

sealed class Screen(val title: String, val icon: ImageVector) {
    object Explore : Screen("Explore", Icons.Default.Search)
    object Liked : Screen("Liked", Icons.Default.Favorite)
    object Trips : Screen("Trips", Icons.Default.History)
    object Dashboard : Screen("Dashboard", Icons.Default.Dashboard)
    object MyAirbnbs : Screen("My Airbnbs", Icons.AutoMirrored.Filled.List)
    object Profile : Screen("Profile", Icons.Default.AccountCircle)
}

@Composable
fun MainScaffold(
    mainViewModel: MainViewModel,
    authViewModel: AuthViewModel,
    onNavigate: (String) -> Unit
) {
    val currentRole by mainViewModel.currentRole.collectAsState()
    val isGuest by mainViewModel.isGuest.collectAsState()
    
    val customerItems = listOf(Screen.Explore, Screen.Liked, Screen.Trips, Screen.Profile)
    val hostItems = listOf(Screen.Dashboard, Screen.MyAirbnbs, Screen.Profile)
    
    val items = when {
        isGuest -> customerItems
        currentRole == UserRole.HOST -> hostItems
        else -> customerItems
    }
    
    val pagerState = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val isOnline by mainViewModel.isOnline.collectAsState()

    // PENDO: Intelligent Pager Blocking - Prevent swiping when Map is active
    var isMapActive by remember { mutableStateOf(false) }

    // PENDO: Intelligent Back Navigation - If not on the first tab, go to Explore first
    BackHandler(enabled = pagerState.currentPage != 0 || isMapActive) {
        if (isMapActive) {
            // ExploreScreen handles its own back press to close map
        } else {
            scope.launch {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isOnline) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Working Offline 📶",
                        modifier = Modifier.padding(vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(innerPadding),
            beyondViewportPageCount = 1,
            userScrollEnabled = !isMapActive || items[pagerState.currentPage] != Screen.Explore
        ) { page ->
            when (items[page]) {
                is Screen.Explore -> ExploreScreen(
                    mainViewModel = mainViewModel,
                    onMapToggle = { isMapActive = it }
                )
                is Screen.Liked -> LikedScreen()
                is Screen.Trips -> TripsScreen()
                is Screen.Dashboard -> HostDashboardScreen()
                is Screen.MyAirbnbs -> MyPropertiesScreen()
                is Screen.Profile -> ProfileScreen(
                    mainViewModel = mainViewModel,
                    authViewModel = authViewModel,
                    onNavigate = onNavigate
                )
            }
        }
    }
}

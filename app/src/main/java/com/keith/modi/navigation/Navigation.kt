package com.keith.modi.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keith.modi.Supabase
import com.keith.modi.models.AuthState
import com.keith.modi.models.AuthViewModel
import com.keith.modi.models.MainViewModel
import com.keith.modi.models.UserRole
import com.keith.modi.screens.auth.LoginScreen
import com.keith.modi.screens.common.KycScreen
import com.keith.modi.screens.common.ProfileScreen
import com.keith.modi.screens.customer.ExploreScreen
import com.keith.modi.screens.customer.LikedScreen
import com.keith.modi.screens.customer.TripsScreen
import com.keith.modi.screens.host.HostDashboardScreen
import com.keith.modi.screens.host.MyPropertiesScreen
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    // Customer Screens
    object Explore : Screen("explore", "Explore", Icons.Default.Search)
    object Liked : Screen("liked", "Liked", Icons.Default.Favorite)
    object Trips : Screen("trips", "Trips", Icons.Default.History)
    
    // Host Screens
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object MyAirbnbs : Screen("my_airbnbs", "My Airbnbs", Icons.AutoMirrored.Filled.List)
    
    // Common
    object Profile : Screen("profile", "Profile", Icons.Default.AccountCircle)
    object KYC : Screen("kyc", "KYC", Icons.Default.VerifiedUser)
}

@Composable
fun ModiAppNavigation(mainViewModel: MainViewModel = viewModel()) {
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()
    
    // Check for existing session
    var sessionChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val session = Supabase.client.auth.currentSessionOrNull()
        if (session != null) {
            authViewModel.setLoggedIn()
        }
        sessionChecked = true
    }

    if (!sessionChecked) {
        // Show Splash or Loading
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (authState !is AuthState.Success) {
        LoginScreen(authViewModel)
    } else {
        MainScaffold(mainViewModel)
    }
}

@Composable
fun MainScaffold(mainViewModel: MainViewModel) {
    val currentRole by mainViewModel.currentRole.collectAsState()
    
    val customerItems = listOf(Screen.Explore, Screen.Liked, Screen.Trips, Screen.Profile)
    val hostItems = listOf(Screen.Dashboard, Screen.MyAirbnbs, Screen.Profile)
    
    val items = if (currentRole == UserRole.CUSTOMER) customerItems else hostItems
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()

    Scaffold(
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
            userScrollEnabled = true // This enables the swiping feature you requested
        ) { page ->
            when (items[page]) {
                is Screen.Explore -> ExploreScreen()
                is Screen.Liked -> LikedScreen()
                is Screen.Trips -> TripsScreen()
                is Screen.Dashboard -> HostDashboardScreen()
                is Screen.MyAirbnbs -> MyPropertiesScreen()
                is Screen.Profile -> ProfileScreen(mainViewModel)
                is Screen.KYC -> KycScreen(onVerificationComplete = {
                    scope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                })
            }
        }
    }
}

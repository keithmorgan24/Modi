package com.keith.modi

import androidx.compose.runtime.*
import androidx.navigation.compose.*
import com.keith.modi.models.*
import com.keith.modi.ui.screens.auth.*
import com.keith.modi.ui.screens.settings.*
import com.keith.modi.navigation.MainScaffold
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jan.supabase.auth.auth
import androidx.compose.ui.platform.LocalContext
import com.keith.modi.utils.NetworkUtils
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun ModiApp(mainViewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // PENDO: Intelligent Offline Monitoring
    val isOnline by mainViewModel.isOnline.collectAsState()
    
    LaunchedEffect(Unit) {
        while(true) {
            val online = NetworkUtils.isNetworkAvailable(context)
            mainViewModel.updateOnlineStatus(online)
            delay(5000) // Check every 5 seconds
        }
    }

    NavHost(navController = navController, startDestination = "splash") {
        
        composable("splash") {
            SplashScreen(onTimeout = {
                scope.launch {
                    // PENDO: Intelligent Session Verification
                    // We wait up to 2 seconds for Supabase to resolve the session state
                    var status = Supabase.client.auth.sessionStatus.value
                    var attempts = 0
                    while (status.toString().contains("Loading", true) && attempts < 10) {
                        delay(200)
                        status = Supabase.client.auth.sessionStatus.value
                        attempts++
                    }

                    if (status is SessionStatus.Authenticated) {
                        navController.navigate("main") { popUpTo("splash") { inclusive = true } }
                    } else {
                        navController.navigate("welcome") { popUpTo("splash") { inclusive = true } }
                    }
                }
            })
        }

        composable("welcome") {
            WelcomeScreen(
                onLogin = { 
                    if (isOnline) navController.navigate("login/false")
                    else mainViewModel.showOfflineNotice()
                },
                onSignUp = { 
                    if (isOnline) navController.navigate("login/true")
                    else mainViewModel.showOfflineNotice()
                },
                onGuest = { 
                    mainViewModel.setGuestMode(true)
                    navController.navigate("main") 
                }
            )
        }

        composable("login/{isSignUp}") { backStackEntry ->
            val isSignUp = backStackEntry.arguments?.getString("isSignUp")?.toBoolean() ?: false
            LoginScreen(isSignUpInitial = isSignUp, viewModel = authViewModel)
        }

        composable("main") {
            // PENDO: Intelligent Role Gate - Show Main UI with Bottom Nav
            MainScaffold(
                mainViewModel = mainViewModel,
                authViewModel = authViewModel,
                onNavigate = { route -> 
                    if (isOnline || isOfflineAllowed(route)) {
                        navController.navigate(route)
                    } else {
                        mainViewModel.showOfflineNotice()
                    }
                }
            )
        }


        composable("edit_profile") { EditProfileScreen(onBack = { navController.popBackStack() }) }
        composable("notifications") { 
            NotificationsScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onLoginRedirect = {
                    mainViewModel.setGuestMode(false)
                    navController.navigate("welcome") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            ) 
        }
        composable("privacy_security") { PrivacySecurityScreen(onBack = { navController.popBackStack() }) }
        composable("support") { SupportScreen(onBack = { navController.popBackStack() }) }
    }

    // PENDO: Intelligent Offline Dialog
    val showOfflineDialog by mainViewModel.showOfflineDialog.collectAsState()
    if (showOfflineDialog) {
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissOfflineNotice() },
            icon = { Icon(Icons.Default.WifiOff, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Currently Offline", fontWeight = FontWeight.Bold) },
            text = { 
                Text(
                    "Kindly connect to the internet to use more features of the Modi app. You can still navigate some areas, but most actions require a connection.",
                    textAlign = TextAlign.Center
                ) 
            },
            confirmButton = {
                Button(onClick = { mainViewModel.dismissOfflineNotice() }) {
                    Text("Understood")
                }
            }
        )
    }

    // PENDO: Security Observer
    val authState by authViewModel.authState.collectAsState()
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> {
                // CRITICAL FIX: Ensure Guest Mode is DISABLED when a user logs in or signs up
                mainViewModel.setGuestMode(false)
                mainViewModel.fetchUserProfile()
                navController.navigate("main") { 
                    popUpTo("welcome") { inclusive = true } 
                }
            }
            is AuthState.Idle -> {
                // mainViewModel.fetchUserProfile() // No longer needed here
                navController.navigate("welcome") { 
                    popUpTo(0) { inclusive = true }
                }
            }
            else -> {}
        }
    }
}

private fun isOfflineAllowed(route: String): Boolean {
    // PENDO: Define which routes can be explored offline (static content)
    return when(route) {
        "main", "support", "privacy_security" -> true
        else -> false
    }
}

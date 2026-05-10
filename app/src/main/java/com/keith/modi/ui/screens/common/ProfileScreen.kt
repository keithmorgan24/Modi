package com.keith.modi.ui.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.keith.modi.Supabase
import com.keith.modi.models.AuthViewModel
import com.keith.modi.models.MainViewModel
import com.keith.modi.models.Profile
import com.keith.modi.models.UserRole
import com.keith.modi.ui.theme.ModiTheme
import io.github.jan.supabase.auth.auth

@Composable
fun ProfileScreen(
    mainViewModel: MainViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    onNavigate: (String) -> Unit = {}
) {
    val role by mainViewModel.currentRole.collectAsState()
    val profile by mainViewModel.userProfile.collectAsState()
    val isGuest by mainViewModel.isGuest.collectAsState()
    val userEmail = remember { Supabase.client.auth.currentUserOrNull()?.email ?: "" }

    ProfileContent(
        profile = profile,
        email = userEmail,
        currentRole = role,
        isGuest = isGuest,
        onToggleRole = { mainViewModel.toggleRole() },
        onLogout = { authViewModel.logout() },
        onNavigate = onNavigate,
        onLoginRedirect = { 
            // Reset guest mode when navigating to login
            mainViewModel.setGuestMode(false)
            onNavigate("welcome") 
        }
    )
}

@Composable
fun ProfileContent(
    profile: Profile?,
    email: String,
    currentRole: UserRole,
    isGuest: Boolean,
    onToggleRole: () -> Unit,
    onLogout: () -> Unit,
    onNavigate: (String) -> Unit = {},
    onLoginRedirect: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var showAuthDialog by remember { mutableStateOf(false) }
    var pendingRoute by remember { mutableStateOf<String?>(null) }

    val handleAction = { route: String? ->
        if (isGuest) {
            pendingRoute = route
            showAuthDialog = true
        } else if (route != null) {
            onNavigate(route)
        }
    }

    if (showAuthDialog) {
        AlertDialog(
            onDismissRequest = { showAuthDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Authentication Required", fontWeight = FontWeight.Bold) },
            text = { Text("You need to be logged in to access this feature. Join the Modi community to get started!", textAlign = TextAlign.Center) },
            confirmButton = {
                Button(
                    onClick = {
                        showAuthDialog = false
                        onLoginRedirect()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Login or Sign Up")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthDialog = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Maybe Later")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Profile Header Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            ) {
                if (!isGuest && profile?.avatarUrl != null) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isGuest) "Guest Traveler" else (profile?.fullName ?: "Loading Profile..."),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (!isGuest) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Sign in to unlock full features",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (!isGuest) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = CircleShape
                ) {
                    Text(
                        text = if (currentRole == UserRole.HOST) "Host Account" else "Customer Account",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Account Settings Group
        Text(
            "Account Settings",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                ProfileMenuItem(
                    icon = Icons.Default.Person,
                    title = "Personal Information",
                    subtitle = "Update your name and profile photo",
                    onClick = { handleAction("edit_profile") }
                )
                
                ProfileMenuItem(
                    icon = if (currentRole == UserRole.CUSTOMER) Icons.Default.AddBusiness else Icons.Default.Explore,
                    title = if (currentRole == UserRole.CUSTOMER) "Switch to Hosting" else "Switch to Exploring",
                    subtitle = if (currentRole == UserRole.CUSTOMER) "List your own spaces" else "Find your next stay",
                    onClick = { if (isGuest) showAuthDialog = true else onToggleRole() },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // General Group
        Text(
            "General",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                ProfileMenuItem(
                    icon = Icons.Default.NotificationsNone,
                    title = "Notifications",
                    subtitle = "Stay updated with your bookings",
                    onClick = { handleAction("notifications") }
                )
                
                ProfileMenuItem(
                    icon = Icons.Default.Shield,
                    title = "Privacy & Security",
                    subtitle = "Manage your data and security",
                    onClick = { handleAction("privacy_security") }
                )

                ProfileMenuItem(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = "Help & Support",
                    subtitle = "Get assistance and read FAQs",
                    onClick = { onNavigate("support") } // Support usually public
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Guest vs Authenticated Footer
        if (isGuest) {
            Button(
                onClick = onLoginRedirect,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Login or Sign Up", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            // PENDO: High-Security Logout with clear touch target
            OutlinedButton(
                onClick = { 
                    println("[DEBUG] Logout Clicked")
                    onLogout() 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Log Out Securely", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = tint
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun ProfileScreenPreview() {
    ModiTheme {
        ProfileContent(
            profile = Profile(id = "1", fullName = "Keith Vire"),
            email = "keith@modi.com",
            currentRole = UserRole.CUSTOMER,
            isGuest = false,
            onToggleRole = {},
            onLogout = {}
        )
    }
}

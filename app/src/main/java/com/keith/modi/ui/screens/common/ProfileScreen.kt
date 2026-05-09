package com.keith.modi.screens.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keith.modi.models.AuthViewModel
import com.keith.modi.models.MainViewModel
import com.keith.modi.models.Profile
import com.keith.modi.models.UserRole
import com.keith.modi.ui.theme.ModiTheme

/**
 * State-aware entry point for the Profile Screen.
 * Prioritizes data security and reactive UI updates.
 */
@Composable
fun ProfileScreen(
    mainViewModel: MainViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val role by mainViewModel.currentRole.collectAsState()
    val profile by mainViewModel.userProfile.collectAsState()

    ProfileContent(
        profile = profile,
        currentRole = role,
        onToggleRole = { mainViewModel.toggleRole() },
        onLogout = { authViewModel.logout() }
    )
}

/**
 * Stateless UI Content for the Profile Screen.
 * Adheres to modern "Material 3" design standards and security-first UX.
 */
@Composable
fun ProfileContent(
    profile: Profile?,
    currentRole: UserRole,
    onToggleRole: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Profile Avatar with Security/Verification Badge
        Box {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(70.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (profile?.isKycVerified == true) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 4.dp
                ) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Verified Identity",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = profile?.fullName ?: "Welcome to Modi",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (profile?.isKycVerified == true) "Verified Member" else "Identity Verification Required",
            style = MaterialTheme.typography.bodyMedium,
            color = if (profile?.isKycVerified == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Actions Card - Modern, grouped layout
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileMenuItem(
                    icon = Icons.Default.VerifiedUser,
                    title = "KYC Verification",
                    subtitle = if (profile?.isKycVerified == true) "Status: Verified" else "Action required: Complete ID check",
                    onClick = { /* Navigation to KYC handled via Pager in Navigation.kt */ }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                ProfileMenuItem(
                    icon = if (currentRole == UserRole.CUSTOMER) Icons.Default.Storefront else Icons.Default.Search,
                    title = if (currentRole == UserRole.CUSTOMER) "Switch to Hosting" else "Switch to Exploring",
                    subtitle = "Switch between being a guest or a host",
                    onClick = onToggleRole
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                ProfileMenuItem(
                    icon = Icons.Default.Settings,
                    title = "Security Settings",
                    subtitle = "Two-factor auth and privacy",
                    onClick = { }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Secure Logout Button
        TextButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout Securely", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun ProfileScreenPreview() {
    ModiTheme {
        ProfileContent(
            profile = Profile(id = "1", fullName = "John Doe", isKycVerified = true),
            currentRole = UserRole.CUSTOMER,
            onToggleRole = {},
            onLogout = {}
        )
    }
}

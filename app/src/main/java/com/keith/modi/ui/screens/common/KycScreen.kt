package com.keith.modi.ui.screens.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keith.modi.CloudinaryHelper
import com.keith.modi.Supabase
import com.keith.modi.utils.ErrorUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KycScreen(onVerificationComplete: () -> Unit) {
    var idUri by remember { mutableStateOf<Uri?>(null) }
    var selfieUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadStep by remember { mutableIntStateOf(1) } // 1: ID, 2: Selfie, 3: Success
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val idLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        idUri = uri
    }
    
    val selfieLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selfieUri = uri
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Identity Verification", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress Indicator
            LinearProgressIndicator(
                progress = { when(uploadStep) { 1 -> 0.3f; 2 -> 0.6f; else -> 1f } },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // PENDO: Modern Error UI
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(it, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            when (uploadStep) {
                1 -> KycUploadStep(
                    title = "Upload National ID / Passport",
                    description = "We need this to verify your identity as a professional host.",
                    icon = Icons.Default.Badge,
                    selectedUri = idUri,
                    onPick = { idLauncher.launch("image/*") },
                    onNext = { if (idUri != null) uploadStep = 2 }
                )
                2 -> KycUploadStep(
                    title = "Live Selfie Check",
                    description = "Take a clear selfie to match against your ID.",
                    icon = Icons.Default.Face,
                    selectedUri = selfieUri,
                    onPick = { selfieLauncher.launch("image/*") },
                    onNext = {
                        if (selfieUri != null) {
                            scope.launch {
                                isUploading = true
                                errorMessage = null
                                try {
                                    val userId = Supabase.client.auth.currentUserOrNull()?.id
                                        ?: throw Exception("User not logged in")

                                    // 1. Upload to Cloudinary
                                    val idResult = CloudinaryHelper.uploadImage(context, idUri!!, "kyc_ids")
                                    val selfieResult = CloudinaryHelper.uploadImage(context, selfieUri!!, "kyc_selfies")
                                    
                                    val idUrl = idResult["secure_url"] as String
                                    val selfieUrl = selfieResult["secure_url"] as String

                                    Supabase.client.postgrest["profiles"].update(
                                        mapOf("avatar_url" to selfieUrl) 
                                    ) {
                                        filter { eq("id", userId) }
                                    }

                                    uploadStep = 3
                                } catch (e: Exception) {
                                    errorMessage = ErrorUtils.sanitizeError(e)
                                } finally {
                                    isUploading = false
                                }
                            }
                        }
                    },
                    isLoading = isUploading
                )
                3 -> KycSuccessStep(onVerificationComplete)
            }
        }
    }
}

@Composable
fun KycUploadStep(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedUri: Uri?,
    onPick: () -> Unit,
    onNext: () -> Unit,
    isLoading: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .clickable { onPick() },
            contentAlignment = Alignment.Center
        ) {
            if (selectedUri != null) {
                Text("Image Selected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    Text("Tap to upload photo", color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedUri != null && !isLoading,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun KycSuccessStep(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(100.dp), tint = Color(0xFF4CAF50))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Verification Submitted!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Our AI is reviewing your documents. You can start exploring while we verify.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
            Text("Go to Dashboard")
        }
    }
}

package com.keith.modi.ui.screens.common

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.keith.modi.BuildConfig
import com.keith.modi.Supabase
import com.keith.modi.models.AppRelease
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import com.google.zxing.qrcode.QRCodeWriter
import com.keith.modi.ui.theme.ModiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareAppScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // PENDO: Professional distribution link management - Constructing dynamically from Config
    val baseUrl = BuildConfig.SUPABASE_URL.removeSuffix("/")
    val fallbackLink = "$baseUrl/storage/v1/object/public/app-distribution/modi_v1_0.apk"
    
    var appLink by remember { mutableStateOf(fallbackLink) }
    var latestRelease by remember { mutableStateOf<AppRelease?>(null) }
    var isLoadingLink by remember { mutableStateOf(true) }
    
    var fetchError by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        try {
            // 1. Fetch latest release info from Database
            val release = Supabase.client.postgrest["app_releases"]
                .select {
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }.decodeSingleOrNull<AppRelease>()
            
            if (release != null) {
                latestRelease = release
                // PENDO: Intelligent path resolution - handles both full URLs and storage paths
                appLink = if (release.apkPath.startsWith("http")) {
                    release.apkPath
                } else {
                    Supabase.client.storage["app-distribution"].publicUrl(release.apkPath)
                }
            } else {
                // PENDO: Dynamic fallback link construction
                appLink = fallbackLink
                println("[SUPABASE] No release info found. Using fallback link.")
            }
        } catch (e: Exception) {
            // Only show error for genuine network/sync failures
            fetchError = "Connection Sync Failed"
            println("[SUPABASE] Release fetch failed: ${e.message}")
        } finally {
            isLoadingLink = false
        }
    }

    val qrBitmap = remember(appLink) { generateQrCode(appLink) }
    var isPreparingFile by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Distribute Modi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(appLink))
                        scope.launch { snackbarHostState.showSnackbar("Link copied to clipboard") }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Invite your circle to Modi 🏠",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            if (isLoadingLink) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Error Notification (Only for actual failures)
            if (fetchError != null && !isLoadingLink) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Sync Notice: $fetchError. Using local backup link.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            latestRelease?.let { release ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Latest Version: v${release.versionName}", fontWeight = FontWeight.Bold)
                            release.releaseNotes?.let { 
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        if (release.isCritical) {
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text("CRITICAL", color = MaterialTheme.colorScheme.onError)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Premium QR Code Card
            Card(
                modifier = Modifier.size(280.dp),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    qrBitmap?.let {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(180.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "SCAN TO INSTALL", 
                                style = MaterialTheme.typography.labelSmall, 
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                        }
                    } ?: CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary Distribution Actions
            Text("Verified Distribution Methods", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // PENDO: The "Direct Installer" Action - Shares the actual App File (APK)
                // This is the most reliable way to share the app in low-connectivity areas
                Button(
                    onClick = {
                        scope.launch {
                            isPreparingFile = true
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val apkFile = File(context.applicationInfo.sourceDir)
                                    val cachePath = File(context.cacheDir, "apps")
                                    cachePath.mkdirs()
                                    val sharedApk = File(cachePath, "Modi_Premium.apk")
                                    
                                    // Efficient copy stream
                                    apkFile.inputStream().use { input ->
                                        sharedApk.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }

                                    val hash = calculateSHA256(sharedApk)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", sharedApk)
                                    Pair(uri, hash)
                                }

                                val (contentUri, sha256) = result
                                val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                                
                                val shareIntent = ShareCompat.IntentBuilder(context)
                                    .setType("application/vnd.android.package-archive")
                                    .setStream(contentUri)
                                    .setSubject("Install Modi v$versionName")
                                    .setText("Hey! Join me on Modi. Here's the app installer.\n\n🔒 Verified SHA-256:\n$sha256")
                                    .createChooserIntent()
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                scope.launch { snackbarHostState.showSnackbar("Sharing failed: ${e.localizedMessage}") }
                            } finally {
                                isPreparingFile = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1.2f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isPreparingFile
                ) {
                    if (isPreparingFile) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Share APK", 
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val shareIntent = ShareCompat.IntentBuilder(context)
                            .setType("text/plain")
                            .setSubject("Join me on Modi")
                            .setText("Hey! Join me on Modi, the best app for premium stays: $appLink")
                            .createChooserIntent()
                        context.startActivity(shareIntent)
                    },
                    modifier = Modifier.weight(0.8f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Link Only", fontSize = 14.sp, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Secondary Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ShareOptionItem(
                        icon = Icons.Default.QrCode,
                        title = "Share QR as Image",
                        onClick = {
                            qrBitmap?.let { bitmap ->
                                try {
                                    val cachePath = File(context.cacheDir, "images")
                                    cachePath.mkdirs()
                                    val imagePath = File(cachePath, "modi_qr.png")
                                    FileOutputStream(imagePath).use { stream ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                    }
                                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imagePath)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, contentUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    ShareOptionItem(
                        icon = Icons.Default.Email,
                        title = "Send via Email",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:?subject=Join%20me%20on%20Modi&body=Hey!%20Check%20out%20Modi%20at%20$appLink"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun ShareOptionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

fun generateQrCode(text: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

/**
 * PENDO: Security helper to calculate SHA-256 hash of the APK.
 * This ensures integrity when sharing files directly.
 */
private fun calculateSHA256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { fis ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

@Preview(showBackground = true)
@Composable
fun ShareAppScreenPreview() {
    ModiTheme {
        ShareAppScreen(onBack = {})
    }
}

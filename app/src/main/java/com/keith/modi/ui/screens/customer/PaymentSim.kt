package com.keith.modi.screens.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun MpesaPaymentDialog(
    amount: Double,
    errorMessage: String? = null,
    onPayClicked: (String) -> Unit,
    onCancel: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var isTriggered by remember { mutableStateOf(false) }

    // Reset triggered state if a new error comes in
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) isTriggered = false
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Confirm M-PESA Payment", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Amount: KSH $amount", color = Color.White.copy(alpha = 0.8f))

                Spacer(Modifier.height(24.dp))

                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(errorMessage, color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (!isTriggered) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone Number", color = Color.White.copy(alpha = 0.7f)) },
                        placeholder = { Text("07XXXXXXXX", color = Color.White.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Requesting prompt... 📲", color = Color.White)
                }

                Spacer(Modifier.height(32.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = { isTriggered = true; onPayClicked(phoneNumber) },
                        enabled = phoneNumber.length >= 10 && !isTriggered,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text("Pay Now 💰", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

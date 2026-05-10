package com.keith.modi.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
    onGuest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Welcome to Modi",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            text = "Experience the future of property hosting and secure stays.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Premium Selection Buttons
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Sign In to My Account", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSignUp,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text("Create New Account", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(32.dp))

        TextButton(
            onClick = onGuest,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Continue as Guest", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

package com.keith.modi.utils

import io.github.jan.supabase.exceptions.HttpRequestException

object ErrorUtils {
    fun sanitizeError(e: Throwable): String {
        // PENDO: SECURITY FIRST - Strip tokens but reveal the technical heart of the issue
        var message = e.message ?: ""
        message = message.replace(Regex("Bearer\\s+[a-zA-Z0-9\\-_\\.]+"), "[TOKEN MASKED]")
        
        return when {
            message.contains("Invalid login credentials", ignoreCase = true) -> "Invalid email or password"
            message.contains("404", ignoreCase = true) -> "Service Not Found (404). Check project reference."
            message.contains("401", ignoreCase = true) -> "Unauthorized (401). Check your Anon Key."
            
            // PENDO: Enhanced Rate Limit Handling
            message.contains("over_email_send_rate_limit", ignoreCase = true) -> "Too many attempts. Please wait a few minutes before trying again for security purposes."
            
            // PENDO: Secure Email Confirmation Handling
            message.contains("email_not_confirmed", ignoreCase = true) -> "Please check your inbox to confirm your email before logging in."

            // PENDO: Unique Email Enforcement (Signup)
            message.contains("User already registered", ignoreCase = true) -> "This email is already registered. Please login instead."
            message.contains("Email address already exists", ignoreCase = true) -> "This email is already in use. Try a different one."

            // PENDO: Trace debugging - Shield technical leaks (URLs/Tokens)
            else -> {
                "Authentication error. Please ensure your credentials are correct."
            }
        }
    }
}

package com.keith.modi.utils

import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.ResponseException

object ErrorUtils {
    fun sanitizeError(e: Throwable): String {
        // PENDO: Cybersecurity first - Ensure no tokens or internal URLs are leaked in production logs/UI
        val message = e.message ?: ""
        
        return when {
            // Check for specific Supabase Auth errors
            message.contains("Invalid login credentials", ignoreCase = true) -> "Invalid email or password"
            message.contains("Email not confirmed", ignoreCase = true) -> "Please confirm your email address"
            message.contains("User already registered", ignoreCase = true) -> "An account with this email already exists"
            message.contains("Unable to validate", ignoreCase = true) || message.contains("validation_failed") -> "Kindly fill out all details correctly"
            
            // Network issues
            e is HttpRequestException || e is java.net.ConnectException -> "Connection failed. Please check your internet."
            
            // Generic fallback that's user friendly
            else -> "Something went wrong. Please try again later."
        }
    }
}

package com.keith.modi.utils

import io.github.jan.supabase.exceptions.HttpRequestException

object ErrorUtils {
    fun sanitizeError(e: Throwable): String {
        // PENDO: SECURITY FIRST - Strip tokens but reveal technical hints
        var message = e.message ?: ""
        message = message.replace(Regex("Bearer\\s+[a-zA-Z0-9\\-_\\.]+"), "[TOKEN MASKED]")
        
        return when {
            message.contains("Invalid login credentials", ignoreCase = true) -> "Invalid email or password"
            message.contains("Unexpected end of JSON", ignoreCase = true) -> "Server returned a non-JSON error. Check function deployment."
            message.contains("404", ignoreCase = true) -> "Function not found (404). Ensure it is deployed to the correct project."
            message.contains("401", ignoreCase = true) -> "Unauthorized (401). Check your Anon Key."
            message.contains("500", ignoreCase = true) -> "Server crash (500). Check Supabase Edge logs."
            
            // Network issues
            e is HttpRequestException || e is java.net.ConnectException -> "Connection failed. Check your internet and project URL."
            
            // PENDO: Technical fallback for debugging
            else -> {
                val clean = if (message.length > 80) message.take(80) + "..." else message
                "System Error: $clean"
            }
        }
    }
}

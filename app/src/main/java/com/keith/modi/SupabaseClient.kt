package com.keith.modi

import android.content.Context
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.functions.Functions
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

object Supabase {
    private const val SUPABASE_URL = "https://beztonodgfvlrxzyxkxb.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJlenRvbm9kZ2Z2bHJ4enl4a3hiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgyMjkyMDYsImV4cCI6MjA5MzgwNTIwNn0.DdIC1FaWOrNGd5tFmnIVkq9jg4yVdyJHsiNXJKPahdc"

    lateinit var client: io.github.jan.supabase.SupabaseClient

    fun init(context: Context) {
        client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Auth) {
                sessionManager = SharedPreferencesSessionManager(context)
            }
            install(Realtime)
            install(Storage)
            install(Functions)
        }
    }
}

/**
 * PENDO: High-Security Session Persistence
 * Encapsulates the user's session in Android's private SharedPreferences.
 */
class SharedPreferencesSessionManager(context: Context) : SessionManager {
    private val prefs = context.getSharedPreferences("modi_secure_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        val serialized = json.encodeToString(session)
        prefs.edit().putString("session_data", serialized).apply()
    }

    override suspend fun loadSession(): UserSession? {
        val sessionStr = prefs.getString("session_data", null) ?: return null
        return try {
            json.decodeFromString<UserSession>(sessionStr)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteSession() {
        prefs.edit().remove("session_data").apply()
    }
}

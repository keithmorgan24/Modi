package com.keith.modi

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.functions.Functions
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.keith.modi.BuildConfig

object Supabase {
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY

    lateinit var client: io.github.jan.supabase.SupabaseClient

    fun init(context: Context) {
        client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            httpEngine = OkHttp.create()
            
            // PENDO: Data Integrity - Ignore unknown backend fields to prevent crashes during schema updates
            defaultSerializer = io.github.jan.supabase.serializer.KotlinXSerializer(Json { 
                ignoreUnknownKeys = true 
                coerceInputValues = true
                encodeDefaults = true
            })

            install(Postgrest)
            install(Auth) {
                sessionManager = SecureSessionManager(context)
            }
            install(Realtime)
            install(Storage)
            install(Functions)
        }
    }
}

/**
 * PENDO: Military-Grade Session Persistence
 * Uses AES-256 encryption to protect session tokens at rest.
 */
class SecureSessionManager(context: Context) : SessionManager {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "modi_secure_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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

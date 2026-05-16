package com.keith.modi

import android.content.Context
import android.content.SharedPreferences
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
            
            defaultSerializer = io.github.jan.supabase.serializer.KotlinXSerializer(Json { 
                ignoreUnknownKeys = true 
                coerceInputValues = true
                encodeDefaults = true
            })

            install(Postgrest)
            install(Auth) {
                // PENDO: Intelligent Session Recovery
                try {
                    sessionManager = SecureSessionManager(context)
                } catch (e: Exception) {
                    println("[SECURITY] SecureSessionManager failed: ${e.message}")
                    // Supabase will use its default SessionManager if we don't assign one here
                }
            }
            install(Realtime)
            install(Storage)
            install(Functions)
        }
    }
}

class SecureSessionManager(context: Context) : SessionManager {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            "modi_secure_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("modi_session_fallback", Context.MODE_PRIVATE)
    }

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

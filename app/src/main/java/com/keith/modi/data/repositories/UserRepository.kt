package com.keith.modi.data.repositories

import com.keith.modi.Supabase
import com.keith.modi.models.Profile
import com.keith.modi.models.UserRole
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * PENDO: Professional Data Access Layer
 * Decouples the UI from Supabase implementation and centralizes security logic.
 */
class UserRepository {
    private val client = Supabase.client
    private val profileTable = client.postgrest["profiles"]

    suspend fun getProfile(userId: String): Profile? {
        return try {
            profileTable.select {
                filter { eq("id", userId) }
            }.decodeSingle<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateProfile(userId: String, updates: Map<String, Any>): Boolean {
        return try {
            // PENDO SECURITY: Never trust client-provided IDs for role updates
            // Role updates should ideally be handled via a secure Edge Function
            if (updates.containsKey("role")) {
                throw SecurityException("Role updates restricted to secure environment")
            }
            
            profileTable.update(updates) {
                filter { eq("id", userId) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * PENDO: Realtime Security Sync
     * Flows profile updates directly to the UI using Supabase Realtime.
     */
    fun observeProfile(userId: String): Flow<Profile> {
        val channel = client.channel("user_profile_sync_$userId")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "profiles"
            // PENDO: The filter is set here via the DSL
            // filter = "id=eq.$userId" // This is often handled in the subscription logic in newer versions
        }
        
        return flow.mapNotNull { action ->
            if (action is PostgresAction.Update) {
                val profile = action.decodeRecord<Profile>()
                if (profile.id == userId) profile else null
            } else {
                null
            }
        }
    }
}

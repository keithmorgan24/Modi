package com.keith.modi.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keith.modi.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _currentRole = MutableStateFlow(UserRole.CUSTOMER)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    private val _userProfile = MutableStateFlow<Profile?>(null)
    val userProfile: StateFlow<Profile?> = _userProfile.asStateFlow()

    init {
        fetchUserProfile()
        listenToProfileChanges()
    }

    fun toggleRole() {
        _currentRole.value = if (_currentRole.value == UserRole.CUSTOMER) {
            UserRole.HOST
        } else {
            UserRole.CUSTOMER
        }
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    val profile = Supabase.client.postgrest["profiles"]
                        .select {
                            filter { eq("id", userId) }
                        }
                        .decodeSingle<Profile>()
                    _userProfile.value = profile
                }
            } catch (e: Exception) {
                println("Error fetching user profile: ${e.localizedMessage}")
            }
        }
    }

    private fun listenToProfileChanges() {
        val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return
        
        val channel = Supabase.client.channel("profile_changes")
        val profileFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "profiles"
        }

        profileFlow.onEach { action ->
            when (action) {
                is PostgresAction.Update -> {
                    val updatedProfile = action.decodeRecord<Profile>()
                    if (updatedProfile.id == userId) {
                        _userProfile.value = updatedProfile
                    }
                }
                else -> {}
            }
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            channel.subscribe()
        }
    }
}
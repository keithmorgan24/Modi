package com.keith.modi.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keith.modi.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun signUp(email: String, pass: String, name: String, role: UserRole = UserRole.CUSTOMER) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                Supabase.client.auth.signUpWith(Email) {
                    this.email = email
                    password = pass
                    // This data goes into raw_user_meta_data which our SQL trigger reads
                    data = buildJsonObject {
                        put("full_name", name)
                        put("role", role.name)
                    }
                }
                _authState.value = AuthState.Success("Check your email for confirmation!")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Registration failed")
            }
        }
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                Supabase.client.auth.signInWith(Email) {
                    this.email = email
                    password = pass
                }
                _authState.value = AuthState.Success("Logged in successfully!")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Login failed")
            }
        }
    }
    
    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun setLoggedIn() {
        _authState.value = AuthState.Success("Welcome back!")
    }

    fun logout() {
        viewModelScope.launch {
            try {
                Supabase.client.auth.signOut()
                _authState.value = AuthState.Idle
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Logout failed")
            }
        }
    }
}

package com.keith.modi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun signUp(email: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                Supabase.client.auth.signUpWith(Email) {
                    this.email = email
                    password = pass
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
}

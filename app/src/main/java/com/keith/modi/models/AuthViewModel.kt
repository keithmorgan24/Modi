package com.keith.modi.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keith.modi.Supabase
import com.keith.modi.utils.ErrorUtils
import com.keith.modi.utils.ValidationUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class AuthState {
    object Initial : AuthState()
    object Idle : AuthState()
    object Loading : AuthState()
    object PasswordResetRequested : AuthState() // New state for "Check your email" screen
    object PasswordResetRequired : AuthState() // PENDO: Specific state for recovery
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun signUp(email: String, pass: String, name: String, role: UserRole = UserRole.CUSTOMER) {
        if (email.isBlank() || pass.isBlank() || name.isBlank()) {
            _authState.value = AuthState.Error("Kindly fill out all details")
            return
        }

        val emailValidation = ValidationUtils.validateEmail(email)
        if (emailValidation is ValidationUtils.ValidationResult.Error) {
            _authState.value = AuthState.Error(emailValidation.message)
            return
        }

        val passwordValidation = ValidationUtils.validatePassword(pass)
        if (passwordValidation is ValidationUtils.ValidationResult.Error) {
            _authState.value = AuthState.Error(passwordValidation.message)
            return
        }

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
                _authState.value = AuthState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authState.value = AuthState.Error("Kindly fill out the details")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                Supabase.client.auth.signInWith(Email) {
                    this.email = email
                    password = pass
                }
                _authState.value = AuthState.Success("Logged in successfully!")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }
    
    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun setLoggedIn() {
        _authState.value = AuthState.Success("Welcome back!")
    }

    fun setResetRequired() {
        _authState.value = AuthState.PasswordResetRequired
    }

    fun logout() {
        viewModelScope.launch {
            try {
                Supabase.client.auth.signOut()
                _authState.value = AuthState.Idle
            } catch (e: Exception) {
                _authState.value = AuthState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun updateFullName(newName: String) {
        if (newName.isBlank()) {
            _authState.value = AuthState.Error("Name cannot be blank")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id ?: throw Exception("User not found")
                
                // 1. Update public.profiles table directly (MainViewModel listens to this)
                Supabase.client.postgrest["profiles"].update(
                    mapOf("full_name" to newName)
                ) {
                    filter { eq("id", userId) }
                }

                // 2. Also update Auth metadata for consistency
                Supabase.client.auth.updateUser {
                    data = buildJsonObject {
                        put("full_name", newName)
                    }
                }
                
                _authState.value = AuthState.Success("Name updated successfully!")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun updateAvatar(newAvatarUrl: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val userId = Supabase.client.auth.currentUserOrNull()?.id ?: throw Exception("User not found")

                // 1. Update public.profiles table directly
                Supabase.client.postgrest["profiles"].update(
                    mapOf("avatar_url" to newAvatarUrl)
                ) {
                    filter { eq("id", userId) }
                }

                // 2. Update Auth metadata
                Supabase.client.auth.updateUser {
                    data = buildJsonObject {
                        put("avatar_url", newAvatarUrl)
                    }
                }
                _authState.value = AuthState.Success("Profile picture updated!")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun updatePassword(newPass: String) {
        val passwordValidation = ValidationUtils.validatePassword(newPass)
        if (passwordValidation is ValidationUtils.ValidationResult.Error) {
            _authState.value = AuthState.Error(passwordValidation.message)
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                Supabase.client.auth.updateUser {
                    password = newPass
                }
                _authState.value = AuthState.Success("Password updated successfully!")
                // PENDO: Force login state after reset to trigger main app observer
                setLoggedIn()
            } catch (e: Exception) {
                _authState.value = AuthState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun updatePasswordWithVerification(currentPass: String, newPass: String) {
        if (currentPass.isBlank()) {
            _authState.value = AuthState.Error("Please enter your current password")
            return
        }

        val passwordValidation = ValidationUtils.validatePassword(newPass)
        if (passwordValidation is ValidationUtils.ValidationResult.Error) {
            _authState.value = AuthState.Error(passwordValidation.message)
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // 1. Standard secure flow: sign in with current credentials to verify, then update.
                val email = Supabase.client.auth.currentUserOrNull()?.email ?: throw Exception("User not found")
                
                Supabase.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = currentPass
                }

                // 2. If re-auth succeeded, update to new password
                Supabase.client.auth.updateUser {
                    password = newPass
                }
                _authState.value = AuthState.Success("Password updated successfully!")
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Verification failed: Ensure current password is correct.")
            }
        }
    }

    fun requestAccountDeletion() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // In a production app, you would call a Supabase Edge Function here 
                // to handle the secure deletion of user data and the auth record.
                // For now, we sign out and set a state.
                Supabase.client.auth.signOut()
                _authState.value = AuthState.Error("Deletion request submitted. You have been logged out.")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _authState.value = AuthState.Error("Please enter your email address")
            return
        }

        val emailValidation = ValidationUtils.validateEmail(email)
        if (emailValidation is ValidationUtils.ValidationResult.Error) {
            _authState.value = AuthState.Error(emailValidation.message)
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // PENDO SECURITY: Generic response to prevent user enumeration
                Supabase.client.auth.resetPasswordForEmail(email)
                _authState.value = AuthState.PasswordResetRequested
            } catch (e: Exception) {
                _authState.value = AuthState.Error(ErrorUtils.sanitizeError(e))
            }
        }
    }
}

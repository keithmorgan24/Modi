package com.keith.modi.utils

object ValidationUtils {
    
    /**
     * PENDO: Security Standard Password Validation
     * Requirements:
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * - At least one special character (@$!%*?&)
     */
    fun validatePassword(password: String): ValidationResult {
        if (password.length < 8) {
            return ValidationResult.Error("Password must be at least 8 characters long")
        }
        if (!password.any { it.isUpperCase() }) {
            return ValidationResult.Error("Add at least one uppercase letter")
        }
        if (!password.any { it.isLowerCase() }) {
            return ValidationResult.Error("Add at least one lowercase letter")
        }
        if (!password.any { it.isDigit() }) {
            return ValidationResult.Error("Add at least one number")
        }
        val specialChars = "@$!%*?&#^()_=+-"
        if (!password.any { it in specialChars }) {
            return ValidationResult.Error("Add at least one special character (@$!%*?&...)")
        }
        
        return ValidationResult.Success
    }

    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}

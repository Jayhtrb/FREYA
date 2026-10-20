package ai.freya.app.ui.screens.auth

import ai.freya.app.FreyaApplication
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as FreyaApplication).container.authRepository

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _error.value = "Email and password are required."
            return
        }
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                repo.login(email.trim(), password)
                _success.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Login failed"
            } finally {
                _loading.value = false
            }
        }
    }

    fun register(email: String, password: String, displayName: String) {
        if (email.isBlank() || password.isBlank() || displayName.isBlank()) {
            _error.value = "Email, password and display name are required."
            return
        }
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                repo.register(email.trim(), password, displayName.trim())
                _success.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Registration failed"
            } finally {
                _loading.value = false
            }
        }
    }
}

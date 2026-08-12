package com.closeonjae.chesspuzzle.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closeonjae.chesspuzzle.auth.LichessAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val isSigningIn: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)

/** DESIGN.md 5절 로그인 화면 states: default / in-progress / failed. */
class LoginViewModel(private val authManager: LichessAuthManager) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signIn() {
        if (_uiState.value.isSigningIn) return
        _uiState.update { it.copy(isSigningIn = true, error = null) }
        viewModelScope.launch {
            authManager.signIn()
                .onSuccess { _uiState.update { it.copy(isSigningIn = false, signedIn = true) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isSigningIn = false, error = e.message ?: "Sign-in failed") }
                }
        }
    }
}

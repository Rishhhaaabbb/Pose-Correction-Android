package com.posemonitor2.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.domain.model.AuthUser
import com.domain.model.UserProfile
import com.domain.repository.AuthRepository
import com.domain.usecase.EnsureProfileUseCase
import com.domain.usecase.FetchUserRoleUseCase
import com.domain.usecase.RestoreSessionUseCase
import com.domain.usecase.SendPasswordResetUseCase
import com.domain.usecase.SignInWithEmailUseCase
import com.domain.usecase.SignInWithGoogleUseCase
import com.domain.usecase.SignUpWithEmailUseCase
import com.posemonitor2.ui.theme.ThemeMode
import com.posemonitor2.ui.theme.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signUpWithEmailUseCase: SignUpWithEmailUseCase,
    private val signInWithEmailUseCase: SignInWithEmailUseCase,
    private val sendPasswordResetUseCase: SendPasswordResetUseCase,
    private val ensureProfileUseCase: EnsureProfileUseCase,
    private val authRepository: AuthRepository,
    private val fetchUserRoleUseCase: FetchUserRoleUseCase,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        restoreSession()
    }

    fun restoreSession() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val restored = restoreSessionUseCase().getOrNull()
            val isAdmin = restored?.id?.let { uid ->
                fetchUserRoleUseCase(uid) == "admin"
            } ?: false
            _state.update {
                it.copy(
                    isLoading = false,
                    isAuthenticated = restored != null,
                    userId = restored?.id,
                    userEmail = restored?.email,
                    isAdmin = isAdmin,
                )
            }
        }
    }

    fun signInWithGoogle(idToken: String, fallbackName: String?, fallbackEmail: String?) {
        if (idToken.isBlank()) {
            _state.update { it.copy(errorMessage = "Missing Google ID token") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            val authResult = signInWithGoogleUseCase(idToken)
            authResult.onSuccess { authUser ->
                val isAdmin = fetchUserRoleUseCase(authUser.id) == "admin"
                _state.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        userId = authUser.id,
                        userEmail = authUser.email,
                        isAdmin = isAdmin,
                        errorMessage = null,
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = false,
                        userId = null,
                        userEmail = null,
                        isAdmin = false,
                        errorMessage = err.message ?: "Google sign-in failed",
                    )
                }
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            signUpWithEmailUseCase(name, email, password)
                .onSuccess { authUser ->
                    ensureProfileUseCase(
                        UserProfile(
                            id = authUser.id,
                            displayName = authUser.fullName ?: name,
                            email = authUser.email,
                            profileImageUrl = null,
                            heightCm = null,
                            weightKg = null,
                            fitnessGoal = null,
                            targetWeightKg = null,
                            weeklyGoalSessions = null,
                        )
                    )
                    val isAdmin = fetchUserRoleUseCase(authUser.id) == "admin"
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            userId = authUser.id,
                            userEmail = authUser.email,
                            isAdmin = isAdmin,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Sign-up failed")
                    }
                }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            signInWithEmailUseCase(email, password)
                .onSuccess { authUser ->
                    val isAdmin = fetchUserRoleUseCase(authUser.id) == "admin"
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            userId = authUser.id,
                            userEmail = authUser.email,
                            isAdmin = isAdmin,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Sign-in failed")
                    }
                }
        }
    }

    fun sendPasswordReset(email: String, onSent: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, resetEmailSent = false) }
            sendPasswordResetUseCase(email)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, resetEmailSent = true) }
                    onSent()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Failed to send reset email", resetEmailSent = false)
                    }
                }
        }
    }

    fun onGoogleSignInFailed(message: String) {
        _state.update { it.copy(errorMessage = message, isLoading = false) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null, resetEmailSent = false) }
    }

    fun signOut() {
        viewModelScope.launch {
            // Reset to light theme so login/onboarding screens are never shown in dark mode
            ThemePreferences.setThemeMode(getApplication(), ThemeMode.LIGHT)
            authRepository.signOut()
            _state.update {
                it.copy(
                    isAuthenticated = false,
                    userId = null,
                    userEmail = null,
                    isAdmin = false,
                    errorMessage = null
                )
            }
        }
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val userId: String? = null,
    val userEmail: String? = null,
    val isAdmin: Boolean = false,
    val errorMessage: String? = null,
    val resetEmailSent: Boolean = false,
)

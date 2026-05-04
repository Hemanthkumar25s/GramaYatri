package com.gramayatri.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val user: FirebaseUser? = null,
    val error: String? = null,
    val isGuest: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // Check if user is already signed in
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            _uiState.update {
                it.copy(
                    isSignedIn = true,
                    user = currentUser,
                    isGuest = currentUser.isAnonymous
                )
            }
        }
    }

    // Sign in with Google ID token
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = firebaseAuth.signInWithCredential(credential).await()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSignedIn = true,
                        user = result.user,
                        isGuest = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Sign in failed")
                }
            }
        }
    }

    // Continue as guest (anonymous auth)
    fun continueAsGuest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = firebaseAuth.signInAnonymously().await()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSignedIn = true,
                        user = result.user,
                        isGuest = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Guest sign in failed")
                }
            }
        }
    }

    // Sign out
    fun signOut() {
        firebaseAuth.signOut()
        _uiState.update { AuthUiState() }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

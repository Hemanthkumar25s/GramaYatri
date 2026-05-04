package com.gramayatri.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gramayatri.data.model.UserPreferences
import com.gramayatri.data.repository.LocalCacheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val localCacheRepository: LocalCacheRepository
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = localCacheRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    fun saveName(name: String) {
        viewModelScope.launch {
            val current = userPreferences.value
            localCacheRepository.saveUserPreferences(current.copy(userName = name))
        }
    }

    fun updatePreferredRoute(routeId: String) {
        viewModelScope.launch {
            val current = userPreferences.value
            localCacheRepository.saveUserPreferences(current.copy(preferredRouteId = routeId))
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val current = userPreferences.value
            localCacheRepository.saveUserPreferences(current.copy(notificationsEnabled = enabled))
        }
    }
}

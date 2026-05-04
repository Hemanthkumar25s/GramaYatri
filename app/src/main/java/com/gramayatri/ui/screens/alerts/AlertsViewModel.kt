package com.gramayatri.ui.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gramayatri.data.model.AlertsUiState
import com.gramayatri.data.repository.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    fun loadAlerts(routeId: String) {
        viewModelScope.launch {
            firebaseRepository.observeAlerts(routeId)
                .collect { alerts ->
                    _uiState.update {
                        it.copy(isLoading = false, alerts = alerts)
                    }
                }
        }
    }
}

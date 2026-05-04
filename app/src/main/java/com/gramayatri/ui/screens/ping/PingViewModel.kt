package com.gramayatri.ui.screens.ping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gramayatri.data.model.*
import com.gramayatri.data.repository.FirebaseRepository
import com.gramayatri.data.repository.LocalCacheRepository
import com.gramayatri.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PingViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository,
    private val localCacheRepository: LocalCacheRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PingUiState())
    val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

    private var rateLimitJob: Job? = null

    fun selectStop(stop: Stop) = _uiState.update { it.copy(selectedStop = stop) }

    fun selectPingType(type: PingType) = _uiState.update { it.copy(selectedPingType = type) }

    fun submitPing(route: Route, userName: String) {
        val stop = _uiState.value.selectedStop ?: return
        if (_uiState.value.isSubmitting || _uiState.value.rateLimited) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            val prefs = localCacheRepository.userPreferencesFlow.first()
            val deviceId = prefs.deviceId.ifEmpty {
                localCacheRepository.getDeviceId()
            }

            val ping = BusPing(
                routeId = route.id,
                stopId = stop.id,
                stopName = stop.name,
                stopSequence = stop.sequence,
                lat = stop.lat,
                lng = stop.lng,
                userName = userName.ifEmpty { "Anonymous" },
                deviceId = deviceId,
                type = _uiState.value.selectedPingType,
                timestamp = System.currentTimeMillis(),
                isActive = true
            )

            when (val result = firebaseRepository.submitPing(ping, deviceId)) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, success = true, error = null)
                    }
                    // Auto-reset success state after 3 seconds
                    delay(3000)
                    _uiState.update { it.copy(success = false) }
                }
                is NetworkResult.Error -> {
                    val isRateLimited = result.message.contains("Rate limited")
                    if (isRateLimited) {
                        val seconds = Regex("\\d+").find(result.message)?.value?.toIntOrNull() ?: 30
                        startRateLimitCountdown(seconds)
                    }
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = result.message,
                            rateLimited = isRateLimited
                        )
                    }
                }
                else -> {}
            }
        }
    }

    private fun startRateLimitCountdown(seconds: Int) {
        rateLimitJob?.cancel()
        rateLimitJob = viewModelScope.launch {
            for (remaining in seconds downTo 0) {
                _uiState.update {
                    it.copy(
                        rateLimited = remaining > 0,
                        rateLimitRemainingSeconds = remaining
                    )
                }
                if (remaining > 0) delay(1000)
            }
        }
    }

    fun resetState() {
        _uiState.update { PingUiState() }
    }

    override fun onCleared() {
        super.onCleared()
        rateLimitJob?.cancel()
    }
}

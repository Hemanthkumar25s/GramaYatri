package com.gramayatri.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gramayatri.data.model.*
import com.gramayatri.data.repository.FirebaseRepository
import com.gramayatri.data.repository.LocalCacheRepository
import com.gramayatri.domain.usecase.EtaCalculator
import com.gramayatri.utils.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository,
    private val localCacheRepository: LocalCacheRepository,
    private val etaCalculator: EtaCalculator,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Track current ping + route observation jobs so we can restart them
    private var pingObserveJob: Job? = null
    private var alertObserveJob: Job? = null
    private var etaRefreshJob: Job? = null
    private var liveLocationObserveJob: Job? = null

    init {
        observeNetwork()
        loadRoutes()
    }

    private fun observeNetwork() {
        networkMonitor.isOnline
            .onEach { isOnline ->
                _uiState.update { it.copy(isOffline = !isOnline) }
                // When coming back online, refresh
                if (isOnline && _uiState.value.routes.isEmpty()) loadRoutes()
            }
            .launchIn(viewModelScope)
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            firebaseRepository.observeRoutes()
                .collect { result ->
                    when (result) {
                        is NetworkResult.Loading -> _uiState.update { it.copy(isLoading = true) }
                        is NetworkResult.Success -> {
                            val routes = result.data.filter { it.id.isNotBlank() && it.isActive }
                            val preferredRouteId = localCacheRepository.userPreferencesFlow
                                .first().preferredRouteId

                            val selectedRoute = routes.find { it.id == preferredRouteId }
                                ?: routes.firstOrNull()

                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    routes = routes,
                                    selectedRoute = selectedRoute,
                                    error = null,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            }

                            selectedRoute?.let { switchRoute(it) }
                        }
                        is NetworkResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = result.message
                                )
                            }
                        }
                    }
                }
        }
    }

    fun switchRoute(route: Route) {
        if (route.id.isBlank()) return

        _uiState.update { it.copy(selectedRoute = route, stopEtas = emptyList()) }

        // Cancel previous observers
        pingObserveJob?.cancel()
        alertObserveJob?.cancel()
        etaRefreshJob?.cancel()
        liveLocationObserveJob?.cancel()

        // Start new observers for selected route
        observePing(route)
        observeAlerts(route.id)
        observeLiveLocation(route.id)
        startEtaRefreshTimer()
    }

    private fun observeLiveLocation(routeId: String) {
        liveLocationObserveJob = viewModelScope.launch {
            firebaseRepository.observeLiveLocation(routeId)
                .collect { location ->
                    _uiState.update { it.copy(liveBusLocation = location) }
                }
        }
    }

    private fun observePing(route: Route) {
        pingObserveJob = viewModelScope.launch {
            firebaseRepository.observeActivePing(route.id)
                .collect { ping ->
                    _uiState.update { it.copy(activePing = ping) }
                    recalculateEtas(route, ping)
                }
        }
    }

    private fun observeAlerts(routeId: String) {
        alertObserveJob = viewModelScope.launch {
            firebaseRepository.observeAlerts(routeId)
                .collect { alerts ->
                    _uiState.update { it.copy(activeAlert = alerts.firstOrNull()) }
                }
        }
    }

    /**
     * Refreshes ETAs every 30 seconds so the "X min" countdown stays accurate
     * without needing a new Firebase ping.
     */
    private fun startEtaRefreshTimer() {
        etaRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                val state = _uiState.value
                state.selectedRoute?.let { route ->
                    recalculateEtas(route, state.activePing)
                }
            }
        }
    }

    private fun recalculateEtas(route: Route, ping: BusPing?) {
        val etas = etaCalculator.calculateEtas(route, ping)
        _uiState.update { it.copy(stopEtas = etas) }
    }

    fun confirmPing(pingId: String, confirmed: Boolean) {
        val routeId = _uiState.value.selectedRoute?.id ?: return
        viewModelScope.launch {
            firebaseRepository.confirmPing(routeId, pingId, confirmed)
        }
    }

    fun retry() {
        _uiState.update { it.copy(error = null) }
        loadRoutes()
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}

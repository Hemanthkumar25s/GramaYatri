package com.gramayatri.ui.screens.home

import androidx.lifecycle.SavedStateHandle
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
import kotlin.math.*

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository,
    private val localCacheRepository: LocalCacheRepository,
    private val etaCalculator: EtaCalculator,
    private val networkMonitor: NetworkMonitor,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _proximityAlert = MutableStateFlow<String?>(null)
    val proximityAlert: StateFlow<String?> = _proximityAlert.asStateFlow()

    // Track current ping + route observation jobs so we can restart them
    private var pingObserveJob: Job? = null
    private var alertObserveJob: Job? = null
    private var etaRefreshJob: Job? = null
    private var liveLocationObserveJob: Job? = null
    private var proximityObserveJob: Job? = null
    private var hasStarted = false

    fun dismissProximityAlert() {
        _proximityAlert.value = null
    }

    fun start() {
        if (hasStarted) return
        hasStarted = true
        observeNetwork()
        viewModelScope.launch {
            delay(300)
            loadRoutes()
            observeProximityForPreferredStop()
        }
        observeSelectedRouteFromSearch()
    }

    private fun observeSelectedRouteFromSearch() {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>("selectedRouteId", null)
                .collect { routeId ->
                    if (!routeId.isNullOrBlank()) {
                        _uiState.value.routes.find { it.id == routeId }?.let { route ->
                            switchRoute(route)
                        }
                        // Clear to prevent re-processing on config change
                        savedStateHandle["selectedRouteId"] = null
                    }
                }
        }
    }

    private fun observeProximityForPreferredStop() {
        proximityObserveJob = viewModelScope.launch {
            val prefs = localCacheRepository.userPreferencesFlow.first()
            if (prefs.preferredRouteId.isBlank() || prefs.preferredStopId.isBlank()) return@launch

            // Get stop coordinates
            val routesResult = firebaseRepository.observeRoutes().first { it !is NetworkResult.Loading }
            val routes = when (routesResult) {
                is NetworkResult.Success -> routesResult.data
                else -> emptyList()
            }
            val route = routes.find { it.id == prefs.preferredRouteId } ?: return@launch
            val stop = route.stops.find { it.id == prefs.preferredStopId } ?: return@launch

            // Real-time proximity check using live location updates
            firebaseRepository.observeLiveLocation(prefs.preferredRouteId)
                .collect { location ->
                    if (location != null && location.isActive) {
                        val distance = calculateDistance(
                            location.lat, location.lng,
                            stop.lat, stop.lng
                        )
                        if (distance <= 3.0) {  // within 3 km
                            _proximityAlert.value = "🚌 Bus is ${String.format("%.1f", distance)} km from ${stop.name}!"
                        } else {
                            _proximityAlert.value = null
                        }
                    }
                }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
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
        observeAlerts(route.id)
        observeLiveLocation(route.id)
        startEtaRefreshTimer()
    }

    private fun observeLiveLocation(routeId: String) {
        liveLocationObserveJob = viewModelScope.launch {
            firebaseRepository.observeLiveLocation(routeId)
                .collect { location ->
                    _uiState.update { it.copy(liveBusLocation = location) }
                    val route = _uiState.value.selectedRoute
                    if (route != null && location != null) {
                        recalculateEtas(route, location)
                    } else if (route != null) {
                        clearEtas(route)
                    }
                }
        }
    }

    private fun observePing(route: Route) {
        pingObserveJob = viewModelScope.launch {
            firebaseRepository.observeActivePing(route.id)
                .collect { ping ->
                    _uiState.update { it.copy(activePing = ping) }
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
                    state.liveBusLocation?.let { liveLocation ->
                        recalculateEtas(route, liveLocation)
                    } ?: clearEtas(route)
                }
            }
        }
    }

    private fun recalculateEtas(route: Route, liveLocation: LiveBusLocation) {
        val etas = etaCalculator.calculateEtas(route, liveLocation)
        _uiState.update { it.copy(stopEtas = etas) }
    }

    private fun clearEtas(route: Route) {
        _uiState.update {
            it.copy(
                stopEtas = route.stops.map { stop ->
                    StopEta(stop = stop, etaMinutes = null, etaTimestamp = null)
                }
            )
        }
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

package com.gramayatri.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gramayatri.data.model.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RouteSearchUiState(
    val fromQuery: String = "",
    val toQuery: String = "",
    val fromStop: String = "",
    val toStop: String = "",
    val selectedDate: Long? = null,
    val selectedTime: Int? = null,  // minutes from midnight
    val matchingRoutes: List<Route> = emptyList(),
    val hasSearched: Boolean = false,
    val showFromSuggestions: Boolean = false,
    val showToSuggestions: Boolean = false,
    val fromSuggestions: List<String> = emptyList(),
    val toSuggestions: List<String> = emptyList()
)

@HiltViewModel
class RouteSearchViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RouteSearchUiState())
    val uiState: StateFlow<RouteSearchUiState> = _uiState.asStateFlow()

    private var fromSearchJob: Job? = null
    private var toSearchJob: Job? = null

    /**
     * Called when user types in the "From" field.
     * Debounces 300ms then computes suggestions from all available routes.
     */
    fun updateFromQuery(query: String, routes: List<Route> = emptyList()) {
        _uiState.update { it.copy(fromQuery = query, fromStop = "") }
        fromSearchJob?.cancel()
        if (query.length >= 2) {
            fromSearchJob = viewModelScope.launch {
                delay(300)
                updateFromSuggestions(query, routes)
            }
        } else {
            _uiState.update { it.copy(showFromSuggestions = false, fromSuggestions = emptyList()) }
        }
    }

    /**
     * Called when user types in the "To" field.
     * Debounces 300ms then computes suggestions from all available routes.
     */
    fun updateToQuery(query: String, routes: List<Route> = emptyList()) {
        _uiState.update { it.copy(toQuery = query, toStop = "") }
        toSearchJob?.cancel()
        if (query.length >= 2) {
            toSearchJob = viewModelScope.launch {
                delay(300)
                updateToSuggestions(query, routes)
            }
        } else {
            _uiState.update { it.copy(showToSuggestions = false, toSuggestions = emptyList()) }
        }
    }

    fun selectFromStop(stop: String) {
        _uiState.update {
            it.copy(
                fromQuery = stop,
                fromStop = stop,
                showFromSuggestions = false,
                fromSuggestions = emptyList()
            )
        }
    }

    fun selectToStop(stop: String) {
        _uiState.update {
            it.copy(
                toQuery = stop,
                toStop = stop,
                showToSuggestions = false,
                toSuggestions = emptyList()
            )
        }
    }

    fun swapFromTo() {
        _uiState.update {
            it.copy(
                fromQuery = it.toQuery,
                toQuery = it.fromQuery,
                fromStop = it.toStop,
                toStop = it.fromStop
            )
        }
    }

    fun clearFrom() {
        _uiState.update { it.copy(fromQuery = "", fromStop = "", showFromSuggestions = false) }
    }

    fun clearTo() {
        _uiState.update { it.copy(toQuery = "", toStop = "", showToSuggestions = false) }
    }

    fun selectDate(dateMillis: Long) {
        _uiState.update { it.copy(selectedDate = dateMillis) }
    }

    fun selectTime(minutesFromMidnight: Int) {
        _uiState.update { it.copy(selectedTime = minutesFromMidnight) }
    }

    fun searchRoutes(routes: List<Route>) {
        val state = _uiState.value
        val from = state.fromStop.trim().lowercase()
        val to = state.toStop.trim().lowercase()

        if (from.isBlank() || to.isBlank()) return

        val matching = routes.filter { route ->
            val originMatch = route.origin.lowercase().contains(from)
            val originExact = route.origin.lowercase() == from
            val destMatch = route.destination.lowercase().contains(to)
            val destExact = route.destination.lowercase() == to

            val fromStopMatch = route.stops.any { it.name.lowercase().contains(from) }
            val toStopMatch = route.stops.any { it.name.lowercase().contains(to) }

            val fromIndex = route.stops.indexOfFirst { it.name.lowercase().contains(from) }
            val toIndex = route.stops.indexOfLast { it.name.lowercase().contains(to) }

            (originExact && (destMatch || toStopMatch)) ||
                    (originMatch && destMatch) ||
                    (fromStopMatch && toStopMatch && fromIndex >= 0 && toIndex >= 0 && fromIndex < toIndex) ||
                    (originMatch && toStopMatch) ||
                    (fromStopMatch && destMatch)
        }

        _uiState.update {
            it.copy(
                matchingRoutes = matching,
                hasSearched = true
            )
        }
    }

    private fun updateFromSuggestions(query: String, routes: List<Route>) {
        if (query.length < 2) {
            _uiState.update { it.copy(showFromSuggestions = false, fromSuggestions = emptyList()) }
            return
        }
        val q = query.lowercase()
        val suggestions = mutableSetOf<String>()
        routes.forEach { route ->
            if (route.origin.lowercase().contains(q)) suggestions.add(route.origin)
            route.stops.forEach { stop ->
                if (stop.name.lowercase().contains(q)) suggestions.add(stop.name)
            }
        }
        _uiState.update {
            it.copy(
                showFromSuggestions = suggestions.isNotEmpty(),
                fromSuggestions = suggestions.take(8)
            )
        }
    }

    private fun updateToSuggestions(query: String, routes: List<Route>) {
        if (query.length < 2) {
            _uiState.update { it.copy(showToSuggestions = false, toSuggestions = emptyList()) }
            return
        }
        val q = query.lowercase()
        val suggestions = mutableSetOf<String>()
        routes.forEach { route ->
            if (route.destination.lowercase().contains(q)) suggestions.add(route.destination)
            route.stops.forEach { stop ->
                if (stop.name.lowercase().contains(q)) suggestions.add(stop.name)
            }
        }
        _uiState.update {
            it.copy(
                showToSuggestions = suggestions.isNotEmpty(),
                toSuggestions = suggestions.take(8)
            )
        }
    }
}

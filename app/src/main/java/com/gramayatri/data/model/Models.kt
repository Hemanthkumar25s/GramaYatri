package com.gramayatri.data.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

// ─── Ping Model ────────────────────────────────────────────────────────────

@Serializable
data class BusPing(
    val id: String = "",
    val routeId: String = "",
    val stopId: String = "",
    val stopName: String = "",
    val stopSequence: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val userName: String = "",
    val deviceId: String = "",
    val type: PingType = PingType.BUS_AT_STOP,
    val timestamp: Long = 0L,
    val isActive: Boolean = true,
    val confirmationCount: Int = 0,
    val denialCount: Int = 0
)

enum class PingType(val label: String, val emoji: String) {
    BUS_AT_STOP("Bus arrived at stop", "🚌"),
    ON_THE_BUS("I'm on the bus", "🧑"),
    BUS_LEFT_STOP("Bus just left", "💨"),
    BUS_DELAYED("Bus is delayed", "⏱️"),
    BUS_CANCELLED("Bus cancelled", "❌"),
    EXTRA_BUS("Extra bus running", "➕")
}

// ─── ETA Model ─────────────────────────────────────────────────────────────

data class StopEta(
    val stop: Stop,
    val etaMinutes: Int?,           // null = no live data
    val etaTimestamp: Long?,        // absolute epoch millis
    val isBusPassed: Boolean = false,
    val isCurrentLocation: Boolean = false, // bus is here right now
    val confidence: EtaConfidence = EtaConfidence.NONE
)

enum class EtaConfidence { HIGH, MEDIUM, LOW, NONE }

// ─── Alert Model ───────────────────────────────────────────────────────────

@Serializable
data class BusAlert(
    val id: String = "",
    val routeId: String = "",
    val routeName: String = "",
    val type: AlertType = AlertType.DELAY,
    val message: String = "",
    val timestamp: Long = 0L,
    val isActive: Boolean = true
)

enum class AlertType(val label: String, val emoji: String) {
    CANCELLED("Cancelled", "❌"),
    DELAY("Delayed", "⏱️"),
    EXTRA("Extra Bus", "➕"),
    ROUTE_CHANGE("Route Change", "🔄"),
    GENERAL("Notice", "📢")
}

// ─── Network State ─────────────────────────────────────────────────────────

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}

// ─── UI State ──────────────────────────────────────────────────────────────

data class HomeUiState(
    val isLoading: Boolean = true,
    val routes: List<Route> = emptyList(),
    val selectedRoute: Route? = null,
    val stopEtas: List<StopEta> = emptyList(),
    val activeAlert: BusAlert? = null,
    val activePing: BusPing? = null,
    val liveBusLocation: LiveBusLocation? = null,
    val isOffline: Boolean = false,
    val lastSyncTime: Long? = null,
    val error: String? = null
)

data class PingUiState(
    val isSubmitting: Boolean = false,
    val selectedStop: Stop? = null,
    val selectedPingType: PingType = PingType.BUS_AT_STOP,
    val success: Boolean = false,
    val error: String? = null,
    val rateLimited: Boolean = false,
    val rateLimitRemainingSeconds: Int = 0
)

data class AlertsUiState(
    val isLoading: Boolean = true,
    val alerts: List<BusAlert> = emptyList(),
    val error: String? = null
)

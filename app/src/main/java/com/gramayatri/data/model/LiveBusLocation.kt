package com.gramayatri.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveBusLocation(
    val routeId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val accuracy: Float = 0f,
    val timestamp: Long = 0L,
    val reporterName: String = "",
    val driverName: String = "",
    val driverId: String = "",
    val isActive: Boolean = false,
    val tripId: String = ""
)

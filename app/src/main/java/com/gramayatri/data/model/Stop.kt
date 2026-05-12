package com.gramayatri.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Stop(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val sequence: Int = 0,
    val distanceFromOriginKm: Double = 0.0,
    val estimatedTime: Int = 0,  // minutes from start
    val avgTravelTimeFromPrevMinutes: Int = 0   // minutes from previous stop
)


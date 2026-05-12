package com.gramayatri.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Route(
    val id: String = "",
    val name: String = "",
    val number: String = "",
    val origin: String = "",
    val destination: String = "",
    val distance: Int = 0,
    val duration: Int = 0,
    val isActive: Boolean = true,
    val operator: String = "KSRTC",
    val stops: List<Stop> = emptyList(),
    val firstStopLat: Double = 0.0,  // For lazy loading thumbnail
    val firstStopLng: Double = 0.0,
    val rating: Double = 4.5,
    val reviews: Int = 0,
    val operatorId: String = "ksrtc",      // Multi-tenant key
    val operatorName: String = "KSRTC",
    val operatorPhone: String = "",
    val subscriptionTier: String = "basic"
)

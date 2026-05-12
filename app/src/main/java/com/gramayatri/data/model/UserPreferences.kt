package com.gramayatri.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val userName: String = "",
    val preferredStopId: String = "",
    val preferredRouteId: String = "",
    val deviceId: String = "",
    val hasCompletedOnboarding: Boolean = false,
    val notificationsEnabled: Boolean = true
)

package com.gramayatri.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val userName: String = "",
    val preferredStopId: String = "",
    val preferredRouteId: String = "",
    val deviceId: String = "",
    val hasCompletedOnboarding: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val role: UserRole = UserRole.PASSENGER,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val hasSelectedLanguage: Boolean = false,
    val hasSeenIntro: Boolean = false
)

enum class UserRole {
    PASSENGER
}

enum class AppLanguage {
    ENGLISH,
    KANNADA
}

package com.gramayatri.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TicketMachineSession(
    val routeId: String = "",
    val tripId: String = "",
    val machineId: String = "",
    val verificationToken: String = "",
    val qrPayload: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val isActive: Boolean = true
)

@Serializable
data class DriverVerification(
    val routeId: String = "",
    val tripId: String = "",
    val driverId: String = "",
    val machineId: String = "",
    val status: VerificationStatus = VerificationStatus.PENDING,
    val distanceFromMachineMeters: Double = 0.0,
    val verifiedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val reason: String = ""
)

enum class VerificationStatus {
    PENDING,
    VERIFIED,
    REJECTED,
    EXPIRED
}

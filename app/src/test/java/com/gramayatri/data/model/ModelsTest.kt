package com.gramayatri.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for GramaYatri data models.
 *
 * Covers:
 * - [NetworkResult] sealed class
 * - [StopEta] data class
 * - [HomeUiState], [PingUiState], [AlertsUiState] state classes
 * - [PingType], [AlertType], [EtaConfidence], [VerificationStatus] enums
 * - [BusPing], [BusAlert], [LiveBusLocation], [TicketMachineSession] defaults
 * - [UserPreferences], [UserRole], [AppLanguage]
 * - [Route] default values
 * - [Stop] default values
 */
class ModelsTest {

    // ─── NetworkResult ───────────────────────────────────────────────────

    @Test
    fun `NetworkResult Success holds data`() {
        val result: NetworkResult<String> = NetworkResult.Success("hello")
        assertTrue(result is NetworkResult.Success)
        assertEquals("hello", (result as NetworkResult.Success).data)
    }

    @Test
    fun `NetworkResult Error holds message and cause`() {
        val cause = RuntimeException("test error")
        val result = NetworkResult.Error("Something went wrong", cause)

        assertTrue(result is NetworkResult.Error)
        assertEquals("Something went wrong", (result as NetworkResult.Error).message)
        assertEquals(cause, result.cause)
    }

    @Test
    fun `NetworkResult Error allows null cause`() {
        val result = NetworkResult.Error("Error without cause")
        assertTrue(result is NetworkResult.Error)
        assertEquals("Error without cause", (result as NetworkResult.Error).message)
        assertNull(result.cause)
    }

    @Test
    fun `NetworkResult Loading is singleton`() {
        assertTrue(NetworkResult.Loading is NetworkResult<*>)
    }

    @Test
    fun `NetworkResult when exhaustive`() {
        val results: List<NetworkResult<Int>> = listOf(
            NetworkResult.Success(42),
            NetworkResult.Error("fail"),
            NetworkResult.Loading
        )

        val outputs = results.map { result ->
            when (result) {
                is NetworkResult.Success -> "data:${result.data}"
                is NetworkResult.Error -> "err:${result.message}"
                is NetworkResult.Loading -> "loading"
            }
        }

        assertEquals(listOf("data:42", "err:fail", "loading"), outputs)
    }

    // ─── StopEta ─────────────────────────────────────────────────────────

    @Test
    fun `StopEta defaults are reasonable`() {
        val stop = Stop(id = "s1", name = "Test")
        val eta = StopEta(stop = stop, etaMinutes = null, etaTimestamp = null)

        assertEquals(stop, eta.stop)
        assertNull(eta.etaMinutes)
        assertNull(eta.etaTimestamp)
        assertFalse(eta.isBusPassed)
        assertFalse(eta.isCurrentLocation)
        assertEquals(EtaConfidence.NONE, eta.confidence)
    }

    @Test
    fun `StopEta can be created with all values`() {
        val stop = Stop(id = "s1", name = "Test", sequence = 3)
        val eta = StopEta(
            stop = stop,
            etaMinutes = 15,
            etaTimestamp = 1_000_000_000_000L,
            isBusPassed = false,
            isCurrentLocation = false,
            confidence = EtaConfidence.HIGH
        )

        assertEquals(15, eta.etaMinutes)
        assertEquals(1_000_000_000_000L, eta.etaTimestamp)
        assertEquals(EtaConfidence.HIGH, eta.confidence)
    }

    // ─── PingType Enum ───────────────────────────────────────────────────

    @Test
    fun `PingType has correct labels and emojis`() {
        assertEquals("Bus arrived at stop", PingType.BUS_AT_STOP.label)
        assertEquals("🚌", PingType.BUS_AT_STOP.emoji)
        assertEquals("I'm on the bus", PingType.ON_THE_BUS.label)
        assertEquals("🧑", PingType.ON_THE_BUS.emoji)
        assertEquals("Bus just left", PingType.BUS_LEFT_STOP.label)
        assertEquals("💨", PingType.BUS_LEFT_STOP.emoji)
    }

    @Test
    fun `PingType has 6 entries`() {
        assertEquals(6, PingType.entries.size)
    }

    @Test
    fun `PingType valueOf round-trips`() {
        PingType.entries.forEach { type ->
            assertEquals(type, PingType.valueOf(type.name))
        }
    }

    // ─── AlertType Enum ──────────────────────────────────────────────────

    @Test
    fun `AlertType has correct labels and emojis`() {
        assertEquals("Cancelled", AlertType.CANCELLED.label)
        assertEquals("❌", AlertType.CANCELLED.emoji)
        assertEquals("Delayed", AlertType.DELAY.label)
        assertEquals("⏱️", AlertType.DELAY.emoji)
        assertEquals("Extra Bus", AlertType.EXTRA.label)
        assertEquals("➕", AlertType.EXTRA.emoji)
    }

    @Test
    fun `AlertType has 5 entries`() {
        assertEquals(5, AlertType.entries.size)
    }

    // ─── EtaConfidence Enum ──────────────────────────────────────────────

    @Test
    fun `EtaConfidence has correct ordering`() {
        val values = EtaConfidence.entries
        assertEquals(4, values.size)
        assertEquals(EtaConfidence.HIGH, values[0])
        assertEquals(EtaConfidence.MEDIUM, values[1])
        assertEquals(EtaConfidence.LOW, values[2])
        assertEquals(EtaConfidence.NONE, values[3])
    }

    // ─── VerificationStatus Enum ─────────────────────────────────────────

    @Test
    fun `VerificationStatus has 4 entries`() {
        assertEquals(4, VerificationStatus.entries.size)
    }

    @Test
    fun `VerificationStatus valueOf round-trips`() {
        VerificationStatus.entries.forEach { status ->
            assertEquals(status, VerificationStatus.valueOf(status.name))
        }
    }

    // ─── BusPing Defaults ────────────────────────────────────────────────

    @Test
    fun `BusPing default values are reasonable`() {
        val ping = BusPing()
        assertEquals("", ping.id)
        assertEquals("", ping.routeId)
        assertEquals(PingType.BUS_AT_STOP, ping.type)
        assertEquals(0L, ping.timestamp)
        assertTrue(ping.isActive)
        assertEquals(0, ping.confirmationCount)
        assertEquals(0, ping.denialCount)
    }

    // ─── BusAlert Defaults ───────────────────────────────────────────────

    @Test
    fun `BusAlert default values are reasonable`() {
        val alert = BusAlert()
        assertEquals("", alert.id)
        assertEquals(AlertType.DELAY, alert.type)
        assertEquals("", alert.message)
        assertTrue(alert.isActive)
    }

    // ─── LiveBusLocation Defaults ────────────────────────────────────────

    @Test
    fun `LiveBusLocation default values are reasonable`() {
        val loc = LiveBusLocation()
        assertEquals(0.0, loc.lat, 0.001)
        assertEquals(0.0, loc.lng, 0.001)
        assertEquals(0f, loc.speed)
        assertEquals(0f, loc.bearing)
        assertFalse(loc.isActive)
        assertEquals(LocationSource.PASSENGER, loc.source)
    }

    // ─── TicketMachineSession Defaults ───────────────────────────────────

    @Test
    fun `TicketMachineSession default values are reasonable`() {
        val session = TicketMachineSession()
        assertEquals("", session.routeId)
        assertEquals("", session.tripId)
        assertEquals("", session.machineId)
        assertEquals("", session.verificationToken)
        assertEquals(0.0, session.lat, 0.001)
        assertEquals(0.0, session.lng, 0.001)
        assertTrue(session.isActive)
    }

    // ─── DriverVerification Defaults ─────────────────────────────────────

    @Test
    fun `DriverVerification default values are reasonable`() {
        val dv = DriverVerification()
        assertEquals("", dv.routeId)
        assertEquals(VerificationStatus.PENDING, dv.status)
        assertEquals(0.0, dv.distanceFromMachineMeters, 0.001)
        assertEquals("", dv.reason)
    }

    // ─── UserPreferences & Enums ─────────────────────────────────────────

    @Test
    fun `UserPreferences default values`() {
        val prefs = UserPreferences()
        assertEquals("", prefs.userName)
        assertEquals("", prefs.preferredStopId)
        assertEquals("", prefs.preferredRouteId)
        assertFalse(prefs.hasCompletedOnboarding)
        assertTrue(prefs.notificationsEnabled)
        assertEquals(UserRole.PASSENGER, prefs.role)
        assertEquals(AppLanguage.ENGLISH, prefs.language)
    }

    @Test
    fun `UserRole has only PASSENGER`() {
        assertEquals(1, UserRole.entries.size)
        assertEquals(UserRole.PASSENGER, UserRole.valueOf("PASSENGER"))
    }

    @Test
    fun `AppLanguage has ENGLISH and KANNADA`() {
        assertEquals(2, AppLanguage.entries.size)
        assertEquals(AppLanguage.ENGLISH, AppLanguage.valueOf("ENGLISH"))
        assertEquals(AppLanguage.KANNADA, AppLanguage.valueOf("KANNADA"))
    }

    // ─── Route Defaults ──────────────────────────────────────────────────

    @Test
    fun `Route default values`() {
        val route = Route()
        assertEquals("", route.id)
        assertTrue(route.isActive)
        assertEquals("KSRTC", route.operator)
        assertEquals(4.5, route.rating, 0.001)
        assertEquals("ksrtc", route.operatorId)
        assertEquals("basic", route.subscriptionTier)
    }

    @Test
    fun `Route copy creates independent instance`() {
        val route = Route(id = "KSRTC-1", name = "Original", stops = listOf(Stop()))
        val copy = route.copy(name = "Modified")
        assertEquals("Original", route.name)
        assertEquals("Modified", copy.name)
        assertEquals(route.id, copy.id)
    }

    // ─── Stop Defaults ───────────────────────────────────────────────────

    @Test
    fun `Stop default values`() {
        val stop = Stop()
        assertEquals("", stop.id)
        assertEquals("", stop.name)
        assertEquals(0.0, stop.lat, 0.001)
        assertEquals(0.0, stop.lng, 0.001)
        assertEquals(0, stop.sequence)
        assertEquals(0, stop.estimatedTime)
        assertEquals(0, stop.avgTravelTimeFromPrevMinutes)
    }

    // ─── UI State Classes ────────────────────────────────────────────────

    @Test
    fun `HomeUiState defaults`() {
        val state = HomeUiState()
        assertTrue(state.isLoading)
        assertTrue(state.routes.isEmpty())
        assertNull(state.selectedRoute)
        assertTrue(state.stopEtas.isEmpty())
        assertNull(state.activeAlert)
        assertNull(state.activePing)
        assertNull(state.liveBusLocation)
        assertFalse(state.isOffline)
        assertNull(state.lastSyncTime)
        assertNull(state.error)
    }

    @Test
    fun `PingUiState defaults`() {
        val state = PingUiState()
        assertFalse(state.isSubmitting)
        assertNull(state.selectedStop)
        assertEquals(PingType.BUS_AT_STOP, state.selectedPingType)
        assertFalse(state.success)
        assertNull(state.error)
        assertFalse(state.rateLimited)
        assertEquals(0, state.rateLimitRemainingSeconds)
    }

    @Test
    fun `AlertsUiState defaults`() {
        val state = AlertsUiState()
        assertTrue(state.isLoading)
        assertTrue(state.alerts.isEmpty())
        assertNull(state.error)
    }

    // ─── LocationSource Enum ─────────────────────────────────────────────

    @Test
    fun `LocationSource has 3 entries`() {
        assertEquals(3, LocationSource.entries.size)
        assertEquals(LocationSource.PASSENGER, LocationSource.valueOf("PASSENGER"))
        assertEquals(LocationSource.DRIVER, LocationSource.valueOf("DRIVER"))
        assertEquals(LocationSource.TICKET_MACHINE, LocationSource.valueOf("TICKET_MACHINE"))
    }
}

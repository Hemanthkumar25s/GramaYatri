package com.gramayatri.data.repository

import com.gramayatri.data.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the top-level [toMap] extension functions defined in [FirebaseRepository].
 *
 * These tests verify that model-to-map serialization works correctly,
 * which is critical for Firebase read/write operations.
 */
class ModelsExtTest {

    @Test
    fun `BusPing toMap() includes all fields`() {
        val ping = BusPing(
            id = "ping-abc",
            routeId = "KSRTC-123",
            stopId = "stop-5",
            stopName = "Test Stop",
            stopSequence = 5,
            lat = 12.34,
            lng = 56.78,
            userName = "TestUser",
            deviceId = "device-xyz",
            type = PingType.BUS_AT_STOP,
            timestamp = 1_000_000_000_000L,
            isActive = true,
            confirmationCount = 3,
            denialCount = 1
        )

        val map = ping.toMap()

        assertEquals("KSRTC-123", map["routeId"])
        assertEquals("stop-5", map["stopId"])
        assertEquals("Test Stop", map["stopName"])
        assertEquals(5, map["stopSequence"])
        assertEquals(12.34, map["lat"])
        assertEquals(56.78, map["lng"])
        assertEquals("TestUser", map["userName"])
        assertEquals("device-xyz", map["deviceId"])
        assertEquals("BUS_AT_STOP", map["type"])
        assertEquals(1_000_000_000_000L, map["timestamp"])
        assertEquals(true, map["isActive"])
        assertEquals(3, map["confirmationCount"])
        assertEquals(1, map["denialCount"])
        assertEquals(13, map.size)  // All fields present (id is excluded — it's the Firebase key)
    }

    @Test
    fun `BusPing toMap() handles default values`() {
        val ping = BusPing()
        val map = ping.toMap()

        assertEquals("", map["routeId"])
        assertEquals("", map["stopId"])
        assertEquals("", map["stopName"])
        assertEquals(0, map["stopSequence"])
        assertEquals(0.0, map["lat"])
        assertEquals(0.0, map["lng"])
        assertEquals("", map["userName"])
        assertEquals("", map["deviceId"])
        assertEquals("BUS_AT_STOP", map["type"])
        assertEquals(0L, map["timestamp"])
        assertEquals(true, map["isActive"])
        assertEquals(0, map["confirmationCount"])
        assertEquals(0, map["denialCount"])
    }

    @Test
    fun `BusPing toMap() uses correct ping type enum name`() {
        val types = PingType.entries.toTypedArray()
        types.forEach { type ->
            val ping = BusPing(type = type)
            val map = ping.toMap()
            assertEquals(type.name, map["type"])
        }
    }

    @Test
    fun `LiveBusLocation toMap() includes all fields`() {
        val location = LiveBusLocation(
            routeId = "KSRTC-123",
            lat = 12.34,
            lng = 56.78,
            speed = 45.5f,
            bearing = 180f,
            accuracy = 25f,
            timestamp = 1_000_000_000_000L,
            reporterName = "User1",
            driverName = "Driver1",
            driverId = "driver-abc",
            isActive = true,
            tripId = "trip-xyz",
            source = LocationSource.DRIVER
        )

        val map = location.toMap()

        assertEquals("KSRTC-123", map["routeId"])
        assertEquals(12.34, map["lat"])
        assertEquals(56.78, map["lng"])
        assertEquals(45.5f, map["speed"])
        assertEquals(180f, map["bearing"])
        assertEquals(25f, map["accuracy"])
        assertEquals(1_000_000_000_000L, map["timestamp"])
        assertEquals("User1", map["reporterName"])
        assertEquals("Driver1", map["driverName"])
        assertEquals("driver-abc", map["driverId"])
        assertEquals(true, map["isActive"])
        assertEquals("trip-xyz", map["tripId"])
        assertEquals("DRIVER", map["source"])
        assertEquals(13, map.size)
    }

    @Test
    fun `LiveBusLocation toMap() uses correct source enum`() {
        val sources = LocationSource.entries.toTypedArray()
        sources.forEach { source ->
            val location = LiveBusLocation(source = source)
            val map = location.toMap()
            assertEquals(source.name, map["source"])
        }
    }

    @Test
    fun `BusAlert toMap() includes all fields`() {
        val alert = BusAlert(
            id = "alert-1",
            routeId = "KSRTC-123",
            routeName = "Test Route",
            type = AlertType.DELAY,
            message = "Bus delayed by 15 min",
            timestamp = 1_000_000_000_000L,
            isActive = true
        )

        val map = alert.toMap()

        assertEquals("KSRTC-123", map["routeId"])
        assertEquals("Test Route", map["routeName"])
        assertEquals("DELAY", map["type"])
        assertEquals("Bus delayed by 15 min", map["message"])
        assertEquals(1_000_000_000_000L, map["timestamp"])
        assertEquals(true, map["isActive"])
        assertEquals(6, map.size)
    }

    @Test
    fun `BusAlert toMap() uses correct alert type enum name`() {
        val types = AlertType.entries.toTypedArray()
        types.forEach { type ->
            val alert = BusAlert(type = type)
            val map = alert.toMap()
            assertEquals(type.name, map["type"])
        }
    }

    @Test
    fun `TicketMachineSession toMap() includes all fields`() {
        val session = TicketMachineSession(
            routeId = "KSRTC-123",
            tripId = "KSRTC-123-2026-05-30",
            machineId = "ETM-001",
            verificationToken = "ABC123XYZ9",
            qrPayload = "gramayatri://driver-verify?routeId=KSRTC-123&...",
            lat = 12.34,
            lng = 56.78,
            createdAt = 1_000_000_000_000L,
            expiresAt = 1_000_600_000_000L,
            isActive = true
        )

        val map = session.toMap()

        assertEquals("KSRTC-123", map["routeId"])
        assertEquals("KSRTC-123-2026-05-30", map["tripId"])
        assertEquals("ETM-001", map["machineId"])
        assertEquals("ABC123XYZ9", map["verificationToken"])
        assertEquals(12.34, map["lat"])
        assertEquals(56.78, map["lng"])
        assertEquals(1_000_000_000_000L, map["createdAt"])
        assertEquals(1_000_600_000_000L, map["expiresAt"])
        assertEquals(true, map["isActive"])
        assertEquals(10, map.size)
    }

    @Test
    fun `DriverVerification toMap() includes all fields`() {
        val verification = DriverVerification(
            routeId = "KSRTC-123",
            tripId = "KSRTC-123-2026-05-30",
            driverId = "driver-abc",
            machineId = "ETM-001",
            status = VerificationStatus.VERIFIED,
            distanceFromMachineMeters = 50.0,
            verifiedAt = 1_000_000_000_000L,
            expiresAt = 1_000_600_000_000L,
            reason = "Driver is verified near ticket machine"
        )

        val map = verification.toMap()

        assertEquals("KSRTC-123", map["routeId"])
        assertEquals("KSRTC-123-2026-05-30", map["tripId"])
        assertEquals("driver-abc", map["driverId"])
        assertEquals("ETM-001", map["machineId"])
        assertEquals("VERIFIED", map["status"])
        assertEquals(50.0, map["distanceFromMachineMeters"])
        assertEquals(1_000_000_000_000L, map["verifiedAt"])
        assertEquals(1_000_600_000_000L, map["expiresAt"])
        assertEquals(9, map.size)
    }

    @Test
    fun `DriverVerification toMap() uses all verification statuses`() {
        val statuses = VerificationStatus.entries.toTypedArray()
        statuses.forEach { status ->
            val verification = DriverVerification(status = status)
            val map = verification.toMap()
            assertEquals(status.name, map["status"])
        }
    }
}

package com.gramayatri

import com.gramayatri.data.model.*
import com.gramayatri.domain.usecase.EtaCalculator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [EtaCalculator].
 *
 * Covers:
 * - Ping-based ETA calculation
 * - Live-location-based ETA calculation
 * - ETA formatting (standard & accessible)
 * - Confidence level calculation
 * - Edge cases (null ping, null location, empty route, single stop)
 */
class EtaCalculatorTest {

    private lateinit var calculator: EtaCalculator

    // A 5-stop route for testing
    private val routeStops = listOf(
        Stop(id = "stop-0", name = "Stop A", sequence = 0, lat = 12.0, lng = 77.0,
            distanceFromOriginKm = 0.0, avgTravelTimeFromPrevMinutes = 0),
        Stop(id = "stop-1", name = "Stop B", sequence = 1, lat = 12.01, lng = 77.01,
            distanceFromOriginKm = 1.0, avgTravelTimeFromPrevMinutes = 10),
        Stop(id = "stop-2", name = "Stop C", sequence = 2, lat = 12.02, lng = 77.02,
            distanceFromOriginKm = 2.5, avgTravelTimeFromPrevMinutes = 8),
        Stop(id = "stop-3", name = "Stop D", sequence = 3, lat = 12.03, lng = 77.03,
            distanceFromOriginKm = 4.0, avgTravelTimeFromPrevMinutes = 12),
        Stop(id = "stop-4", name = "Stop E", sequence = 4, lat = 12.04, lng = 77.04,
            distanceFromOriginKm = 5.5, avgTravelTimeFromPrevMinutes = 15)
    )

    private val testRoute = Route(
        id = "KSRTC-123",
        name = "Test Route",
        number = "123",
        origin = "Origin",
        destination = "Destination",
        stops = routeStops,
        distance = 6,
        duration = 45
    )

    @Before
    fun setUp() {
        calculator = EtaCalculator()
    }

    // ─── Ping-based ETA Tests ──────────────────────────────────────────

    @Test
    fun `calculateEtas with ping at stop C returns correct ETAs for all stops`() {
        val now = 1_000_000_000_000L
        val pingTime = now - 5 * 60 * 1000L  // 5 minutes ago

        val ping = BusPing(
            id = "ping-1",
            routeId = "KSRTC-123",
            stopId = "stop-2",  // Stop C
            stopName = "Stop C",
            stopSequence = 2,
            timestamp = pingTime,
            isActive = true
        )

        val etas = calculator.calculateEtas(testRoute, ping, now)

        assertEquals(5, etas.size)

        // Stop A (seq 0) — passed
        assertTrue(etas[0].isBusPassed)
        assertNull(etas[0].etaMinutes)

        // Stop B (seq 1) — passed
        assertTrue(etas[1].isBusPassed)
        assertNull(etas[1].etaMinutes)

        // Stop C (seq 2) — current location (ping was 5 min ago, < 5 min threshold? Let's check)
        // In the code: isCurrentLocation = pingAgeMinutes < 5
        // pingAgeMinutes = 5, so isCurrentLocation = false, isBusPassed = pingAgeMinutes >= 5 = true
        // Actually wait, 5 < 5 is false, so isCurrentLocation = false
        // And 5 >= 5 is true, so isBusPassed = true
        // etaMinutes = pingAgeMinutes = 5
        assertEquals(5, etas[2].etaMinutes)
        assertFalse(etas[2].isCurrentLocation)
        assertTrue(etas[2].isBusPassed)

        // Stop D (seq 3) — travel time from C(2) to D(3) = stop 3's avgTravelTimeFromPrevMinutes = 12
        // remainingMinutes = max(12 - 5, 0) = 7
        assertEquals(7, etas[3].etaMinutes)
        assertFalse(etas[3].isBusPassed)

        // Stop E (seq 4) — travel time from C(2) to E(4) = 12 + 15 = 27
        // remainingMinutes = max(27 - 5, 0) = 22
        assertEquals(22, etas[4].etaMinutes)
        assertFalse(etas[4].isBusPassed)
    }

    @Test
    fun `calculateEtas with ping at first stop marks it as current location`() {
        val now = 1_000_000_000_000L
        val pingTime = now - 2 * 60 * 1000L  // 2 minutes ago (< 5 min threshold)

        val ping = BusPing(
            id = "ping-1",
            routeId = "KSRTC-123",
            stopId = "stop-0",
            stopName = "Stop A",
            stopSequence = 0,
            timestamp = pingTime,
            isActive = true
        )

        val etas = calculator.calculateEtas(testRoute, ping, now)

        // Stop A is current location because pingAgeMinutes = 2 < 5
        assertTrue(etas[0].isCurrentLocation)
        assertFalse(etas[0].isBusPassed)
        assertEquals(2, etas[0].etaMinutes)

        // All other stops should have future ETAs
        (1..4).forEach { i ->
            assertFalse(etas[i].isBusPassed)
            assertNotNull(etas[i].etaMinutes)
        }
    }

    @Test
    fun `calculateEtas with null ping returns null ETAs for all stops`() {
        val etas = calculator.calculateEtas(testRoute, null as BusPing?)

        assertEquals(5, etas.size)
        etas.forEach { eta ->
            assertNull(eta.etaMinutes)
            assertNull(eta.etaTimestamp)
            assertFalse(eta.isCurrentLocation)
            assertFalse(eta.isBusPassed)
        }
    }

    @Test
    fun `calculateEtas with ping for unknown stopId returns null ETAs`() {
        val ping = BusPing(
            id = "ping-1",
            routeId = "KSRTC-123",
            stopId = "nonexistent-stop",
            timestamp = 1_000_000_000_000L,
            isActive = true
        )

        val etas = calculator.calculateEtas(testRoute, ping, 1_000_000_000_000L)

        etas.forEach { eta ->
            assertNull(eta.etaMinutes)
            assertNull(eta.etaTimestamp)
        }
    }

    @Test
    fun `calculateEtas with empty route returns empty list`() {
        val emptyRoute = testRoute.copy(stops = emptyList())
        val ping = BusPing(routeId = "KSRTC-123", timestamp = 1_000_000_000_000L)

        val etas = calculator.calculateEtas(emptyRoute, ping)
        assertTrue(etas.isEmpty())
    }

    @Test
    fun `calculateEtas with single-stop route`() {
        val singleStopRoute = testRoute.copy(
            stops = listOf(
                Stop(id = "stop-0", name = "Only Stop", sequence = 0, lat = 12.0, lng = 77.0)
            )
        )

        val now = 1_000_000_000_000L
        val ping = BusPing(
            id = "ping-1", routeId = "KSRTC-123",
            stopId = "stop-0", stopSequence = 0,
            timestamp = now - 60_000L, isActive = true
        )

        val etas = calculator.calculateEtas(singleStopRoute, ping, now)
        assertEquals(1, etas.size)
        assertTrue(etas[0].isCurrentLocation)
        // pingAgeMinutes = 1, and code uses: etaMinutes = if (etaMinutes < 2) 0 else etaMinutes
        assertEquals(0, etas[0].etaMinutes)
    }

    // ─── Formatting Tests ──────────────────────────────────────────────

    @Test
    fun `formatEta returns Passed for passed stops`() {
        val eta = StopEta(
            stop = routeStops[0],
            etaMinutes = null,
            etaTimestamp = null,
            isBusPassed = true
        )
        assertEquals("Passed", calculator.formatEta(eta))
    }

    @Test
    fun `formatEta returns Here now for current location`() {
        val eta = StopEta(
            stop = routeStops[1],
            etaMinutes = 0,
            etaTimestamp = 1_000_000_000_000L,
            isCurrentLocation = true
        )
        assertEquals("Here now", calculator.formatEta(eta))
    }

    @Test
    fun `formatEta returns No data when etaMinutes is null`() {
        val eta = StopEta(
            stop = routeStops[0],
            etaMinutes = null,
            etaTimestamp = null
        )
        assertEquals("No data", calculator.formatEta(eta))
    }

    @Test
    fun `formatEta returns Arriving when etaMinutes is 0`() {
        val eta = StopEta(
            stop = routeStops[0],
            etaMinutes = 0,
            etaTimestamp = 1_000_000_000_000L
        )
        assertEquals("Arriving", calculator.formatEta(eta))
    }

    @Test
    fun `formatEta returns minutes for less than 60 min`() {
        val eta = StopEta(
            stop = routeStops[0],
            etaMinutes = 15,
            etaTimestamp = 1_000_000_000_000L
        )
        assertEquals("~15 min", calculator.formatEta(eta))
    }

    @Test
    fun `formatEta returns hours and minutes for over 60 min`() {
        val eta = StopEta(
            stop = routeStops[0],
            etaMinutes = 90,
            etaTimestamp = 1_000_000_000_000L
        )
        assertEquals("~1h 30m", calculator.formatEta(eta))
    }

    @Test
    fun `formatEta returns exact hours for round hours`() {
        val eta = StopEta(
            stop = routeStops[0],
            etaMinutes = 120,
            etaTimestamp = 1_000_000_000_000L
        )
        assertEquals("~2h", calculator.formatEta(eta))
    }

    // ─── Accessibility Formatting Tests ─────────────────────────────────

    @Test
    fun `formatEtaAccessible describes passed stop`() {
        val eta = StopEta(
            stop = routeStops[2],
            etaMinutes = null,
            etaTimestamp = null,
            isBusPassed = true
        )
        assertTrue(calculator.formatEtaAccessible(eta).contains("has passed Stop C"))
    }

    @Test
    fun `formatEtaAccessible describes current location`() {
        val eta = StopEta(
            stop = routeStops[2],
            etaMinutes = 0,
            etaTimestamp = 1_000_000_000_000L,
            isCurrentLocation = true
        )
        assertTrue(calculator.formatEtaAccessible(eta).contains("currently at Stop C"))
    }

    @Test
    fun `formatEtaAccessible describes no data`() {
        val eta = StopEta(
            stop = routeStops[1],
            etaMinutes = null,
            etaTimestamp = null
        )
        assertTrue(calculator.formatEtaAccessible(eta).contains("No live data for Stop B"))
    }

    @Test
    fun `formatEtaAccessible describes arriving`() {
        val eta = StopEta(
            stop = routeStops[0],
            etaMinutes = 0,
            etaTimestamp = 1_000_000_000_000L
        )
        assertTrue(calculator.formatEtaAccessible(eta).contains("arriving at Stop A"))
    }

    @Test
    fun `formatEtaAccessible describes minutes-based ETA`() {
        val eta = StopEta(
            stop = routeStops[0],
            etaMinutes = 15,
            etaTimestamp = 1_000_000_000_000L
        )
        assertTrue(calculator.formatEtaAccessible(eta).contains("15 minutes"))
    }

    @Test
    fun `formatEtaAccessible describes hours-based ETA`() {
        val eta = StopEta(
            stop = routeStops[0],
            etaMinutes = 90,
            etaTimestamp = 1_000_000_000_000L
        )
        val formatted = calculator.formatEtaAccessible(eta)
        assertTrue(formatted.contains("1 hours"))
        assertTrue(formatted.contains("30 minutes"))
    }

    // ─── Confidence Tests ───────────────────────────────────────────────

    @Test
    fun `confidence is HIGH for recent ping without confirmations`() {
        val ping = BusPing(
            id = "ping-1", routeId = "KSRTC-123",
            stopId = "stop-0", timestamp = 1_000_000_000_000L,
            confirmationCount = 0
        )
        val etas = calculator.calculateEtas(testRoute, ping, 1_000_000_000_000L + 5 * 60_000L)
        // age = 5 min < 15 → HIGH
        assertEquals(EtaConfidence.HIGH, etas[1].confidence)
    }

    @Test
    fun `confidence degrades with ping age`() {
        val now = 1_000_000_000_000L
        val ping = BusPing(
            id = "ping-1", routeId = "KSRTC-123",
            stopId = "stop-0", timestamp = now,
            confirmationCount = 0
        )

        // 30 min old → MEDIUM
        val etas30 = calculator.calculateEtas(testRoute, ping, now + 30 * 60_000L)
        assertEquals(EtaConfidence.MEDIUM, etas30[1].confidence)

        // 90 min old → LOW
        val etas90 = calculator.calculateEtas(testRoute, ping, now + 90 * 60_000L)
        assertEquals(EtaConfidence.LOW, etas90[1].confidence)

        // 300 min old → NONE
        val etas300 = calculator.calculateEtas(testRoute, ping, now + 300 * 60_000L)
        assertEquals(EtaConfidence.NONE, etas300[1].confidence)
    }

    @Test
    fun `confirmed pings get extended HIGH confidence duration`() {
        val now = 1_000_000_000_000L
        val ping = BusPing(
            id = "ping-1", routeId = "KSRTC-123",
            stopId = "stop-0", timestamp = now,
            confirmationCount = 2  // >= 2 → confirmationBonus = 1
        )

        // 20 min old — normally this would be MEDIUM (> 15),
        // but with confirmation bonus threshold = 15 + 10 = 25, still HIGH
        val etas = calculator.calculateEtas(testRoute, ping, now + 20 * 60_000L)
        assertEquals(EtaConfidence.HIGH, etas[1].confidence)
    }

    // ─── Live Location ETA Tests ────────────────────────────────────────

    @Test
    fun `calculateEtas with null liveLocation returns null ETAs`() {
        val etas = calculator.calculateEtas(testRoute, null as LiveBusLocation?)
        assertEquals(5, etas.size)
        etas.forEach { eta ->
            assertNull(eta.etaMinutes)
            assertNull(eta.etaTimestamp)
        }
    }

    @Test
    fun `calculateEtas with inactive liveLocation returns null ETAs`() {
        val location = LiveBusLocation(
            routeId = "KSRTC-123", lat = 12.0, lng = 77.0,
            isActive = false, timestamp = 1_000_000_000_000L
        )
        val etas = calculator.calculateEtas(testRoute, location, 1_000_000_000_000L)
        etas.forEach { eta ->
            assertNull(eta.etaMinutes)
        }
    }

    @Test
    fun `calculateEtas with liveLocation at first stop marks it current`() {
        val now = 1_000_000_000_000L
        val location = LiveBusLocation(
            routeId = "KSRTC-123",
            lat = 12.0, lng = 77.0,  // Same as Stop A
            isActive = true,
            timestamp = now,
            accuracy = 50f,
            source = LocationSource.DRIVER
        )

        val etas = calculator.calculateEtas(testRoute, location, now)
        assertEquals(5, etas.size)
        // Stop A should be current location
        assertTrue(etas[0].isCurrentLocation)
        assertEquals(0, etas[0].etaMinutes)
    }

    // ─── Live Location Confidence Tests ─────────────────────────────────

    @Test
    fun `liveLocation confidence is HIGH for trusted source with good accuracy`() {
        val now = 1_000_000_000_000L
        val location = LiveBusLocation(
            routeId = "KSRTC-123",
            lat = 12.01, lng = 77.01,
            isActive = true, timestamp = now,
            accuracy = 50f,
            source = LocationSource.DRIVER
        )

        val etas = calculator.calculateEtas(testRoute, location, now)
        // Driver source + accuracy <= 75f + distance <= 250m → HIGH
        assertEquals(EtaConfidence.HIGH, etas[0].confidence)
    }

    @Test
    fun `liveLocation confidence is NONE for very old location`() {
        val now = 1_000_000_000_000L
        val location = LiveBusLocation(
            routeId = "KSRTC-123",
            lat = 12.01, lng = 77.01,
            isActive = true, timestamp = now - 3 * 60 * 1000L,  // 3 min old
            accuracy = 50f,
            source = LocationSource.DRIVER
        )

        val etas = calculator.calculateEtas(testRoute, location, now)
        // ageMinutes = 3 > 2 → NONE for first stop
        assertEquals(EtaConfidence.NONE, etas[0].confidence)
    }
}

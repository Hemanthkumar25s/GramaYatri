package com.gramayatri.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for utility classes in the GramaYatri User app.
 *
 * Covers:
 * - [TimeUtils.formatRelativeTime]
 * - [TimeUtils.generateDateBasedTripId]
 * - [DeviceUtils.generateDeviceId]
 * - [Constants] values
 */
class UtilsTest {

    // ─── TimeUtils.formatRelativeTime ───────────────────────────────────

    @Test
    fun `formatRelativeTime returns just now for less than 1 minute`() {
        val now = System.currentTimeMillis()
        val recent = now - 30_000  // 30 seconds ago
        assertEquals("just now", TimeUtils.formatRelativeTime(recent))
    }

    @Test
    fun `formatRelativeTime returns minutes ago for 1-59 minutes`() {
        val now = System.currentTimeMillis()
        val fiveMinAgo = now - 5 * 60 * 1000L
        assertEquals("5m ago", TimeUtils.formatRelativeTime(fiveMinAgo))
    }

    @Test
    fun `formatRelativeTime returns 1m ago for exactly 1 minute`() {
        val now = System.currentTimeMillis()
        val oneMinAgo = now - 60_000
        assertEquals("1m ago", TimeUtils.formatRelativeTime(oneMinAgo))
    }

    @Test
    fun `formatRelativeTime returns hours ago for 1-23 hours`() {
        val now = System.currentTimeMillis()
        val threeHoursAgo = now - 3 * 60 * 60 * 1000L
        assertEquals("3h ago", TimeUtils.formatRelativeTime(threeHoursAgo))
    }

    @Test
    fun `formatRelativeTime returns days ago for 24+ hours`() {
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2 * 24 * 60 * 60 * 1000L
        assertEquals("2d ago", TimeUtils.formatRelativeTime(twoDaysAgo))
    }

    // ─── TimeUtils.generateDateBasedTripId ──────────────────────────────

    @Test
    fun `generateDateBasedTripId starts with route ID`() {
        val tripId = TimeUtils.generateDateBasedTripId("KSRTC-123")
        assertTrue("Trip ID should start with route ID", tripId.startsWith("KSRTC-123"))
    }

    @Test
    fun `generateDateBasedTripId contains date in correct format`() {
        val tripId = TimeUtils.generateDateBasedTripId("KSRTC-123")
        // Format: {routeId}-YYYY-MM-DD
        val regex = Regex("^KSRTC-123-\\d{4}-\\d{2}-\\d{2}$")
        assertTrue("Trip ID should match date format", tripId.matches(regex))
    }

    @Test
    fun `generateDateBasedTripId produces different IDs for different routes`() {
        val tripId1 = TimeUtils.generateDateBasedTripId("R1")
        val tripId2 = TimeUtils.generateDateBasedTripId("R2")
        // Different routes should produce different IDs
        assertNotEquals(tripId1, tripId2)
        // Both should contain today's date
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        assertTrue(tripId1.contains(todayDate))
        assertTrue(tripId2.contains(todayDate))
    }

    // ─── DeviceUtils.generateDeviceId ────────────────────────────────────

    @Test
    fun `generateDeviceId returns non-empty string`() {
        val deviceId = DeviceUtils.generateDeviceId()
        assertTrue("Device ID should not be empty", deviceId.isNotEmpty())
    }

    @Test
    fun `generateDeviceId returns unique values on each call`() {
        val id1 = DeviceUtils.generateDeviceId()
        val id2 = DeviceUtils.generateDeviceId()
        assertNotEquals("Device IDs should be unique", id1, id2)
    }

    @Test
    fun `generateDeviceId returns UUID format`() {
        val deviceId = DeviceUtils.generateDeviceId()
        // UUID format: 8-4-4-4-12 hex chars
        val uuidRegex = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
        assertTrue("Device ID should be a valid UUID", deviceId.matches(uuidRegex))
    }

    // ─── Constants ──────────────────────────────────────────────────────

    @Test
    fun `PING_EXPIRY_MS is exactly 4 hours`() {
        assertEquals(4 * 60 * 60 * 1000L, Constants.PING_EXPIRY_MS)
    }

    @Test
    fun `ALERT_EXPIRY_MS is exactly 24 hours`() {
        assertEquals(24 * 60 * 60 * 1000L, Constants.ALERT_EXPIRY_MS)
    }

    @Test
    fun `RATE_LIMIT_MS is exactly 2 minutes`() {
        assertEquals(2 * 60 * 1000L, Constants.RATE_LIMIT_MS)
    }

    @Test
    fun `SPAM_DENIAL_THRESHOLD is 5`() {
        assertEquals(5, Constants.SPAM_DENIAL_THRESHOLD)
    }

    @Test
    fun `ETA_REFRESH_INTERVAL_MS is 30 seconds`() {
        assertEquals(30_000L, Constants.ETA_REFRESH_INTERVAL_MS)
    }
}

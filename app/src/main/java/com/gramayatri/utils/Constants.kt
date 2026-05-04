package com.gramayatri.utils

import java.util.UUID

object Constants {
    // Ping expires after 4 hours
    const val PING_EXPIRY_MS = 4 * 60 * 60 * 1000L

    // Alerts expire after 24 hours
    const val ALERT_EXPIRY_MS = 24 * 60 * 60 * 1000L

    // Rate limit: 1 ping per 2 minutes per device
    const val RATE_LIMIT_MS = 2 * 60 * 1000L

    // Auto-deactivate ping if 5+ denials
    const val SPAM_DENIAL_THRESHOLD = 5

    // ETA refresh interval (UI countdown)
    const val ETA_REFRESH_INTERVAL_MS = 30_000L

    // Firebase paths
    const val FB_ROUTES = "routes"
    const val FB_PINGS = "pings"
    const val FB_ALERTS = "alerts"
}

object DeviceUtils {
    fun generateDeviceId(): String = UUID.randomUUID().toString()
}

object TimeUtils {
    fun formatRelativeTime(epochMs: Long): String {
        val diffMs = System.currentTimeMillis() - epochMs
        val diffMin = diffMs / 60_000
        return when {
            diffMin < 1 -> "just now"
            diffMin < 60 -> "${diffMin}m ago"
            diffMin < 1440 -> "${diffMin / 60}h ago"
            else -> "${diffMin / 1440}d ago"
        }
    }
}

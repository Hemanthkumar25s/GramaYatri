package com.gramayatri.domain.usecase

import com.gramayatri.data.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * ETA Engine
 * ──────────
 * Core algorithm:
 *   Given a bus ping at stop N at time T,
 *   ETA(stop M) = T + Σ avgTravelTime(N→N+1→...→M)
 *
 * Confidence degrades as the ping ages.
 */
@Singleton
class EtaCalculator @Inject constructor() {

    /**
     * Calculate ETAs for all stops in a route given the latest ping.
     *
     * @param route        The route with stop definitions
     * @param activePing   The most recent valid bus ping (nullable)
     * @param currentTime  Current epoch millis (injectable for testing)
     */
    fun calculateEtas(
        route: Route,
        activePing: BusPing?,
        currentTime: Long = System.currentTimeMillis()
    ): List<StopEta> {
        if (activePing == null || route.stops.isEmpty()) {
            return route.stops.map { stop ->
                StopEta(stop = stop, etaMinutes = null, etaTimestamp = null)
            }
        }

        val pingStop = route.stops.find { it.id == activePing.stopId }
            ?: return route.stops.map { stop ->
                StopEta(stop = stop, etaMinutes = null, etaTimestamp = null)
            }

        val pingAge = currentTime - activePing.timestamp
        val pingAgeMinutes = (pingAge / 60_000).toInt()
        val confidence = calculateConfidence(pingAgeMinutes, activePing)

        return route.stops.map { stop ->
            when {
                stop.sequence < pingStop.sequence -> {
                    // Bus already passed this stop
                    StopEta(
                        stop = stop,
                        etaMinutes = null,
                        etaTimestamp = null,
                        isBusPassed = true,
                        confidence = EtaConfidence.HIGH
                    )
                }
                stop.sequence == pingStop.sequence -> {
                    // Bus is HERE (or was here at ping time)
                    val etaMinutes = pingAgeMinutes
                    StopEta(
                        stop = stop,
                        etaMinutes = if (etaMinutes < 2) 0 else etaMinutes,
                        etaTimestamp = activePing.timestamp,
                        isCurrentLocation = pingAgeMinutes < 5,
                        isBusPassed = pingAgeMinutes >= 5,
                        confidence = confidence
                    )
                }
                else -> {
                    // Future stop — sum travel times from ping stop to here
                    val travelMinutes = calculateTravelTime(route.stops, pingStop.sequence, stop.sequence)
                    // Subtract time already elapsed since the ping
                    val remainingMinutes = (travelMinutes - pingAgeMinutes).coerceAtLeast(0)
                    val etaTimestamp = activePing.timestamp + (travelMinutes * 60_000L)

                    StopEta(
                        stop = stop,
                        etaMinutes = remainingMinutes,
                        etaTimestamp = etaTimestamp,
                        confidence = confidence
                    )
                }
            }
        }
    }

    /**
     * Sum average travel times between consecutive stops from [fromSequence] to [toSequence].
     * Uses the avgTravelTimeFromPrevMinutes field on each stop.
     */
    private fun calculateTravelTime(stops: List<Stop>, fromSequence: Int, toSequence: Int): Int {
        return stops
            .filter { it.sequence > fromSequence && it.sequence <= toSequence }
            .sumOf { it.avgTravelTimeFromPrevMinutes }
    }

    /**
     * Confidence degrades as the ping ages.
     * < 15 min = HIGH
     * 15–60 min = MEDIUM
     * 60–240 min = LOW
     * > 240 min = NONE (shouldn't happen — pings expire at 4h)
     */
    private fun calculateConfidence(ageMinutes: Int, ping: BusPing): EtaConfidence {
        // Confirmed pings get a confidence boost
        val confirmationBonus = if (ping.confirmationCount >= 2) 1 else 0

        return when {
            ageMinutes < (15 + confirmationBonus * 10) -> EtaConfidence.HIGH
            ageMinutes < (60 + confirmationBonus * 30) -> EtaConfidence.MEDIUM
            ageMinutes < 240 -> EtaConfidence.LOW
            else -> EtaConfidence.NONE
        }
    }

    /**
     * Format ETA for display.
     */
    fun formatEta(eta: StopEta): String {
        return when {
            eta.isBusPassed && !eta.isCurrentLocation -> "Passed"
            eta.isCurrentLocation -> "Here now"
            eta.etaMinutes == null -> "No data"
            eta.etaMinutes == 0 -> "Arriving"
            eta.etaMinutes < 60 -> "~${eta.etaMinutes} min"
            else -> {
                val h = eta.etaMinutes / 60
                val m = eta.etaMinutes % 60
                if (m == 0) "~${h}h" else "~${h}h ${m}m"
            }
        }
    }

    /**
     * Format for accessibility (screen readers).
     */
    fun formatEtaAccessible(eta: StopEta): String {
        return when {
            eta.isBusPassed && !eta.isCurrentLocation -> "Bus has passed ${eta.stop.name}"
            eta.isCurrentLocation -> "Bus is currently at ${eta.stop.name}"
            eta.etaMinutes == null -> "No live data for ${eta.stop.name}"
            eta.etaMinutes == 0 -> "Bus arriving at ${eta.stop.name}"
            eta.etaMinutes < 60 -> "Bus arrives at ${eta.stop.name} in approximately ${eta.etaMinutes} minutes"
            else -> {
                val h = eta.etaMinutes / 60
                val m = eta.etaMinutes % 60
                "Bus arrives at ${eta.stop.name} in approximately $h hours ${if (m > 0) "$m minutes" else ""}"
            }
        }
    }
}

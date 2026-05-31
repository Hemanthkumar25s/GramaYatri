package com.gramayatri.domain.usecase

import com.gramayatri.data.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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

    fun calculateEtas(
        route: Route,
        liveLocation: LiveBusLocation?,
        currentTime: Long = System.currentTimeMillis()
    ): List<StopEta> {
        if (liveLocation == null || !liveLocation.isActive || route.stops.isEmpty()) {
            return route.stops.map { stop ->
                StopEta(stop = stop, etaMinutes = null, etaTimestamp = null)
            }
        }

        val orderedStops = route.stops.sortedBy { it.sequence }
        val routeDistances = calculateRouteDistances(orderedStops)
        val progress = locateRouteProgress(orderedStops, liveLocation.lat, liveLocation.lng, routeDistances)
            ?: return route.stops.map { stop ->
                StopEta(stop = stop, etaMinutes = null, etaTimestamp = null)
            }

        val locationAgeMinutes = ((currentTime - liveLocation.timestamp).coerceAtLeast(0) / 60_000).toInt()
        val confidence = calculateLiveLocationConfidence(liveLocation, locationAgeMinutes, progress.distanceToRouteMeters)
        val currentRadiusMeters = liveLocation.accuracy.coerceAtLeast(100f).toDouble()

        return orderedStops.map { stop ->
            val stopDistanceKm = stopDistance(stop, routeDistances)
            val stopDistanceFromBusMeters = distanceMeters(liveLocation.lat, liveLocation.lng, stop.lat, stop.lng)
            when {
                stopDistanceKm < progress.distanceFromOriginKm - 0.15 -> {
                    StopEta(
                        stop = stop,
                        etaMinutes = null,
                        etaTimestamp = null,
                        isBusPassed = true,
                        confidence = confidence
                    )
                }
                stopDistanceFromBusMeters <= currentRadiusMeters -> {
                    StopEta(
                        stop = stop,
                        etaMinutes = 0,
                        etaTimestamp = liveLocation.timestamp,
                        isCurrentLocation = true,
                        confidence = confidence
                    )
                }
                stopDistanceKm >= progress.distanceFromOriginKm -> {
                    val travelMinutes = calculateRemainingTravelMinutes(
                        orderedStops,
                        progress.distanceFromOriginKm,
                        stopDistanceKm,
                        routeDistances
                    )
                    val adjustedMinutes = (travelMinutes - locationAgeMinutes).coerceAtLeast(0)
                    StopEta(
                        stop = stop,
                        etaMinutes = adjustedMinutes,
                        etaTimestamp = liveLocation.timestamp + (travelMinutes * 60_000L),
                        confidence = confidence
                    )
                }
                else -> {
                    StopEta(
                        stop = stop,
                        etaMinutes = null,
                        etaTimestamp = null,
                        isBusPassed = true,
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

    private fun calculateLiveLocationConfidence(
        location: LiveBusLocation,
        ageMinutes: Int,
        distanceToRouteMeters: Double
    ): EtaConfidence {
        val trustedSource = location.source == LocationSource.DRIVER ||
                location.source == LocationSource.TICKET_MACHINE
        return when {
            ageMinutes > 2 -> EtaConfidence.NONE
            trustedSource && location.accuracy <= 75f && distanceToRouteMeters <= 250.0 -> EtaConfidence.HIGH
            trustedSource && distanceToRouteMeters <= 500.0 -> EtaConfidence.MEDIUM
            distanceToRouteMeters <= 500.0 -> EtaConfidence.MEDIUM
            else -> EtaConfidence.LOW
        }
    }

    private fun calculateRouteDistances(stops: List<Stop>): Map<String, Double> {
        if (stops.isEmpty()) return emptyMap()

        var runningDistanceKm = 0.0
        val distances = mutableMapOf(stops.first().id to 0.0)
        stops.zipWithNext { from, to ->
            runningDistanceKm += distanceMeters(from.lat, from.lng, to.lat, to.lng) / 1000.0
            distances[to.id] = runningDistanceKm
        }
        return distances
    }

    private fun locateRouteProgress(
        stops: List<Stop>,
        lat: Double,
        lng: Double,
        routeDistances: Map<String, Double>
    ): RouteProgress? {
        if (stops.size == 1) {
            return RouteProgress(0.0, distanceMeters(lat, lng, stops.first().lat, stops.first().lng))
        }

        var bestProgress: RouteProgress? = null
        stops.zipWithNext { from, to ->
            val segmentLengthMeters = distanceMeters(from.lat, from.lng, to.lat, to.lng)
            if (segmentLengthMeters <= 0.0) return@zipWithNext

            val projected = projectPointToSegmentMeters(lat, lng, from.lat, from.lng, to.lat, to.lng)
            val segmentStartKm = stopDistance(from, routeDistances)
            val distanceFromOriginKm = segmentStartKm + (segmentLengthMeters * projected.fraction / 1000.0)
            val progress = RouteProgress(distanceFromOriginKm, projected.distanceMeters)
            if (bestProgress == null || progress.distanceToRouteMeters < bestProgress!!.distanceToRouteMeters) {
                bestProgress = progress
            }
        }
        return bestProgress
    }

    private fun calculateRemainingTravelMinutes(
        stops: List<Stop>,
        currentDistanceKm: Double,
        targetDistanceKm: Double,
        routeDistances: Map<String, Double>
    ): Int {
        if (targetDistanceKm <= currentDistanceKm) return 0

        val minutes = stops.drop(1).sumOf { stop ->
            val previousStop = stops[stops.indexOf(stop) - 1]
            val segmentStartKm = stopDistance(previousStop, routeDistances)
            val segmentEndKm = stopDistance(stop, routeDistances)
            val overlapStart = maxOf(currentDistanceKm, segmentStartKm)
            val overlapEnd = minOf(targetDistanceKm, segmentEndKm)
            if (overlapEnd <= overlapStart || segmentEndKm <= segmentStartKm) {
                0.0
            } else {
                val segmentFraction = (overlapEnd - overlapStart) / (segmentEndKm - segmentStartKm)
                stop.avgTravelTimeFromPrevMinutes * segmentFraction
            }
        }
        return minutes.toInt().coerceAtLeast(1)
    }

    private fun stopDistance(stop: Stop, routeDistances: Map<String, Double>): Double {
        return stop.distanceFromOriginKm.takeIf { it > 0.0 } ?: routeDistances[stop.id] ?: 0.0
    }

    private fun projectPointToSegmentMeters(
        pointLat: Double,
        pointLng: Double,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): SegmentProjection {
        val originLatRadians = startLat * PI / 180.0
        val px = metersX(pointLng - startLng, originLatRadians)
        val py = metersY(pointLat - startLat)
        val ex = metersX(endLng - startLng, originLatRadians)
        val ey = metersY(endLat - startLat)
        val lengthSquared = ex.pow(2) + ey.pow(2)
        val fraction = if (lengthSquared == 0.0) 0.0 else ((px * ex + py * ey) / lengthSquared).coerceIn(0.0, 1.0)
        val projectedX = ex * fraction
        val projectedY = ey * fraction
        val distance = sqrt((px - projectedX).pow(2) + (py - projectedY).pow(2))
        return SegmentProjection(fraction, distance)
    }

    private fun metersX(deltaLongitude: Double, latitudeRadians: Double): Double {
        return deltaLongitude * 111_320.0 * cos(latitudeRadians)
    }

    private fun metersY(deltaLatitude: Double): Double {
        return deltaLatitude * 110_540.0
    }

    private fun distanceMeters(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = (endLat - startLat) * PI / 180.0
        val dLng = (endLng - startLng) * PI / 180.0
        val lat1 = startLat * PI / 180.0
        val lat2 = endLat * PI / 180.0
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return earthRadiusMeters * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private data class RouteProgress(
        val distanceFromOriginKm: Double,
        val distanceToRouteMeters: Double
    )

    private data class SegmentProjection(
        val fraction: Double,
        val distanceMeters: Double
    )

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

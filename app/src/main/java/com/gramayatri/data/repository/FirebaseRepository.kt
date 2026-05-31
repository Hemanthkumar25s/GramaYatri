package com.gramayatri.data.repository

import com.google.firebase.database.*
import com.gramayatri.data.model.*
import com.gramayatri.utils.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class FirebaseRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val localCacheRepository: LocalCacheRepository
) {

    // ─── References ───────────────────────────────────────────────────────
    private val routesRef get() = database.getReference("routes")
    private val pingsRef get() = database.getReference("pings")
    private val alertsRef get() = database.getReference("alerts")
    private val liveLocationsRef get() = database.getReference("live_locations")
    private val ticketMachineSessionsRef get() = database.getReference("ticket_machine_sessions")
    private val driverVerificationsRef get() = database.getReference("driver_verifications")

    // ─── Routes ───────────────────────────────────────────────────────────

    fun observeRoutes(): Flow<NetworkResult<List<Route>>> = callbackFlow {
        trySend(NetworkResult.Loading)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val routes = snapshot.children.mapNotNull { child ->
                    parseRoute(child)
                }.filter { it.id.isNotBlank() && it.isActive }
                localCacheRepository.cacheRoutes(routes)
                trySend(NetworkResult.Success(routes))
            }

            override fun onCancelled(error: DatabaseError) {
                val cached = localCacheRepository.getCachedRoutes()
                if (cached.isNotEmpty()) {
                    trySend(NetworkResult.Success(cached))
                } else {
                    trySend(NetworkResult.Error(error.message, error.toException()))
                }
            }
        }

        routesRef.addValueEventListener(listener)
        awaitClose { routesRef.removeEventListener(listener) }
    }.catch { e ->
        val cached = localCacheRepository.getCachedRoutes()
        if (cached.isNotEmpty()) emit(NetworkResult.Success(cached))
        else emit(NetworkResult.Error(e.message ?: "Unknown error", e))
    }

    // ─── Live Locations ───────────────────────────────────────────────────

    fun observeLiveLocation(routeId: String): Flow<LiveBusLocation?> = callbackFlow {
        val ref = liveLocationsRef.child(routeId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }

                val locations = if (snapshot.child("lat").exists()) {
                    listOfNotNull(parseLiveLocation(routeId, snapshot))
                } else {
                    snapshot.children.mapNotNull { child -> parseLiveLocation(routeId, child) }
                }

                trySend(selectBestLiveLocation(locations))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun updateLiveLocation(location: LiveBusLocation) {
        try {
            liveLocationsRef
                .child(location.routeId)
                .child(location.source.name)
                .setValue(location.toMap())
                .await()
        } catch (e: Exception) {
            // Non-critical
        }
    }

    suspend fun deactivateLiveLocation(routeId: String, source: LocationSource) {
        try {
            liveLocationsRef.child(routeId).child(source.name).updateChildren(
                mapOf(
                    "isActive" to false,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) {
            // Non-critical
        }
    }

    suspend fun publishTicketMachineSession(session: TicketMachineSession) {
        try {
            ticketMachineSessionsRef.child(session.tripId).setValue(session.toMap()).await()
        } catch (e: Exception) {
            // Non-critical; GPS broadcast can continue and retry on next update.
        }
    }

    suspend fun verifyDriverBackup(
        routeId: String,
        tripId: String,
        driverId: String,
        token: String,
        driverLat: Double,
        driverLng: Double
    ): DriverVerification {
        return try {
            val snapshot = ticketMachineSessionsRef.child(tripId).get().await()
            val session = parseTicketMachineSession(snapshot)
                ?: return rejectedVerification(routeId, tripId, driverId, "No active ticket-machine trip")

            val now = System.currentTimeMillis()
            val distanceMeters = distanceMeters(driverLat, driverLng, session.lat, session.lng)
            val status = when {
                !session.isActive || session.expiresAt < now -> VerificationStatus.EXPIRED
                session.routeId != routeId -> VerificationStatus.REJECTED
                session.verificationToken != token -> VerificationStatus.REJECTED
                distanceMeters > DRIVER_MACHINE_MAX_DISTANCE_METERS -> VerificationStatus.REJECTED
                else -> VerificationStatus.VERIFIED
            }

            val verification = DriverVerification(
                routeId = routeId,
                tripId = tripId,
                driverId = driverId,
                machineId = session.machineId,
                status = status,
                distanceFromMachineMeters = distanceMeters,
                verifiedAt = now,
                expiresAt = now + DRIVER_VERIFICATION_TTL_MS,
                reason = when (status) {
                    VerificationStatus.VERIFIED -> "Driver is verified near ticket machine"
                    VerificationStatus.EXPIRED -> "Ticket-machine token expired"
                    else -> "Driver token, route, or distance check failed"
                }
            )
            driverVerificationsRef.child(tripId).child(driverId).setValue(verification.toMap()).await()
            verification
        } catch (e: Exception) {
            rejectedVerification(routeId, tripId, driverId, e.message ?: "Verification failed")
        }
    }

    // ─── Active Ping for Route ─────────────────────────────────────────────

    fun observeActivePing(routeId: String): Flow<BusPing?> = callbackFlow {
        val cutoffTime = System.currentTimeMillis() - Constants.PING_EXPIRY_MS
        val query = pingsRef
            .child(routeId)
            .orderByChild("timestamp")
            .startAt(cutoffTime.toDouble())
            .limitToLast(1)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ping = snapshot.children.lastOrNull()?.let { parsePing(it) }
                // Only return if it's actually active (not cancelled/denied)
                val activePing = ping?.takeIf { it.isActive }
                trySend(activePing)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(localCacheRepository.getCachedPing(routeId))
            }
        }

        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    // ─── Submit Ping ───────────────────────────────────────────────────────

    suspend fun submitPing(
        ping: BusPing,
        deviceId: String
    ): NetworkResult<BusPing> {
        return try {
            // Rate limit check
            val lastPingTime = localCacheRepository.getLastPingTime(deviceId)
            val elapsed = System.currentTimeMillis() - lastPingTime
            if (elapsed < Constants.RATE_LIMIT_MS) {
                val remaining = ((Constants.RATE_LIMIT_MS - elapsed) / 1000).toInt()
                return NetworkResult.Error("Rate limited. Wait ${remaining}s", null)
            }

            withTimeout(10_000) {
                val newRef = pingsRef.child(ping.routeId).push()
                val pingWithId = ping.copy(id = newRef.key ?: "")
                newRef.setValue(pingWithId.toMap()).await()
                localCacheRepository.saveLastPingTime(deviceId)
                localCacheRepository.cachePing(ping.routeId, pingWithId)
                NetworkResult.Success(pingWithId)
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Failed to submit ping", e)
        }
    }

    // ─── Ping Confirmation ─────────────────────────────────────────────────

    suspend fun confirmPing(routeId: String, pingId: String, confirmed: Boolean) {
        try {
            val field = if (confirmed) "confirmationCount" else "denialCount"
            pingsRef.child(routeId).child(pingId).child(field)
                .runTransaction(object : Transaction.Handler {
                    override fun doTransaction(data: MutableData): Transaction.Result {
                        val current = data.getValue(Int::class.java) ?: 0
                        data.value = current + 1
                        return Transaction.success(data)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
                })

            // Auto-deactivate ping if too many denials
            val pingSnapshot = pingsRef.child(routeId).child(pingId).get().await()
            val denials = pingSnapshot.child("denialCount").getValue(Int::class.java) ?: 0
            if (denials >= Constants.SPAM_DENIAL_THRESHOLD) {
                pingsRef.child(routeId).child(pingId).child("isActive").setValue(false).await()
            }
        } catch (e: Exception) {
            // Silent fail — non-critical
        }
    }

    // ─── Alerts ───────────────────────────────────────────────────────────

    fun observeAlerts(routeId: String): Flow<List<BusAlert>> = callbackFlow {
        val cutoffTime = System.currentTimeMillis() - Constants.ALERT_EXPIRY_MS
        val query = alertsRef
            .child(routeId)
            .orderByChild("timestamp")
            .startAt(cutoffTime.toDouble())

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = snapshot.children.mapNotNull { parseAlert(it) }
                    .filter { it.isActive }
                    .sortedByDescending { it.timestamp }
                trySend(alerts)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }

        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    // ─── Admin: Post Alert ─────────────────────────────────────────────────

    suspend fun postAlert(alert: BusAlert): NetworkResult<Unit> {
        return try {
            val ref = alertsRef.child(alert.routeId).push()
            val alertWithId = alert.copy(id = ref.key ?: "")
            ref.setValue(alertWithId.toMap()).await()
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Failed to post alert", e)
        }
    }

    // ─── Cleanup expired pings (call periodically) ─────────────────────────

    suspend fun cleanupExpiredPings(routeId: String) {
        try {
            val cutoffTime = System.currentTimeMillis() - Constants.PING_EXPIRY_MS
            val snapshot = pingsRef.child(routeId)
                .orderByChild("timestamp")
                .endAt(cutoffTime.toDouble())
                .get()
                .await()

            snapshot.children.forEach { child ->
                child.ref.child("isActive").setValue(false).await()
            }
        } catch (e: Exception) {
            // Non-critical cleanup
        }
    }

    // ─── Parsers ───────────────────────────────────────────────────────────

    private fun parseRoute(snapshot: DataSnapshot): Route? {
        return try {
            val routeId = snapshot.key?.takeIf { it.isNotBlank() } ?: return null
            val stops = snapshot.child("stops").children.mapNotNull { stopSnapshot ->
                Stop(
                    id = stopSnapshot.key ?: return@mapNotNull null,
                    name = stopSnapshot.child("name").getValue(String::class.java) ?: "",
                    sequence = stopSnapshot.child("sequence").getValue(Int::class.java) ?: 0,
                    distanceFromOriginKm = stopSnapshot.child("distanceFromOriginKm")
                        .getValue(Double::class.java) ?: 0.0,
                    lat = stopSnapshot.child("lat").getValue(Double::class.java) ?: 0.0,
                    lng = stopSnapshot.child("lng").getValue(Double::class.java) ?: 0.0,
                    avgTravelTimeFromPrevMinutes = stopSnapshot.child("avgTravelTimeFromPrevMinutes")
                        .getValue(Int::class.java) ?: 5
                )
            }.sortedBy { it.sequence }

            Route(
                id = routeId,
                name = snapshot.child("name").getValue(String::class.java) ?: "",
                number = snapshot.child("number").getValue(String::class.java) ?: "",
                origin = snapshot.child("origin").getValue(String::class.java) ?: "",
                destination = snapshot.child("destination").getValue(String::class.java) ?: "",
                stops = stops,
                isActive = snapshot.child("isActive").getValue(Boolean::class.java) ?: true
            )
        } catch (e: Exception) { null }
    }

    private fun parsePing(snapshot: DataSnapshot): BusPing? {
        return try {
            val typeStr = snapshot.child("type").getValue(String::class.java) ?: "BUS_AT_STOP"
            BusPing(
                id = snapshot.key ?: "",
                routeId = snapshot.child("routeId").getValue(String::class.java) ?: "",
                stopId = snapshot.child("stopId").getValue(String::class.java) ?: "",
                stopName = snapshot.child("stopName").getValue(String::class.java) ?: "",
                stopSequence = snapshot.child("stopSequence").getValue(Int::class.java) ?: 0,
                lat = snapshot.child("lat").getValue(Double::class.java) ?: 0.0,
                lng = snapshot.child("lng").getValue(Double::class.java) ?: 0.0,
                userName = snapshot.child("userName").getValue(String::class.java) ?: "",
                deviceId = snapshot.child("deviceId").getValue(String::class.java) ?: "",
                type = PingType.valueOf(typeStr),
                timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L,
                isActive = snapshot.child("isActive").getValue(Boolean::class.java) ?: true,
                confirmationCount = snapshot.child("confirmationCount").getValue(Int::class.java) ?: 0,
                denialCount = snapshot.child("denialCount").getValue(Int::class.java) ?: 0
            )
        } catch (e: Exception) { null }
    }

    private fun parseAlert(snapshot: DataSnapshot): BusAlert? {
        return try {
            val typeStr = snapshot.child("type").getValue(String::class.java) ?: "GENERAL"
            BusAlert(
                id = snapshot.key ?: "",
                routeId = snapshot.child("routeId").getValue(String::class.java) ?: "",
                routeName = snapshot.child("routeName").getValue(String::class.java) ?: "",
                type = AlertType.valueOf(typeStr),
                message = snapshot.child("message").getValue(String::class.java) ?: "",
                timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L,
                isActive = snapshot.child("isActive").getValue(Boolean::class.java) ?: true
            )
        } catch (e: Exception) { null }
    }

    private fun parseTicketMachineSession(snapshot: DataSnapshot): TicketMachineSession? {
        return try {
            if (!snapshot.exists()) return null
            TicketMachineSession(
                routeId = snapshot.child("routeId").getValue(String::class.java) ?: "",
                tripId = snapshot.child("tripId").getValue(String::class.java) ?: "",
                machineId = snapshot.child("machineId").getValue(String::class.java) ?: "",
                verificationToken = snapshot.child("verificationToken").getValue(String::class.java) ?: "",
                qrPayload = snapshot.child("qrPayload").getValue(String::class.java) ?: "",
                lat = snapshot.child("lat").asDouble(),
                lng = snapshot.child("lng").asDouble(),
                createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
                expiresAt = snapshot.child("expiresAt").getValue(Long::class.java) ?: 0L,
                isActive = snapshot.child("isActive").getValue(Boolean::class.java) ?: true
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLiveLocation(routeId: String, snapshot: DataSnapshot): LiveBusLocation? {
        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: return null
        if (System.currentTimeMillis() - timestamp > 120_000) return null

        val location = LiveBusLocation(
            routeId = routeId,
            lat = snapshot.child("lat").asDouble(),
            lng = snapshot.child("lng").asDouble(),
            speed = snapshot.child("speed").asFloat(),
            bearing = snapshot.child("bearing").asFloat(),
            accuracy = snapshot.child("accuracy").asFloat(),
            timestamp = timestamp,
            reporterName = snapshot.child("reporterName").getValue(String::class.java) ?: "",
            driverName = snapshot.child("driverName").getValue(String::class.java) ?: "",
            driverId = snapshot.child("driverId").getValue(String::class.java) ?: "",
            isActive = snapshot.child("isActive").getValue(Boolean::class.java) ?: true,
            tripId = snapshot.child("tripId").getValue(String::class.java) ?: "",
            source = parseLocationSource(snapshot.child("source").getValue(String::class.java))
        )

        return location.takeIf { it.isActive }
    }

    private fun selectBestLiveLocation(locations: List<LiveBusLocation>): LiveBusLocation? {
        return locations
            .filter { it.source == LocationSource.TICKET_MACHINE }
            .maxByOrNull { it.timestamp }
            ?: locations
                .filter { it.source == LocationSource.DRIVER }
                .maxByOrNull { it.timestamp }
    }

    private fun DataSnapshot.asDouble(): Double {
        return (value as? Number)?.toDouble()
            ?: getValue(Double::class.java)
            ?: 0.0
    }

    private fun DataSnapshot.asFloat(): Float {
        return (value as? Number)?.toFloat()
            ?: getValue(Float::class.java)
            ?: 0f
    }

    private fun parseLocationSource(value: String?): LocationSource {
        return runCatching {
            LocationSource.valueOf(value ?: LocationSource.PASSENGER.name)
        }.getOrDefault(LocationSource.PASSENGER)
    }

    private fun rejectedVerification(
        routeId: String,
        tripId: String,
        driverId: String,
        reason: String
    ): DriverVerification {
        return DriverVerification(
            routeId = routeId,
            tripId = tripId,
            driverId = driverId,
            status = VerificationStatus.REJECTED,
            reason = reason,
            verifiedAt = System.currentTimeMillis()
        )
    }

    private fun distanceMeters(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(endLat - startLat)
        val dLng = Math.toRadians(endLng - startLng)
        val lat1 = Math.toRadians(startLat)
        val lat2 = Math.toRadians(endLat)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return earthRadiusMeters * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    companion object {
        private const val DRIVER_MACHINE_MAX_DISTANCE_METERS = 200.0
        private const val DRIVER_VERIFICATION_TTL_MS = 10 * 60 * 1000L
    }
}

// Extension: BusPing → Firebase map
fun BusPing.toMap(): Map<String, Any> = mapOf(
    "routeId" to routeId,
    "stopId" to stopId,
    "stopName" to stopName,
    "stopSequence" to stopSequence,
    "lat" to lat,
    "lng" to lng,
    "userName" to userName,
    "deviceId" to deviceId,
    "type" to type.name,
    "timestamp" to timestamp,
    "isActive" to isActive,
    "confirmationCount" to confirmationCount,
    "denialCount" to denialCount
)

fun LiveBusLocation.toMap(): Map<String, Any> = mapOf(
    "routeId" to routeId,
    "lat" to lat,
    "lng" to lng,
    "speed" to speed,
    "bearing" to bearing,
    "accuracy" to accuracy,
    "timestamp" to timestamp,
    "reporterName" to reporterName,
    "driverName" to driverName,
    "driverId" to driverId,
    "isActive" to isActive,
    "tripId" to tripId,
    "source" to source.name
)

fun BusAlert.toMap(): Map<String, Any> = mapOf(
    "routeId" to routeId,
    "routeName" to routeName,
    "type" to type.name,
    "message" to message,
    "timestamp" to timestamp,
    "isActive" to isActive
)

fun TicketMachineSession.toMap(): Map<String, Any> = mapOf(
    "routeId" to routeId,
    "tripId" to tripId,
    "machineId" to machineId,
    "verificationToken" to verificationToken,
    "qrPayload" to qrPayload,
    "lat" to lat,
    "lng" to lng,
    "createdAt" to createdAt,
    "expiresAt" to expiresAt,
    "isActive" to isActive
)

fun DriverVerification.toMap(): Map<String, Any> = mapOf(
    "routeId" to routeId,
    "tripId" to tripId,
    "driverId" to driverId,
    "machineId" to machineId,
    "status" to status.name,
    "distanceFromMachineMeters" to distanceFromMachineMeters,
    "verifiedAt" to verifiedAt,
    "expiresAt" to expiresAt,
    "reason" to reason
)

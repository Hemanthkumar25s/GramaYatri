package com.gramayatri.data.location

import android.app.*
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.gramayatri.data.model.LiveBusLocation
import com.gramayatri.data.model.LocationSource
import com.gramayatri.data.model.TicketMachineSession
import com.gramayatri.data.repository.FirebaseRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class BusTrackingService : Service() {

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var routeId: String? = null
    private var userName: String? = null
    private var driverName: String? = null
    private var driverId: String? = null
    private var tripId: String? = null
    private var verificationToken: String? = null
    private var source: LocationSource = LocationSource.PASSENGER

    companion object {
        const val CHANNEL_ID = "bus_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_ROUTE_ID = "EXTRA_ROUTE_ID"
        const val EXTRA_USER_NAME = "EXTRA_USER_NAME"
        const val EXTRA_DRIVER_NAME = "EXTRA_DRIVER_NAME"
        const val EXTRA_DRIVER_ID = "EXTRA_DRIVER_ID"
        const val EXTRA_TRIP_ID = "EXTRA_TRIP_ID"
        const val EXTRA_SOURCE = "EXTRA_SOURCE"
        const val EXTRA_VERIFICATION_TOKEN = "EXTRA_VERIFICATION_TOKEN"
        private const val TICKET_MACHINE_TOKEN_TTL_MS = 10 * 60 * 1000L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    updateFirebaseLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                routeId = intent.getStringExtra(EXTRA_ROUTE_ID)
                userName = intent.getStringExtra(EXTRA_USER_NAME)
                driverName = intent.getStringExtra(EXTRA_DRIVER_NAME)
                driverId = intent.getStringExtra(EXTRA_DRIVER_ID)
                tripId = intent.getStringExtra(EXTRA_TRIP_ID)
                verificationToken = intent.getStringExtra(EXTRA_VERIFICATION_TOKEN)
                source = parseLocationSource(intent.getStringExtra(EXTRA_SOURCE))
                startForegroundService()
                startLocationUpdates()
            }
            ACTION_STOP -> {
                stopBroadcasting()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val stopIntent = Intent(this, BusTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Broadcasting Bus Location")
            .setContentText(notificationText())
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Broadcasting", stopPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000)
            .setMinUpdateIntervalMillis(10000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun updateFirebaseLocation(location: Location) {
        val rid = routeId ?: return
        val liveLocation = LiveBusLocation(
            routeId = rid,
            lat = location.latitude,
            lng = location.longitude,
            speed = location.speed,
            bearing = location.bearing,
            accuracy = location.accuracy,
            timestamp = System.currentTimeMillis(),
            reporterName = userName ?: driverName ?: "Anonymous",
            driverName = driverName.orEmpty(),
            driverId = driverId.orEmpty(),
            isActive = true,
            tripId = tripId.orEmpty(),
            source = source
        )
        
        serviceScope.launch {
            firebaseRepository.updateLiveLocation(liveLocation)
            publishTicketMachineSessionIfNeeded(location)
        }
    }

    private suspend fun publishTicketMachineSessionIfNeeded(location: Location) {
        if (source != LocationSource.TICKET_MACHINE) return
        val rid = routeId ?: return
        val tid = tripId ?: return
        val token = verificationToken ?: return
        val machineId = driverId ?: driverName ?: "ticket-machine"
        val now = System.currentTimeMillis()
        firebaseRepository.publishTicketMachineSession(
            TicketMachineSession(
                routeId = rid,
                tripId = tid,
                machineId = machineId,
                verificationToken = token,
                qrPayload = buildQrPayload(rid, tid, machineId, token),
                lat = location.latitude,
                lng = location.longitude,
                createdAt = now,
                expiresAt = now + TICKET_MACHINE_TOKEN_TTL_MS,
                isActive = true
            )
        )
    }

    private fun buildQrPayload(routeId: String, tripId: String, machineId: String, token: String): String {
        return "gramayatri://driver-verify?routeId=$routeId&tripId=$tripId&machineId=$machineId&token=$token"
    }

    private fun stopBroadcasting() {
        val rid = routeId
        if (rid == null) {
            stopSelf()
            return
        }

        serviceScope.launch {
            firebaseRepository.deactivateLiveLocation(rid, source)
            withContext(Dispatchers.Main) {
                stopSelf()
            }
        }
    }

    private fun notificationText(): String {
        return when (source) {
            LocationSource.DRIVER -> "Driver mode is sharing verified GPS for this bus."
            LocationSource.TICKET_MACHINE -> "Ticket machine GPS is sharing verified live bus data."
            LocationSource.PASSENGER -> "You are helping others track this bus. Thank you!"
        }
    }

    private fun parseLocationSource(value: String?): LocationSource {
        return runCatching {
            LocationSource.valueOf(value ?: LocationSource.PASSENGER.name)
        }.getOrDefault(LocationSource.PASSENGER)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bus Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }
}

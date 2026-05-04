package com.gramayatri.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.gramayatri.data.model.Stop
import com.gramayatri.data.repository.FirebaseRepository
import com.gramayatri.data.repository.LocalCacheRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.math.*

@HiltWorker
class ProximityWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val firebaseRepository: FirebaseRepository,
    private val localCacheRepository: LocalCacheRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "proximity_alerts"
        const val ALERT_RADIUS_KM = 2.0
        
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ProximityWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "proximity_check",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = localCacheRepository.userPreferencesFlow.first()
        if (!prefs.notificationsEnabled || prefs.preferredRouteId.isEmpty() || prefs.preferredStopId.isEmpty()) {
            return Result.success()
        }

        // Get route to find preferred stop coordinates
        val routesResult = firebaseRepository.observeRoutes().first { it !is com.gramayatri.data.model.NetworkResult.Loading }
        val routes = (routesResult as? com.gramayatri.data.model.NetworkResult.Success)?.data ?: return Result.retry()
        
        val route = routes.find { it.id == prefs.preferredRouteId } ?: return Result.success()
        val stop = route.stops.find { it.id == prefs.preferredStopId } ?: return Result.success()

        // Get live location
        val liveLocation = firebaseRepository.observeLiveLocation(route.id).first() ?: return Result.success()

        val distance = calculateDistance(liveLocation.lat, liveLocation.lng, stop.lat, stop.lng)
        
        if (distance <= ALERT_RADIUS_KM) {
            showNotification(stop.name)
        }

        return Result.success()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371 // Radius of the earth in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = (sin(dLat / 2) * sin(dLat / 2)) +
                (cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2))
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun showNotification(stopName: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Arrival Alerts", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Bus Approaching!")
            .setContentText("The bus is almost at $stopName. Get ready!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(2002, notification)
    }
}

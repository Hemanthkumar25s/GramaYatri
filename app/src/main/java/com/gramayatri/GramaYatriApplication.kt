package com.gramayatri

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.database.FirebaseDatabase
import com.gramayatri.data.worker.ProximityWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GramaYatriApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase Persistence early to avoid "must be called before usage" error
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Persistence already enabled or instance already in use
        }

        createNotificationChannels()
        ProximityWorker.schedule(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Bus Alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Cancellations, delays, and extra buses"
                enableVibration(true)
            }

            val pingChannel = NotificationChannel(
                CHANNEL_PINGS,
                "Bus Pings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Live bus location reports from the community"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannels(listOf(alertsChannel, pingChannel))
        }
    }

    companion object {
        const val CHANNEL_ALERTS = "grama_yatri_alerts"
        const val CHANNEL_PINGS = "grama_yatri_pings"
    }
}

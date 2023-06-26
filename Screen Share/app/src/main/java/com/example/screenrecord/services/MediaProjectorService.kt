package com.example.screenrecord.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.TaskStackBuilder
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.example.screenrecord.MainActivity
import com.example.screenrecord.R

class MediaProjectorService: Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // Start the foreground service
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ScreenCaptureChannel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create the notification channel for Android Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                notificationChannelId,
                "Screen Capture",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val flag =
            PendingIntent.FLAG_IMMUTABLE

        val details_uri = "https://www.screenrecord.com"

        val clickIntent = Intent(
            Intent.ACTION_VIEW,
            details_uri.toUri(),
            this,
            MainActivity::class.java
        )

        val clickPendingIntent: PendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(clickIntent)
            getPendingIntent(1, flag)
        }

        // Create the notification
        val notificationBuilder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Screen Capture")
            .setContentText("Screen capture in progress")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSmallIcon(R.drawable.ic_baseline_notifications_24)
            .setAutoCancel(false)
            .setContentIntent(clickPendingIntent)
            .setOngoing(true)

        return notificationBuilder.build()
    }

    companion object {
        private const val NOTIFICATION_ID = 100
    }

}
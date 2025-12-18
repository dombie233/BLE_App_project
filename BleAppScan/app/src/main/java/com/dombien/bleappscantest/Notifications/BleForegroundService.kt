package com.dombien.bleappscantest.Notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dombien.bleappscantest.Activities.DeviceActivity
import com.dombien.bleappscantest.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BleForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "ble_sensor_channel_v4_boomerang"
        const val NOTIFICATION_ID = 1001


        const val ACTION_DISCONNECT_REQUEST = "com.dombien.bleappscantest.ACTION_DISCONNECT"


        const val ACTION_NOTIFICATION_DELETED = "com.dombien.bleappscantest.ACTION_NOTIF_DELETED"
    }


    private var lastTemperature: String = "Monitoring..."

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundServiceWithNotification("Connecting...")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Connection",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows active device status"
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, DeviceActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val disconnectIntent = Intent(ACTION_DISCONNECT_REQUEST).apply {
            setPackage(packageName)
        }
        val pendingDisconnectIntent = PendingIntent.getBroadcast(
            this, 0, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val deleteIntent = Intent(this, BleForegroundService::class.java).apply {
            action = ACTION_NOTIFICATION_DELETED
        }
        val pendingDeleteIntent = PendingIntent.getService(
            this, 0, deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // -------------------------------------------------------

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ble_notification)
            .setContentTitle("BLE Device Connected")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true) // Dla starszych Androidów
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(pendingDeleteIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", pendingDisconnectIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun startForegroundServiceWithNotification(content: String) {
        lastTemperature = content
        val notification = buildNotification(content)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {

            ACTION_NOTIFICATION_DELETED -> {

                CoroutineScope(Dispatchers.Main).launch {
                    delay(100)

                    startForegroundServiceWithNotification(lastTemperature)
                }
            }

            else -> {
                val temp = intent?.getStringExtra("temperature")
                if (temp != null) {
                    startForegroundServiceWithNotification(temp)
                }
            }
        }

        return START_STICKY
    }
}
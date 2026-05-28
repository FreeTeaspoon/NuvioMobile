package com.nuvio.app.features.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nuvio.app.core.deeplink.buildDownloadsDeepLinkUrl
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

class DownloadsForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildServiceNotification(this))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ServiceNotificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(ServiceNotificationId, notification)
        }
    }

    companion object {
        private const val ServiceNotificationId = 42_018
        private const val ChannelId = "downloads_live_status"

        fun start(context: Context) {
            val appContext = context.applicationContext
            ensureNotificationChannel(appContext)
            val intent = Intent(appContext, DownloadsForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, DownloadsForegroundService::class.java),
            )
        }

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(ChannelId) != null) return

            manager.createNotificationChannel(
                NotificationChannel(
                    ChannelId,
                    runBlocking { getString(Res.string.downloads_channel_name) },
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = runBlocking { getString(Res.string.downloads_channel_description) }
                },
            )
        }

        private fun buildServiceNotification(context: Context): Notification {
            val launchIntent = Intent(context, com.nuvio.app.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse(buildDownloadsDeepLinkUrl())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val launchPendingIntent = PendingIntent.getActivity(
                context,
                ServiceNotificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, ChannelId)
                .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
                .setContentTitle(runBlocking { getString(Res.string.downloads_channel_name) })
                .setContentText(runBlocking { getString(Res.string.downloads_channel_description) })
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(launchPendingIntent)
                .build()
        }
    }
}

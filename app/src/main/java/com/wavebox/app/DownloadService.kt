package com.wavebox.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Keeps the app process alive while songs are downloading. The downloads themselves run in
 * AppViewModel; this service only shows the ongoing notification.
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        show(1)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        show(intent?.getIntExtra(EXTRA_COUNT, 1) ?: 1)
        return START_NOT_STICKY
    }

    private fun show(count: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Wavebox")
            .setContentText(if (count <= 1) "Downloading a song…" else "Downloading $count songs…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private const val CHANNEL = "wavebox_downloads"
        private const val NOTIF_ID = 1001
        private const val EXTRA_COUNT = "count"

        /** [active] = number of running downloads; 0 stops the service. */
        fun sync(ctx: Context, active: Int) {
            runCatching {
                val i = Intent(ctx, DownloadService::class.java)
                if (active > 0) ctx.startForegroundService(i.putExtra(EXTRA_COUNT, active))
                else ctx.stopService(i)
            }
        }
    }
}

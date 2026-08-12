package pm.antani.resentin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pm.antani.resentin.AppApplication
import pm.antani.resentin.R

/**
 * Exists to keep the process (and therefore the single app-wide
 * [pm.antani.resentin.domain.session.ConnectionManager]) alive while backgrounded —
 * it doesn't own a second connection, it just tells the OS not to kill this one.
 */
class ConnectionForegroundService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    inner class LocalBinder : Binder() {
        fun getService(): ConnectionForegroundService = this@ConnectionForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            // The OS can refuse a foreground-service start for reasons outside our
            // control (e.g. Android 15's per-type time budget, or background-start
            // restrictions) — letting that crash the process defeats the whole point
            // of "stay connected". Flip the preference back off so the UI reflects
            // reality instead of showing a toggle stuck on a dead service.
            Log.w(TAG, "startForeground failed, disabling stay-connected", e)
            val container = (application as AppApplication).container
            scope.launch { container.appPreferences.setStayConnected(false) }
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder = binder

    private fun buildNotification(): Notification {
        ensureChannel()
        val host = (application as AppApplication).container.tokenStore.session.value?.host
        val contentText = if (host != null) getString(R.string.fgs_connected_to, host) else getString(R.string.fgs_connected)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_connection_status),
            NotificationManager.IMPORTANCE_LOW,
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ConnectionFgService"
        private const val CHANNEL_ID = "connection_status"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ConnectionForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionForegroundService::class.java))
        }
    }
}

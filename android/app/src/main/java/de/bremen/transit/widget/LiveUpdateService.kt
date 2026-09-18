package de.bremen.transit.widget

import android.app.*
import android.content.*
import android.os.*
import androidx.core.content.ContextCompat
import de.bremen.transit.MainActivity
import de.bremen.transit.R
import kotlinx.coroutines.*

/** User-visible, continuous home-screen display. No polling while the screen is off. */
class LiveUpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var polling: Job? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { restartPolling() }
    }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("live", "Live-Abfahrten", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, LiveUpdateService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        startForeground(41, Notification.Builder(this, "live")
            .setSmallIcon(R.drawable.ic_live).setContentTitle("Checkit Live-Modus")
            .setContentText("Neue Abfahrten jede Minute bei eingeschaltetem Bildschirm.")
            .setContentIntent(open).setOngoing(true).setShowWhen(false)
            .addAction(Notification.Action.Builder(null, "Live-Modus beenden", stop).build()).build())
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP" || !enabled(this)) {
            setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        restartPolling()
        return START_STICKY
    }
    private fun restartPolling() {
        polling?.cancel()
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        polling = scope.launch {
            var first = true
            while (isActive) {
                try { WidgetUpdates.refresh(this@LiveUpdateService, force = first) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* Retry on the next tick, preserving the last data stand. */ }
                first = false
                delay(10_000)
            }
        }
    }
    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }
    companion object {
        fun enabled(context: Context) = context.getSharedPreferences("live-mode", MODE_PRIVATE).getBoolean("enabled", true)
        fun setEnabled(context: Context, value: Boolean) { context.getSharedPreferences("live-mode", MODE_PRIVATE).edit().putBoolean("enabled", value).apply() }
        fun start(context: Context) {
            if (enabled(context)) ContextCompat.startForegroundService(context, Intent(context, LiveUpdateService::class.java))
        }
    }
}

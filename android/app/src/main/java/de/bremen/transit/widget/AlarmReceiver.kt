package de.bremen.transit.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import de.bremen.transit.MainActivity
import de.bremen.transit.R
import de.bremen.transit.data.TransitDisplay

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DepartureAlarm.ACTION) return
        val departureId = intent.getStringExtra("departureId") ?: return
        val line = intent.getStringExtra("line").orEmpty()
        val destination = intent.getStringExtra("destination").orEmpty()
        val stopName = intent.getStringExtra("stopName").orEmpty()
        val minutesBefore = intent.getIntExtra("minutesBefore", 10)
        DepartureAlarm.cancel(context, departureId)
        val snapshot = runCatching { TransitDisplay.repository(context).load() }.getOrNull()
        val departure = snapshot?.departures?.find { it.id == departureId }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("alarms", "Abfahrts-Erinnerungen", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (departure?.cancelled == true) {
            "Linie $line nach $destination fällt leider aus."
        } else {
            "Linie $line nach $destination fährt in $minutesBefore Minuten ab · $stopName"
        }
        val notification = NotificationCompat.Builder(context, "alarms")
            .setSmallIcon(R.drawable.ic_live)
            .setContentTitle("Checkit ⏰ Abfahrt")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(departureId.hashCode(), notification)
    }
}

package de.bremen.transit.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.bremen.transit.data.Departure
import de.bremen.transit.data.Stop
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Schedules exact departure reminders via AlarmManager. Survives Doze; no server involved. */
object DepartureAlarm {
    data class Alarm(
        val departureId: String,
        val line: String,
        val destination: String,
        val stopId: String,
        val stopName: String,
        val fireAt: Long,
        val minutesBefore: Int
    )

    const val ACTION = "de.bremen.transit.ALARM_FIRED"
    private const val PREFS = "departure-alarms"
    private const val KEY = "alarms"
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Berlin"))

    fun fireTimeLabel(fireAt: Long): String = timeFormat.format(Instant.ofEpochMilli(fireAt))

    fun getAlarms(context: Context): List<Alarm> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return runCatching {
            val array = JSONArray(raw ?: "")
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        Alarm(
                            departureId = item.getString("departureId"),
                            line = item.getString("line"),
                            destination = item.getString("destination"),
                            stopId = item.getString("stopId"),
                            stopName = item.getString("stopName"),
                            fireAt = item.getLong("fireAt"),
                            minutesBefore = item.getInt("minutesBefore")
                        )
                    )
                }
            }.filter { it.fireAt > System.currentTimeMillis() }
        }.getOrDefault(emptyList())
    }

    fun isSet(context: Context, departureId: String): Boolean =
        getAlarms(context).any { it.departureId == departureId }

    /** Returns false when the departure is too close for a reminder. */
    fun schedule(context: Context, departure: Departure, stop: Stop, minutesBefore: Int): Boolean {
        val timeValue = departure.realtime ?: departure.scheduled ?: return false
        val departureAt = runCatching { Instant.parse(timeValue).toEpochMilli() }.getOrNull() ?: return false
        val fireAt = departureAt - minutesBefore * 60_000L
        if (fireAt <= System.currentTimeMillis() + 20_000) return false
        cancel(context, departure.id)
        val alarm = Alarm(departure.id, departure.line, departure.destination, stop.id, stop.name, fireAt, minutesBefore)
        save(context, getAlarms(context) + alarm)
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context, alarm)
        if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        }
        return true
    }

    fun cancel(context: Context, departureId: String) {
        save(context, getAlarms(context).filter { it.departureId != departureId })
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context, departureId, "", "", "", 0)
        manager.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(context: Context, alarm: Alarm): PendingIntent =
        pendingIntent(context, alarm.departureId, alarm.line, alarm.destination, alarm.stopName, alarm.minutesBefore)

    private fun pendingIntent(
        context: Context,
        departureId: String,
        line: String,
        destination: String,
        stopName: String,
        minutesBefore: Int
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION)
            .putExtra("departureId", departureId)
            .putExtra("line", line)
            .putExtra("destination", destination)
            .putExtra("stopName", stopName)
            .putExtra("minutesBefore", minutesBefore)
        return PendingIntent.getBroadcast(
            context,
            departureId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun save(context: Context, alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(
                JSONObject()
                    .put("departureId", alarm.departureId)
                    .put("line", alarm.line)
                    .put("destination", alarm.destination)
                    .put("stopId", alarm.stopId)
                    .put("stopName", alarm.stopName)
                    .put("fireAt", alarm.fireAt)
                    .put("minutesBefore", alarm.minutesBefore)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

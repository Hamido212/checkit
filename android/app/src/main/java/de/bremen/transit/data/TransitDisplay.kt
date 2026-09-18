package de.bremen.transit.data

import android.content.Context
import de.bremen.transit.BuildConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TransitDisplay {
    fun remaining(value: String?, now: Long = System.currentTimeMillis()): String = runCatching {
        val delta = Instant.parse(value).toEpochMilli() - now
        if (delta <= 0) "jetzt" else "${kotlin.math.ceil(delta / 60000.0).toInt()} min"
    }.getOrDefault("—")
    fun time(value: String?): String = runCatching {
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Berlin")).format(Instant.parse(value))
    }.getOrDefault("—")
    fun expired(value: String): Boolean = runCatching { Instant.parse(value).plusSeconds(120).isBefore(Instant.now()) }.getOrDefault(true)
    fun upcoming(d: Departure): Boolean = runCatching { Instant.parse(d.realtime ?: d.scheduled).isAfter(Instant.now()) }.getOrDefault(false)
    fun status(snapshot: DepartureSnapshot?): String = when {
        snapshot == null -> "OFFLINE · Bitte aktualisieren"
        snapshot.stale || expired(snapshot.generatedAt) -> "VERALTET · Stand ${time(snapshot.generatedAt)}"
        snapshot.departures.any { it.realtimeData } -> "ECHTZEIT · Stand ${time(snapshot.generatedAt)}"
        else -> "FAHRPLAN · Stand ${time(snapshot.generatedAt)}"
    }
    fun repository(context: Context) = DepartureRepository(context, TransitApiClient(BuildConfig.API_BASE_URL))
}

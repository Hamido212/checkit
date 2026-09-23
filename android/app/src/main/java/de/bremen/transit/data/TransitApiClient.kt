package de.bremen.transit.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TransitApiClient(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000
) {
    fun searchStops(query: String): List<Stop> {
        val connection = URL("${baseUrl.trimEnd('/')}/api/stops/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            if (connection.responseCode !in 200..299) throw IllegalStateException("Suche nicht erreichbar")
            val items = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("results")
            return (0 until items.length()).map { val item = items.getJSONObject(it); Stop(item.getString("id"), item.getString("name")) }
        } finally { connection.disconnect() }
    }
    fun getDepartures(stop: Stop): DepartureSnapshot {
        val endpoint = "${baseUrl.trimEnd('/')}/api/departures" +
            "?stopId=${java.net.URLEncoder.encode(stop.id, "UTF-8")}" +
            "&stopName=${java.net.URLEncoder.encode(stop.name, "UTF-8")}"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Transit API HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseSnapshot(JSONObject(body), stop)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSnapshot(root: JSONObject, fallbackStop: Stop): DepartureSnapshot {
        val stationJson = root.optJSONObject("station")
        val station = Stop(
            id = stationJson?.optString("id").orEmpty().ifBlank { fallbackStop.id },
            name = stationJson?.optString("name").orEmpty().ifBlank { fallbackStop.name },
            timeZone = stationJson?.optString("timeZone")?.takeUnless { it.isBlank() || it == "null" }
        )
        val items = root.optJSONArray("departures")
        val departures = buildList {
            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val line = item.optJSONObject("line")
                    val stop = item.optJSONObject("stop")
                    val time = item.optJSONObject("time")
                    val realtime = time?.optString("realtime").orEmpty().takeUnless { it.isBlank() || it == "null" }
                    val scheduled = time?.optString("scheduled").orEmpty().takeUnless { it.isBlank() || it == "null" }
                    if (realtime == null && scheduled == null) continue
                    add(
                        Departure(
                            id = item.optString("id", "departure-$index"),
                            line = line?.optString("label").orEmpty().ifBlank { "—" },
                            destination = item.optString("destination", "Ziel unbekannt"),
                            platform = stop?.optString("platform").orEmpty().ifBlank { null },
                            scheduled = scheduled,
                            realtime = realtime,
                            delayMinutes = time?.optInt("delayMinutes", 0) ?: 0,
                            realtimeData = item.optBoolean("realtime", false),
                            cancelled = item.optBoolean("cancelled", false),
                            color = line?.optString("color")?.takeUnless { it == "null" || it.isBlank() },
                            textColor = line?.optString("textColor")?.takeUnless { it == "null" || it.isBlank() }
                        )
                    )
                }
            }
        }
        return DepartureSnapshot(
            station = station,
            generatedAt = root.optString("generatedAt"),
            departures = departures
        )
    }
}

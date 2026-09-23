package de.bremen.transit.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DepartureRepository(
    context: Context,
    private val apiClient: TransitApiClient
) {
    private val preferences = context.getSharedPreferences("departure-cache", Context.MODE_PRIVATE)

    fun refresh(stop: Stop): DepartureSnapshot {
        return try {
            val fresh = apiClient.getDepartures(stop)
            if (selectedStop().id == stop.id) save(fresh)
            fresh
        } catch (error: Exception) {
            val cached = load()?.takeIf { it.station.id == stop.id }?.copy(stale = true) ?: throw error
            if (selectedStop().id == stop.id) save(cached)
            cached
        }
    }

    fun load(): DepartureSnapshot? {
        val raw = preferences.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    fun selectedStop(): Stop {
        return Stop(
            id = preferences.getString(KEY_STOP_ID, DEFAULT_STOP.id) ?: DEFAULT_STOP.id,
            name = preferences.getString(KEY_STOP_NAME, DEFAULT_STOP.name) ?: DEFAULT_STOP.name
        )
    }

    fun saveSelectedStop(stop: Stop) {
        preferences.edit()
            .putString(KEY_STOP_ID, stop.id)
            .putString(KEY_STOP_NAME, stop.name)
            .apply()
    }

    fun getFavorites(): List<Stop> {
        val raw = preferences.getString(KEY_FAVORITES, null)
        if (raw == null) return listOf(selectedStop()).also { saveFavorites(it) }
        val parsed = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(Stop(item.getString("id"), item.getString("name")))
                }
            }
        }.getOrDefault(emptyList())
        return parsed
    }

    fun saveFavorites(stops: List<Stop>) {
        val array = JSONArray()
        stops.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) }
        preferences.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    fun addFavorite(stop: Stop) {
        val current = getFavorites()
        if (current.none { it.id == stop.id }) saveFavorites(current + stop)
    }

    fun removeFavorite(stopId: String) {
        saveFavorites(getFavorites().filter { it.id != stopId })
    }

    fun isFavorite(stopId: String) = getFavorites().any { it.id == stopId }

    private fun save(snapshot: DepartureSnapshot) {
        preferences.edit()
            .putString(KEY_SNAPSHOT, encode(snapshot).toString())
            .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
            .apply()
    }

    private fun encode(snapshot: DepartureSnapshot): JSONObject {
        val departures = JSONArray()
        snapshot.departures.forEach { departure ->
            departures.put(
                JSONObject()
                    .put("id", departure.id)
                    .put("line", departure.line)
                    .put("destination", departure.destination)
                    .put("platform", departure.platform)
                    .put("scheduled", departure.scheduled)
                    .put("realtime", departure.realtime)
                    .put("delayMinutes", departure.delayMinutes)
                    .put("realtimeData", departure.realtimeData)
                    .put("cancelled", departure.cancelled)
                    .put("color", departure.color)
                    .put("textColor", departure.textColor)
            )
        }
        return JSONObject()
            .put(
                "station",
                JSONObject().put("id", snapshot.station.id).put("name", snapshot.station.name).put("timeZone", snapshot.station.timeZone)
            )
            .put("generatedAt", snapshot.generatedAt)
            .put("stale", snapshot.stale)
            .put("departures", departures)
    }

    private fun decode(root: JSONObject): DepartureSnapshot {
        val stationJson = root.getJSONObject("station")
        val items = root.getJSONArray("departures")
        val departures = buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    Departure(
                        id = item.getString("id"),
                        line = item.getString("line"),
                        destination = item.getString("destination"),
                        platform = item.optString("platform").takeUnless { it.isBlank() || it == "null" },
                        scheduled = item.optString("scheduled").takeUnless { it.isBlank() || it == "null" },
                        realtime = item.optString("realtime").takeUnless { it.isBlank() || it == "null" },
                        delayMinutes = item.optInt("delayMinutes"),
                        realtimeData = item.optBoolean("realtimeData"),
                        cancelled = item.optBoolean("cancelled"),
                        color = item.optString("color").takeUnless { it.isBlank() || it == "null" },
                        textColor = item.optString("textColor").takeUnless { it.isBlank() || it == "null" }
                    )
                )
            }
        }
        return DepartureSnapshot(
            station = Stop(stationJson.getString("id"), stationJson.getString("name"), stationJson.optString("timeZone").takeUnless { it.isBlank() || it == "null" }),
            generatedAt = root.optString("generatedAt"),
            departures = departures,
            stale = root.optBoolean("stale", false)
        )
    }

    companion object {
        private const val KEY_SNAPSHOT = "latest-snapshot"
        private const val KEY_LAST_SUCCESS = "last-success"
        private const val KEY_STOP_ID = "selected-stop-id"
        private const val KEY_STOP_NAME = "selected-stop-name"
        private const val KEY_FAVORITES = "favorite-stops"
        private val DEFAULT_STOP = Stop("de-DELFI_de:04011:13927_G", "Bremen Hauptbahnhof")
    }
}

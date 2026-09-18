package de.bremen.transit.data

data class Stop(
    val id: String,
    val name: String
)

data class Departure(
    val id: String,
    val line: String,
    val destination: String,
    val platform: String?,
    val scheduled: String?,
    val realtime: String?,
    val delayMinutes: Int,
    val realtimeData: Boolean,
    val cancelled: Boolean,
    val color: String? = null,
    val textColor: String? = null
)

data class DepartureSnapshot(
    val station: Stop,
    val generatedAt: String,
    val departures: List<Departure>,
    val stale: Boolean = false
)

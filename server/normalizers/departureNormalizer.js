import { boolean } from "../providers/transitousProvider.js";

function routeColor(item) {
  return /^[#]?[0-9a-f]{6}$/i.test(item.routeColor || '')
    ? `#${String(item.routeColor).replace(/^#/, "")}`
    : null;
}

export function normalizeDeparture(item) {
  const stop = item.place || {};
  const scheduled = stop.scheduledDeparture || stop.departure || null;
  const realtime = stop.departure || scheduled;
  const delayMinutes = scheduled && realtime
    ? Math.round((new Date(realtime) - new Date(scheduled)) / 60000)
    : 0;

  return {
    id: item.tripId || `${item.routeShortName || item.displayName}-${realtime}`,
    line: {
      id: item.routeId || item.routeShortName || item.displayName || "unknown",
      label: item.routeShortName || item.displayName || "—",
      color: routeColor(item),
      textColor: /^[#]?[0-9a-f]{6}$/i.test(item.routeTextColor || '') ? `#${item.routeTextColor.replace(/^#/, '')}` : null
    },
    destination: item.headsign || "Ziel unbekannt",
    transportMode: item.mode || "TRANSIT",
    stop: {
      id: stop.stopId || null,
      name: stop.name || null,
      platform: stop.track || null
    },
    time: {
      scheduled,
      realtime,
      delayMinutes
    },
    realtime: boolean(item.realTime),
    cancelled: boolean(item.cancelled) || boolean(item.tripCancelled),
    alerts: []
  };
}

export function normalizeDepartures(raw, station) {
  const timeZone = raw.stopTimes.find(item => item.place?.tz)?.place.tz || station.timeZone || null;
  return {
    station: { ...station, timeZone },
    generatedAt: new Date().toISOString(),
    source: "transitous",
    departures: raw.stopTimes.map(normalizeDeparture).filter(d => Number.isFinite(Date.parse(d.time.realtime || d.time.scheduled))).sort((a,b) => Date.parse(a.time.realtime || a.time.scheduled) - Date.parse(b.time.realtime || b.time.scheduled))
  };
}

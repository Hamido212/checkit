# Bremen Transit API contract

This is the client-facing contract for Android and Windows. Clients must only
depend on this normalized model; they must not know about Transitous, MOTIS or
VBN payloads.

## Base URL

The local development server is `http://127.0.0.1:8787`. A phone cannot use
that address to reach the development computer: on Android, `127.0.0.1` means
the phone itself. Production clients must receive an HTTPS base URL through
their build configuration or environment.

The Android skeleton uses `API_BASE_URL` from `android/gradle.properties` or
the Gradle command line:

```text
./gradlew assembleDebug -PapiBaseUrl=https://api.example.invalid
```

## `GET /api/health`

Returns `200` when the API process is available:

```json
{ "ok": true, "provider": "transitous" }
```

## `GET /api/stops/search?q={query}`

Returns up to eight Bremen stop results:

```json
{
  "results": [
    {
      "id": "de-VBN_000000115067",
      "name": "Bremen Domsheide",
      "locality": "Bremen",
      "lat": 53.075,
      "lon": 8.808
    }
  ]
}
```

An empty query returns `400` with `{ "error": "..." }`.

## `GET /api/departures?stopId={id}&stopName={displayName}`

`stopId` is required for a meaningful result. `stopName` is optional and is
used as a display fallback. The local server defaults to Bremen Hauptbahnhof
when no query parameters are supplied.

```json
{
  "station": {
    "id": "de-DELFI_de:04011:13927_G",
    "name": "Bremen Hauptbahnhof"
  },
  "generatedAt": "2026-09-17T19:00:00.000Z",
  "source": "transitous",
  "departures": [
    {
      "id": "trip-id",
      "line": {
        "id": "6",
        "label": "6",
        "color": "#e51b2b",
        "textColor": null
      },
      "destination": "Flughafen",
      "transportMode": "TRAM",
      "stop": {
        "id": "stop-id",
        "name": "Bremen Hauptbahnhof",
        "platform": "E"
      },
      "time": {
        "scheduled": "2026-09-17T19:05:00Z",
        "realtime": "2026-09-17T19:05:00Z",
        "delayMinutes": 0
      },
      "realtime": true,
      "cancelled": false,
      "alerts": []
    }
  ]
}
```

Times are ISO-8601 timestamps. `time.realtime` can be `null` only when no
departure time is available; clients must then omit the row rather than invent
a time. `delayMinutes > 0` is delayed, `cancelled: true` must remain visible
as cancelled, and `realtime: false` means scheduled data.

Errors use an HTTP `502` response:

```json
{
  "error": "Die Verkehrsdaten konnten gerade nicht geladen werden.",
  "detail": "..."
}
```

Clients should retain their last successful response and mark it stale when a
refresh fails.

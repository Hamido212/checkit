package de.bremen.transit

import android.appwidget.AppWidgetManager
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bremen.transit.data.*
import de.bremen.transit.widget.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onResume() {
        super.onResume()
        LiveUpdateService.start(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 && !getPreferences(MODE_PRIVATE).getBoolean("notification-asked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification-asked", true).apply()
            notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFB51B), background = Color(0xFF111317), onBackground = Color.White, onSurface = Color.White)) {
            Surface(color = Color(0xFF090A0C), contentColor = Color.White) { CheckitApp(this) }
        } }
    }
}
@Composable private fun CheckitApp(activity: MainActivity) {
    val repository = remember { TransitDisplay.repository(activity) }
    val client = remember { TransitApiClient(BuildConfig.API_BASE_URL) }
    var stop by remember { mutableStateOf(repository.selectedStop()) }
    var snapshot by remember { mutableStateOf(repository.load()?.takeIf { it.station.id == stop.id }) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Stop>()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var liveMode by remember { mutableStateOf(LiveUpdateService.enabled(activity)) }
    var favorites by remember { mutableStateOf(repository.getFavorites()) }
    var alarms by remember { mutableStateOf(DepartureAlarm.getAlarms(activity)) }
    var alarmTarget by remember { mutableStateOf<Departure?>(null) }
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch {
        busy = true
        try { WidgetUpdates.refresh(activity, force = true); snapshot = repository.load()?.takeIf { it.station.id == stop.id }; message = if(snapshot?.stale == true) "Keine Verbindung. Letzter Stand bleibt sichtbar." else "" }
        catch (_: Exception) { message = "Keine Verbindung. Bitte erneut versuchen." }
        finally { busy = false }
    } }
    fun setAlarm(departure: Departure, minutes: Int) {
        val manager = activity.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${activity.packageName}")))
            message = "Bitte exakte Erinnerungen erlauben und erneut tippen."
            return
        }
        if (DepartureAlarm.schedule(activity, departure, stop, minutes)) {
            alarms = DepartureAlarm.getAlarms(activity)
            message = "⏰ Erinnerung aktiv: Linie ${departure.line} in $minutes Minuten."
        } else message = "Dafür ist es zu spät – die Abfahrt steht kurz bevor."
        alarmTarget = null
    }
    DisposableEffect(Unit) {
        val preferences = activity.getSharedPreferences("departure-cache", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            snapshot = repository.load()?.takeIf { it.station.id == stop.id }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(Unit) {
        RefreshWorker.schedule(activity)
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            liveMode = LiveUpdateService.enabled(activity)
            while (true) {
                WidgetUpdates.refresh(activity)
                snapshot = repository.load()?.takeIf { it.station.id == stop.id }
                kotlinx.coroutines.delay(60_000)
            }
        }
    }
    LaunchedEffect(Unit) { while(true) { kotlinx.coroutines.delay(1000); now = System.currentTimeMillis() } }
    Column(Modifier.fillMaxSize().background(Color(0xFF090A0C)).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("checkit.", fontSize = 32.sp, color = Color.White)
        Text("Deine Haltestelle. Bei dir.", color = Color(0xFFA8ADB5))
        Column(Modifier.fillMaxWidth().background(Color(0xFF252829),RoundedCornerShape(14.dp)).border(1.dp,Color(0xFF596266),RoundedCornerShape(14.dp)).padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stop.name, Modifier.weight(1f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val starred = favorites.any { it.id == stop.id }
                TextButton(onClick = {
                    if (starred) repository.removeFavorite(stop.id) else repository.addFavorite(stop)
                    favorites = repository.getFavorites()
                }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(35.dp).height(28.dp)) { Text(if (starred) "★" else "☆", fontSize = 19.sp, color = Color(0xFFFFB51B)) }
                TextButton(onClick = { refresh() }, enabled = !busy, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(35.dp).height(28.dp)) { Text("↻", fontSize = 21.sp) }
            }
            Text(TransitDisplay.status(snapshot), fontSize = 10.sp, color = Color(0xFFA8ADB5))
            val departures = snapshot?.departures?.filter { TransitDisplay.upcoming(it) }.orEmpty()
            if(departures.isEmpty()) Text("Keine aktuellen Abfahrten. Bitte aktualisieren.", fontSize = 13.sp, modifier = Modifier.padding(vertical = 20.dp))
            departures.take(4).forEach { d ->
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val color = runCatching { Color(android.graphics.Color.parseColor(d.color ?: "#FFB51B")) }.getOrDefault(Color(0xFFFFB51B))
                    val ink = runCatching { Color(android.graphics.Color.parseColor(d.textColor ?: "#111317")) }.getOrDefault(Color.Black)
                    Text(d.line, Modifier.width(38.dp).background(color).padding(3.dp), color = ink, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
                    Column(Modifier.weight(1f)) {
                        Text(d.destination, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        if(d.delayMinutes > 0) Text("+${d.delayMinutes} min", color = Color(0xFFFFB51B), fontSize = 10.sp)
                    }
                    Column(Modifier.width(58.dp)) {
                        Text(if(d.cancelled) "AUS" else TransitDisplay.remaining(d.realtime ?: d.scheduled, now), Modifier.fillMaxWidth(), color = Color(0xFFFFB51B), fontSize = 14.sp, textAlign = TextAlign.End)
                        Text(TransitDisplay.time(d.realtime ?: d.scheduled), Modifier.fillMaxWidth(), color = Color(0xFFA8ADB5), fontSize = 9.sp, textAlign = TextAlign.End)
                    }
                    val alarmActive = alarms.any { it.departureId == d.id }
                    TextButton(onClick = {
                        if (alarmActive) { DepartureAlarm.cancel(activity, d.id); alarms = DepartureAlarm.getAlarms(activity); message = "⏰ Erinnerung gelöscht." }
                        else alarmTarget = d
                    }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(34.dp).height(30.dp)) {
                        Text("⏰", fontSize = 15.sp, color = if (alarmActive) Color(0xFFFFB51B) else Color(0xFFA8ADB5))
                    }
                }
            }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank()) Text(message, color = Color(0xFFFFB51B), fontSize = 12.sp)
        alarmTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { alarmTarget = null },
                title = { Text("Abfahrts-Erinnerung") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Wann soll Checkit dich an Linie ${target.line} nach ${target.destination} erinnern?")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15).forEach { minutes ->
                                Button(onClick = { setAlarm(target, minutes) }) { Text("$minutes Min.") }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { alarmTarget = null }) { Text("Abbrechen") } }
            )
        }
        Text("Favoriten", fontSize = 19.sp)
        if(favorites.isEmpty()) Text("Noch keine Favoriten. Tippe oben auf ☆, um die aktuelle Haltestelle zu speichern.", fontSize = 12.sp, color = Color(0xFFA8ADB5))
        favorites.forEach { favorite ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy && favorite.id != stop.id, onClick = {
                    stop = favorite; repository.saveSelectedStop(favorite); snapshot = null; refresh()
                }) { Text(favorite.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                TextButton(onClick = { repository.removeFavorite(favorite.id); favorites = repository.getFavorites() },
                    contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text("✕", color = Color(0xFFA8ADB5)) }
            }
        }
        if(alarms.isNotEmpty()) {
            Text("Aktive Erinnerungen", fontSize = 19.sp)
            alarms.forEach { alarm ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("⏰ Linie ${alarm.line} nach ${alarm.destination} · ${DepartureAlarm.fireTimeLabel(alarm.fireAt)} Uhr",
                        Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { DepartureAlarm.cancel(activity, alarm.departureId); alarms = DepartureAlarm.getAlarms(activity) },
                        contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text("✕", color = Color(0xFFA8ADB5)) }
                }
            }
        }
        Text("Haltestelle auswählen", fontSize = 19.sp)
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("z. B. Bremen Domsheide") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && query.isNotBlank(), onClick = { scope.launch {
            busy = true
            try { results = withContext(Dispatchers.IO) { client.searchStops(query.trim()) }; message = if(results.isEmpty()) "Keine Haltestelle gefunden." else "" }
            catch (_: Exception) { message = "Suche fehlgeschlagen. Bitte erneut versuchen." }
            finally { busy = false }
        } }) { Text("Haltestelle suchen") }
        results.forEach { candidate ->
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                stop = candidate; repository.saveSelectedStop(candidate); snapshot = null; results = emptyList(); refresh()
            }) { Text(candidate.name) }
        }
        HorizontalDivider()
        Button(onClick = {
            val manager = AppWidgetManager.getInstance(activity)
            if(manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(activity, BremenDepartureWidgetReceiver::class.java), null, null)
            else message = "Homescreen gedrückt halten → Widgets → Checkit."
        }, modifier = Modifier.fillMaxWidth()) { Text("Widget hinzufügen") }
        Text("Tippe auf einen Favoriten, um die Haltestelle zu wechseln. Die Auswahl gilt für App und alle Checkit-Widgets. Zum Vergrößern die Ränder des Widgets ziehen. Aktualisieren mit ↻, Einstellungen mit Tipp auf die Haltestelle.", fontSize = 12.sp, color = Color(0xFFA8ADB5))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Live-Modus", modifier = Modifier.weight(1f))
            Switch(checked = liveMode, onCheckedChange = { enabled ->
                liveMode = enabled
                LiveUpdateService.setEnabled(activity, enabled)
                if (enabled) LiveUpdateService.start(activity)
                else activity.stopService(android.content.Intent(activity, LiveUpdateService::class.java))
            })
        }
        Text("Live-Modus: neue Daten jede Minute bei eingeschaltetem Bildschirm, mit dauerhafter Benachrichtigung. Ohne Live-Modus aktualisiert Android ungefähr alle 15 Minuten. Bei Xiaomi gegebenenfalls für Checkit unter Akku → Keine Beschränkungen und Autostart aktivieren. Prüfe den angezeigten Datenstand.", fontSize = 12.sp, color = Color(0xFFA8ADB5))
        TextButton(onClick = { activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://transitous.org/sources/"))) }) { Text("Daten: Transitous / MOTIS · Datenquellen ↗", fontSize = 11.sp) }
        Text("© OpenStreetMap-Mitwirkende · Kein offizielles BSAG-Produkt", fontSize = 10.sp, color = Color(0xFFA8ADB5))
    }
}

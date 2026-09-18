package de.bremen.transit

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
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
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch {
        busy = true
        try { WidgetUpdates.refresh(activity, force = true); snapshot = repository.load()?.takeIf { it.station.id == stop.id }; message = if(snapshot?.stale == true) "Keine Verbindung. Letzter Stand bleibt sichtbar." else "" }
        catch (_: Exception) { message = "Keine Verbindung. Bitte erneut versuchen." }
        finally { busy = false }
    } }
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
                }
            }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank()) Text(message, color = Color(0xFFFFB51B), fontSize = 12.sp)
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
        Text("Haltestelle gilt für alle Checkit-Widgets. Zum Vergrößern die Ränder des Widgets ziehen. Aktualisieren mit ↻, Einstellungen mit Tipp auf die Haltestelle.", fontSize = 12.sp, color = Color(0xFFA8ADB5))
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
